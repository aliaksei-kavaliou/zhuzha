package com.inkfirst.core

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Cancelling a generation must arrive as a cancellation.
 *
 * `CancellationException` is an `Exception`, so a bare `catch (e: Exception)`
 * swallows it. Around the body read that turned "the reader navigated away"
 * into `GeminiHttpException("Gemini response was not the expected shape")` —
 * a message that sends someone hunting a server fault that never happened,
 * and a lie to the parent coroutine, which must see a cancellation to unwind.
 *
 * Every other broad catch in :core already guards this (BookBadger,
 * ChapterIllustrator, TopicChecker). GeminiClient was the gap, and the worst
 * place for it: this is where the code sits suspended for minutes at a time,
 * so it is where a cancel actually lands.
 */
class GeminiCancellationTest {

    /**
     * WHAT THIS COVERS, stated precisely, because two earlier versions of it
     * claimed more than they proved.
     *
     * It pins that a cancellation raised while the response is being read comes
     * out of [KtorGeminiClient.generateText] AS a cancellation, rather than as
     * a `GeminiHttpException` telling the reader the server misbehaved.
     *
     * It does NOT exercise the guard around `response.body()`. Measured from
     * the stack trace rather than assumed: ktor's `SaveBodyPlugin` buffers the
     * body DURING the call, so the failure surfaces inside `http.post` —
     * i.e. in `postWithRetry`, where `isTransientConnectFailure` returns false
     * and rethrows. Both earlier attempts (cancelling the job mid-flight, and
     * closing the channel with a cause) passed with the guard REMOVED for
     * exactly that reason.
     *
     * The guard still belongs there: `body()` also deserializes, which can
     * suspend, and every other broad catch in :core already guards this. But it
     * is defensive consistency, not something this test demonstrates.
     *
     * Deterministic by construction, with no timing in it. The body channel is
     * closed WITH a cancellation as its cause, so `response.body()` is
     * guaranteed to raise one while reading — which is precisely the suspension
     * the old `catch (e: Exception)` sat around.
     *
     * A first attempt cancelled the job mid-flight instead. It passed with the
     * guard REMOVED, because the cancel landed before the coroutine ever
     * reached the body read, so it was quietly exercising postWithRetry (which
     * was already correct). Verified both ways this time: fails without the
     * guard, passes with it.
     */
    @Test
    fun `a cancellation raised while reading the response is not converted into a server error`() =
        runTest {
            val stalled = ByteChannel(autoFlush = true)
            stalled.writeStringUtf8("""{"candidates":[""")
            stalled.cancel(CancellationException("the reader navigated away"))

            val engine = MockEngine {
                respond(
                    content = stalled,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }

            KtorGeminiClient(engine).use { client ->
                var thrown: Throwable? = null
                try {
                    client.generateText(
                        model = "gemini-x",
                        systemInstruction = "sys",
                        userPrompt = "hi",
                        apiKey = "k",
                    )
                } catch (t: Throwable) {
                    thrown = t
                }

                assertTrue(
                    thrown is CancellationException,
                    "expected a CancellationException to pass through, got " +
                        "${thrown?.let { it::class.simpleName }}: ${thrown?.message}",
                )
            }
        }
}
