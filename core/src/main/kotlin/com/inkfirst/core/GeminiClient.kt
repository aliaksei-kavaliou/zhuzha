package com.inkfirst.core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Default Gemini model. Changeable per call; app-level settings should also let the user override. */
const val DEFAULT_GEMINI_MODEL: String = "gemini-3.7-flash"

/**
 * Thin seam so tests can feed canned Gemini responses without HTTP.
 * The impl returns the raw model text (Gemini's candidates[0].content.parts[0].text),
 * which for our calls will be a JSON string thanks to responseMimeType.
 */
interface GeminiClient {
    suspend fun generateJson(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject,
        apiKey: String,
    ): String
}

class GeminiHttpException(message: String, val status: Int? = null) : RuntimeException(message)

/**
 * Real Gemini REST client. Talks to generativelanguage.googleapis.com v1beta.
 * The API key travels in the x-goog-api-key header.
 */
class KtorGeminiClient(
    engine: HttpClientEngine = CIO.create(),
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
) : GeminiClient, AutoCloseable {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val http = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
        expectSuccess = false
    }

    override suspend fun generateJson(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject,
        apiKey: String,
    ): String {
        val url = "$baseUrl/v1beta/models/$model:generateContent"
        val request = GenerateContentRequest(
            systemInstruction = SystemInstruction(parts = listOf(Part(text = systemInstruction))),
            contents = listOf(Content(role = "user", parts = listOf(Part(text = userPrompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = responseSchema,
            ),
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

        val parsed: GenerateContentResponse = try {
            response.body()
        } catch (e: Exception) {
            throw GeminiHttpException("Gemini response was not the expected shape: ${e.message}")
        }

        val text = parsed.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw GeminiHttpException("Gemini returned no text candidate")

        // Empty finishReason with no text usually means MAX_TOKENS or SAFETY. Surface it.
        if (text.isBlank()) {
            val reason = parsed.candidates.firstOrNull()?.finishReason ?: "UNKNOWN"
            throw GeminiHttpException("Gemini returned empty text (finishReason=$reason)")
        }
        return text
    }

    override fun close() {
        http.close()
    }
}
