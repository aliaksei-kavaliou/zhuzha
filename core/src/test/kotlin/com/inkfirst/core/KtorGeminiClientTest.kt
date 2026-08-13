package com.inkfirst.core

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KtorGeminiClientTest {

    private val trivialSchema = buildJsonObject { put("type", "OBJECT") }

    @Test
    fun `sends api key header and posts to model URL`() = runTest {
        var seenUrl: String? = null
        var seenApiKey: String? = null
        val engine = MockEngine { req ->
            seenUrl = req.url.toString()
            seenApiKey = req.headers["x-goog-api-key"]
            respond(
                content = ByteReadChannel(
                    """{"candidates":[{"content":{"parts":[{"text":"{\"ok\":true}"}]}}]}""",
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        KtorGeminiClient(engine).use { client ->
            val out = client.generateJson(
                model = "gemini-x", systemInstruction = "sys",
                userPrompt = "hi", responseSchema = trivialSchema, apiKey = "sk-123",
            )
            assertEquals("""{"ok":true}""", out)
        }
        assertTrue(seenUrl!!.endsWith("/v1beta/models/gemini-x:generateContent"))
        assertEquals("sk-123", seenApiKey)
    }

    @Test
    fun `HTTP 4xx becomes GeminiHttpException with status`() {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"error":"nope"}"""),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val ex = assertThrows(GeminiHttpException::class.java) {
            runBlocking {
                KtorGeminiClient(engine).use {
                    it.generateJson("m", "s", "u", trivialSchema, "k")
                }
            }
        }
        assertEquals(400, ex.status)
    }

    @Test
    fun `empty candidates list raises`() {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"candidates":[]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        assertThrows(GeminiHttpException::class.java) {
            runBlocking {
                KtorGeminiClient(engine).use {
                    it.generateJson("m", "s", "u", trivialSchema, "k")
                }
            }
        }
    }
}
