package com.inkfirst.core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.net.ConnectException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
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

/**
 * A picture returned by an image-capable model. [bytes] are already
 * base64-decoded.
 *
 * [text] is the model's accompanying text part, if it sent one. Image
 * responses interleave text and picture, so a prompt can ask for both in a
 * single call — that is how a badge set gets a written description of the
 * mascot without paying for a second request.
 */
class GeneratedImage(val bytes: ByteArray, val mimeType: String, val text: String? = null) {
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

    /**
     * The same, at a pinned [temperature]. For calls that JUDGE rather than
     * write — a verdict that changes between two identical runs is not a
     * verdict. Defaulted to delegate, so a fake that only cares about the
     * prompt needs no change.
     */
    suspend fun generateJson(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject,
        apiKey: String,
        temperature: Double,
    ): String = generateJson(model, systemInstruction, userPrompt, responseSchema, apiKey)

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
     *
     * [referenceImage] is sent alongside the prompt as an example to follow —
     * "draw this character again, new pose". It is how a course's badges stay
     * one recognisable mascot instead of two unrelated doodles.
     */
    suspend fun generateImage(
        model: String,
        prompt: String,
        aspectRatio: String,
        apiKey: String,
        referenceImage: GeneratedImage? = null,
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

    override suspend fun generateJson(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject,
        apiKey: String,
        temperature: Double,
    ): String = postGenerate(
        model = model,
        systemInstruction = systemInstruction,
        userPrompt = userPrompt,
        generationConfig = GenerationConfig(
            temperature = temperature,
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
        referenceImage: GeneratedImage?,
    ): GeneratedImage {
        // imageConfig pins the aspect ratio, which is what lets the paginator
        // reserve a figure's height without opening the file. It's also the
        // most version-sensitive part of this request, so a 400 retries
        // without it rather than losing the picture entirely — the model then
        // picks its own shape and ContentScale.Fit letterboxes it in the slot.
        val parsed = try {
            postImage(model, prompt, apiKey, aspectRatio, referenceImage)
        } catch (e: GeminiHttpException) {
            if (e.status != 400) throw e
            System.err.println("image imageConfig rejected, retrying without it: ${e.message}")
            postImage(model, prompt, apiKey, aspectRatio = null, referenceImage = referenceImage)
        }

        // An image response interleaves parts: the model often emits a short
        // text preamble alongside the picture. Take the first inline payload.
        val parts = parsed.candidates.firstOrNull()?.content?.parts.orEmpty()
        val inline = parts
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

        return GeneratedImage(
            bytes = bytes,
            mimeType = inline.mimeType,
            text = parts.mapNotNull { it.text }.joinToString("\n").trim().ifBlank { null },
        )
    }

    private suspend fun postImage(
        model: String,
        prompt: String,
        apiKey: String,
        aspectRatio: String?,
        referenceImage: GeneratedImage? = null,
    ): GenerateContentResponse = postGenerate(
        model = model,
        systemInstruction = null,
        userPrompt = prompt,
        inlineImage = referenceImage,
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

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun postGenerate(
        model: String,
        systemInstruction: String?,
        userPrompt: String,
        generationConfig: GenerationConfig,
        apiKey: String,
        apiVersion: String = "v1beta",
        inlineImage: GeneratedImage? = null,
    ): GenerateContentResponse {
        val url = "$baseUrl/$apiVersion/models/$model:generateContent"
        val request = GenerateContentRequest(
            systemInstruction = systemInstruction?.let {
                SystemInstruction(parts = listOf(Part(text = it)))
            },
            // Reference image first, then the instruction: the model reads the
            // parts in order, so the picture is already "in view" when the text
            // tells it what to do with it.
            contents = listOf(
                Content(
                    role = "user",
                    parts = listOfNotNull(
                        inlineImage?.let {
                            Part(
                                inlineData = InlineData(
                                    mimeType = it.mimeType,
                                    data = Base64.encode(it.bytes),
                                ),
                            )
                        },
                        Part(text = userPrompt),
                    ),
                ),
            ),
            generationConfig = generationConfig,
        )

        val response: HttpResponse = postWithRetry(url) {
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
        } catch (e: CancellationException) {
            // CancellationException IS an Exception, so without this the catch
            // below would turn "the reader navigated away" into "the server
            // sent something malformed" — and lie to the parent coroutine,
            // which must see a cancellation to unwind.
            //
            // Defensive consistency rather than a fix for an observed failure,
            // and worth being exact about which. Every other broad catch in
            // :core already guards this (BookBadger, ChapterIllustrator,
            // TopicChecker); this was the one that did not. But a cancellation
            // during the NETWORK read does not reach here: ktor's SaveBodyPlugin
            // buffers the body during the call, so it surfaces inside
            // `http.post` and is rethrown by postWithRetry. Measured off a stack
            // trace, not assumed — see GeminiCancellationTest, which documents
            // two attempts that failed to reach this line.
            //
            // What is left for this guard is cancellation during the DESERIALIZE
            // that body() still performs, which can suspend.
            throw e
        } catch (e: Exception) {
            throw GeminiHttpException("Gemini response was not the expected shape: ${e.message}")
        }
    }

    /**
     * POSTs, retrying the handful of failures that mean "the connection did
     * not get made" rather than "the server said no".
     *
     * The first request a process makes can lose the DNS race: on 2026-09-21
     * the first "Compile chapter" of a session died in 80ms with
     * `UnresolvedAddressException` — thrown by `Net.checkAddress`, so the
     * socket was handed an address that never resolved — while WiFi was
     * validated and the API host answered a ping in 19ms. Tapping the button
     * a second time worked, because CIO caches the endpoint once it resolves.
     * A transient miss should not cost a tap, and it certainly should not
     * report itself as a network problem the reader then goes and checks.
     *
     * Only connect-level failures are retried. An HTTP error never lands here
     * ([expectSuccess] is false, so the status is inspected by the caller),
     * and a socket timeout is deliberately excluded: the request timeout is
     * three minutes, and quietly spending another three is worse than saying
     * so. Cancellation falls through untouched — it is not a transient
     * failure, it is the reader leaving.
     */
    private suspend fun postWithRetry(
        url: String,
        build: HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        var attempt = 0
        while (true) {
            try {
                return http.post(url, build)
            } catch (e: Exception) {
                if (attempt >= CONNECT_RETRY_DELAYS_MS.size || !isTransientConnectFailure(e)) throw e
                val wait = CONNECT_RETRY_DELAYS_MS[attempt]
                System.err.println(
                    "Gemini connect attempt ${attempt + 1} failed " +
                        "(${e::class.simpleName}); retrying in ${wait}ms",
                )
                delay(wait)
                attempt++
            }
        }
    }

    /**
     * Whether this is "could not reach the host", as opposed to anything the
     * host actually said. Walks the cause chain because CIO wraps its
     * low-level throwables.
     */
    private fun isTransientConnectFailure(e: Throwable): Boolean {
        var current: Throwable? = e
        while (current != null) {
            when (current) {
                is UnresolvedAddressException,
                is UnknownHostException,
                is ConnectException,
                -> return true
            }
            current = current.cause
        }
        return false
    }

    override fun close() {
        http.close()
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 180_000L
        const val CONNECT_TIMEOUT_MS = 20_000L

        /**
         * Waits before each retry of a failed CONNECTION, in order. Two
         * retries, so a request is attempted three times in all and gives up
         * after about 1.2s of waiting — nothing against a three-minute
         * request timeout, and long enough for a DNS lookup that lost its
         * first race to win the next one.
         */
        val CONNECT_RETRY_DELAYS_MS = longArrayOf(300L, 900L)
    }
}
