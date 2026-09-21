package com.inkfirst.core

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.nio.channels.UnresolvedAddressException

/**
 * The first request a process makes can lose the DNS race. On 2026-09-21 the
 * first "Compile chapter" of a session died in 80ms with
 * `UnresolvedAddressException` while the WiFi was validated and the API host
 * answered a ping in 19ms; the next tap worked, because CIO caches an
 * endpoint once it resolves. These pin the retry that absorbs that, and — as
 * importantly — pin what is NOT retried.
 */
class GeminiConnectRetryTest {

    private fun MockRequestHandleScope.ok() = respond(
        content = ByteReadChannel("""{"candidates":[{"content":{"parts":[{"text":"hello"}]}}]}"""),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    @Test
    fun `a first-request DNS miss is retried instead of surfaced`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls == 1) throw UnresolvedAddressException()
            ok()
        }
        KtorGeminiClient(engine).use { client ->
            val out = client.generateText("gemini-x", "sys", "hi", "sk-1")
            assertEquals("hello", out)
        }
        assertEquals(2, calls)
    }

    @Test
    fun `a refused connection is retried too`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls <= 2) throw ConnectException("Connection refused")
            ok()
        }
        KtorGeminiClient(engine).use { client ->
            assertEquals("hello", client.generateText("gemini-x", "sys", "hi", "sk-1"))
        }
        // Two retries available, so the third attempt is the last one.
        assertEquals(3, calls)
    }

    @Test
    fun `retries are finite — a genuinely unreachable host still fails`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            throw UnresolvedAddressException()
        }
        KtorGeminiClient(engine).use { client ->
            assertThrows(UnresolvedAddressException::class.java) {
                kotlinx.coroutines.runBlocking {
                    client.generateText("gemini-x", "sys", "hi", "sk-1")
                }
            }
        }
        // One attempt plus two retries. If this ever reads higher, the retry
        // has become a way to hammer a host that is genuinely down.
        assertEquals(3, calls)
    }

    @Test
    fun `an HTTP error is NOT retried`() = runTest {
        // The server answered — it just said no. Retrying a 400 re-sends a
        // request that will be rejected identically, and on the image path
        // that is real money.
        var calls = 0
        val engine = MockEngine {
            calls++
            respond(
                content = ByteReadChannel("""{"error":"bad request"}"""),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        KtorGeminiClient(engine).use { client ->
            assertThrows(GeminiHttpException::class.java) {
                kotlinx.coroutines.runBlocking {
                    client.generateText("gemini-x", "sys", "hi", "sk-1")
                }
            }
        }
        assertEquals(1, calls)
    }

    @Test
    fun `the image path gets the same retry`() = runTest {
        // Both paths POST through one place, so this is really asserting that
        // they still do.
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls == 1) throw UnresolvedAddressException()
            respond(
                content = ByteReadChannel(
                    """{"candidates":[{"content":{"parts":[
                       {"inlineData":{"mimeType":"image/png","data":"aGk="}}
                    ]}}]}""",
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        KtorGeminiClient(engine).use { client ->
            val image = client.generateImage("gemini-img", "draw a robot", "4:3", "sk-1")
            assertEquals("image/png", image.mimeType)
        }
        assertEquals(2, calls)
    }
}
