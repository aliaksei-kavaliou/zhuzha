package com.inkfirst.core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Default Gemini model. Changeable per call; app-level settings should also let the user override. */
const val DEFAULT_GEMINI_MODEL: String = "gemini-3.7-flash"

/**
 * Default model for figure illustration ("Nano Banana 2 Lite"). Cheapest and
 * fastest of the image-capable Gemini models — roughly $0.03 per picture,
 * which is the right tier for sketch-style spot art. Overridable in Settings.
 */
const val DEFAULT_GEMINI_IMAGE_MODEL: String = "gemini-3.1-flash-lite-image"

/** A picture returned by an image-capable model. [bytes] are already base64-decoded. */
class GeneratedImage(val bytes: ByteArray, val mimeType: String) {
    /** File extension implied by [mimeType] — "png" unless the model says otherwise. */
    val extension: String
        get() = when {
            mimeType.endsWith("jpeg") || mimeType.endsWith("jpg") -> "jpg"
            mimeType.endsWith("webp") -> "webp"
            else -> "png"
        }
}

/**
 * Thin seam so tests can feed canned Gemini responses without HTTP.
 * The text methods return the raw model text (Gemini's candidates[0].content.parts[0].text).
 */
interface GeminiClient {
    /** Structured JSON output — model is forced to a schema, returns a JSON string. */
    suspend fun generateJson(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject,
        apiKey: String,
    ): String

    /** Plain-text output — for prose like chapter Markdown or memory summaries. */
    suspend fun generateText(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        apiKey: String,
    ): String

    /**
     * Image output — for figure illustration. [model] must be an image-capable
     * model (see [DEFAULT_GEMINI_IMAGE_MODEL]); ordinary text models will fail.
     * There is no system instruction: image models take the whole brief in the
     * user turn, so the house style is baked into [prompt] by the caller.
     */
    suspend fun generateImage(
        model: String,
        prompt: String,
        aspectRatio: String,
        apiKey: String,
    ): GeneratedImage
}

class GeminiHttpException(message: String, val status: Int? = null) : RuntimeException(message)

/**
 * Real Gemini REST client. Talks to generativelanguage.googleapis.com.
 * The API key travels in the x-goog-api-key header.
 */
class KtorGeminiClient(
    engine: HttpClientEngine = CIO.create(),
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
) : GeminiClient, AutoCloseable {

    // explicitNulls = false keeps unset fields off the wire entirely. Matters
    // for Part, which is a union (text OR inlineData) — sending the unused
    // half as an explicit null risks the API rejecting the request.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val http = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
        // Ktor's default request timeout kills slow generations. Drawing a
        // picture regularly takes 20-40s and a long chapter can take longer
        // still, so the ceiling has to be generous — the user is staring at
        // a progress line, not a stalled UI, and a timeout here costs a
        // whole chapter or figure.
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        expectSuccess = false
    }

    override suspend fun generateJson(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject,
        apiKey: String,
    ): String = postGenerate(
        model = model,
        systemInstruction = systemInstruction,
        userPrompt = userPrompt,
        generationConfig = GenerationConfig(
            responseMimeType = "application/json",
            responseSchema = responseSchema,
        ),
        apiKey = apiKey,
    ).textOrThrow()

    override suspend fun generateText(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        apiKey: String,
    ): String = postGenerate(
        model = model,
        systemInstruction = systemInstruction,
        userPrompt = userPrompt,
        generationConfig = GenerationConfig(responseMimeType = "text/plain"),
        apiKey = apiKey,
    ).textOrThrow()

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun generateImage(
        model: String,
        prompt: String,
        aspectRatio: String,
        apiKey: String,
    ): GeneratedImage {
        // imageConfig pins the aspect ratio, which is what lets the paginator
        // reserve a figure's height without opening the file. It's also the
        // most version-sensitive part of this request, so a 400 retries
        // without it rather than losing the picture entirely — the model then
        // picks its own shape and ContentScale.Fit letterboxes it in the slot.
        val parsed = try {
            postImage(model, prompt, apiKey, aspectRatio)
        } catch (e: GeminiHttpException) {
            if (e.status != 400) throw e
            System.err.println("image imageConfig rejected, retrying without it: ${e.message}")
            postImage(model, prompt, apiKey, aspectRatio = null)
        }

        // An image response interleaves parts: the model often emits a short
        // text preamble alongside the picture. Take the first inline payload.
        val inline = parsed.candidates
            .firstOrNull()?.content?.parts.orEmpty()
            .firstNotNullOfOrNull { it.inlineData }
            ?: throw GeminiHttpException(
                "Gemini returned no image (finishReason=" +
                    "${parsed.candidates.firstOrNull()?.finishReason ?: "UNKNOWN"})",
            )

        val bytes = try {
            Base64.decode(inline.data)
        } catch (e: IllegalArgumentException) {
            throw GeminiHttpException("Gemini image payload was not valid base64: ${e.message}")
        }
        if (bytes.isEmpty()) throw GeminiHttpException("Gemini returned a zero-byte image")

        return GeneratedImage(bytes = bytes, mimeType = inline.mimeType)
    }

    private suspend fun postImage(
        model: String,
        prompt: String,
        apiKey: String,
        aspectRatio: String?,
    ): GenerateContentResponse = postGenerate(
        model = model,
        systemInstruction = null,
        userPrompt = prompt,
        generationConfig = GenerationConfig(
            responseModalities = listOf("TEXT", "IMAGE"),
            imageConfig = aspectRatio?.let {
                ImageConfig(aspectRatio = it, imageSize = "1K")
            },
        ),
        apiKey = apiKey,
        apiVersion = "v1",
    )

    private fun GenerateContentResponse.textOrThrow(): String {
        val text = candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw GeminiHttpException("Gemini returned no text candidate")

        // Empty finishReason with no text usually means MAX_TOKENS or SAFETY. Surface it.
        if (text.isBlank()) {
            val reason = candidates.firstOrNull()?.finishReason ?: "UNKNOWN"
            throw GeminiHttpException("Gemini returned empty text (finishReason=$reason)")
        }
        return text
    }

    private suspend fun postGenerate(
        model: String,
        systemInstruction: String?,
        userPrompt: String,
        generationConfig: GenerationConfig,
        apiKey: String,
        apiVersion: String = "v1beta",
    ): GenerateContentResponse {
        val url = "$baseUrl/$apiVersion/models/$model:generateContent"
        val request = GenerateContentRequest(
            systemInstruction = systemInstruction?.let {
                SystemInstruction(parts = listOf(Part(text = it)))
            },
            contents = listOf(Content(role = "user", parts = listOf(Part(text = userPrompt)))),
            generationConfig = generationConfig,
        )

        val response: HttpResponse = http.post(url) {
            headers.append("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            val fullBody = response.bodyAsText()
            // Print unabridged to stderr — lands in Android logcat under "System.err"
            // when running on device, so `adb logcat -s System.err:E` shows the full body.
            System.err.println("Gemini HTTP ${response.status.value} url=$url body=$fullBody")
            throw GeminiHttpException(
                "Gemini request failed: HTTP ${response.status.value} — ${fullBody.take(3000)}",
                status = response.status.value,
            )
        }

        return try {
            response.body()
        } catch (e: Exception) {
            throw GeminiHttpException("Gemini response was not the expected shape: ${e.message}")
        }
    }

    override fun close() {
        http.close()
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 180_000L
        const val CONNECT_TIMEOUT_MS = 20_000L
    }
}
