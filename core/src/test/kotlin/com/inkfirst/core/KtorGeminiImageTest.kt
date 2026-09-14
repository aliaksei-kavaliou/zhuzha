package com.inkfirst.core

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Wire-level behaviour of the image path — see [KtorGeminiClientTest] for the text paths. */
class KtorGeminiImageTest {

    // "hi" == 0x68 0x69
    private val helloBase64 = "aGk="

    private fun imageResponse(mimeType: String = "image/png") = ByteReadChannel(
        """{"candidates":[{"content":{"parts":[
           {"text":"Here you go!"},
           {"inlineData":{"mimeType":"$mimeType","data":"$helloBase64"}}
        ]}}]}""",
    )

    @Test
    fun `decodes base64 image bytes past a text preamble`() = runTest {
        val engine = MockEngine {
            respond(
                content = imageResponse(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        KtorGeminiClient(engine).use { client ->
            val image = client.generateImage("gemini-img", "draw a robot", "4:3", "sk-1")
            assertArrayEquals(byteArrayOf(0x68, 0x69), image.bytes)
            assertEquals("image/png", image.mimeType)
            assertEquals("png", image.extension)
        }
    }

    @Test
    fun `posts to the v1 endpoint asking for an image at the given ratio`() = runTest {
        var seenUrl: String? = null
        var seenBody: String? = null
        val engine = MockEngine { req ->
            seenUrl = req.url.toString()
            seenBody = (req.body as TextContent).text
            respond(
                content = imageResponse(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        KtorGeminiClient(engine).use { it.generateImage("gemini-img", "draw", "4:3", "k") }

        assertTrue(seenUrl!!.endsWith("/v1/models/gemini-img:generateContent"))
        assertTrue(seenBody!!.contains("responseModalities"))
        assertTrue(seenBody!!.contains("\"4:3\""))
        // Image requests carry no system instruction, and explicitNulls=false
        // must keep the unused half of the Part union off the wire.
        assertFalse(seenBody!!.contains("systemInstruction"))
        assertFalse(seenBody!!.contains("inlineData"))
    }

    @Test
    fun `a 400 on imageConfig is retried without it`() = runTest {
        val bodies = mutableListOf<String>()
        val engine = MockEngine { req ->
            bodies += (req.body as TextContent).text
            if (bodies.size == 1) {
                respond(
                    content = ByteReadChannel("""{"error":{"message":"Invalid value at generation_config.image_config"}}"""),
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(
                    content = imageResponse(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        KtorGeminiClient(engine).use { client ->
            val image = client.generateImage("gemini-img", "draw", "4:3", "k")
            assertArrayEquals(byteArrayOf(0x68, 0x69), image.bytes)
        }
        assertEquals(2, bodies.size)
        assertTrue(bodies[0].contains("imageConfig"))
        assertFalse(bodies[1].contains("imageConfig"))
        // The retry must still ask for an image, or it comes back as prose.
        assertTrue(bodies[1].contains("responseModalities"))
    }

    @Test
    fun `a non-400 failure is not retried`() {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond(
                content = ByteReadChannel("""{"error":"nope"}"""),
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        assertThrows(GeminiHttpException::class.java) {
            runBlocking { KtorGeminiClient(engine).use { it.generateImage("m", "p", "4:3", "k") } }
        }
        assertEquals(1, calls)
    }

    @Test
    fun `a text-only response surfaces the finish reason`() {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"candidates":[{"content":{"parts":[{"text":"I can't draw that"}]},"finishReason":"SAFETY"}]}""",
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val ex = assertThrows(GeminiHttpException::class.java) {
            runBlocking { KtorGeminiClient(engine).use { it.generateImage("m", "p", "4:3", "k") } }
        }
        assertTrue(ex.message!!.contains("SAFETY"))
    }

    @Test
    fun `jpeg responses get a jpg extension`() = runTest {
        val engine = MockEngine {
            respond(
                content = imageResponse("image/jpeg"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        KtorGeminiClient(engine).use {
            assertEquals("jpg", it.generateImage("m", "p", "4:3", "k").extension)
        }
    }
}
