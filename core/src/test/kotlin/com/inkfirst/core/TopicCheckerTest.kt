package com.inkfirst.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class CheckClient(
    private val response: String = """{"verdict":"allow","reason":"ok"}""",
    private val fail: Exception? = null,
) : GeminiClient {
    var lastUserPrompt: String? = null
    var lastSystem: String? = null
    var calls = 0

    override suspend fun generateJson(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject,
        apiKey: String,
    ): String {
        calls++
        lastSystem = systemInstruction
        lastUserPrompt = userPrompt
        fail?.let { throw it }
        return response
    }

    override suspend fun generateText(
        model: String, systemInstruction: String, userPrompt: String, apiKey: String,
    ): String = error("the check goes through generateJson")

    override suspend fun generateImage(
        model: String,
        prompt: String,
        aspectRatio: String,
        apiKey: String,
        referenceImage: GeneratedImage?,
    ): GeneratedImage = error("the check draws nothing")
}

private val SOURDOUGH = TopicCheckInputs(topic = "sourdough bread")

class TopicCheckerTest {

    @Test
    fun `the three verdicts come back as themselves`() = runTest {
        listOf(
            "allow" to TopicVerdict.ALLOW,
            "ask" to TopicVerdict.ASK,
            "refuse" to TopicVerdict.REFUSE,
        ).forEach { (word, expected) ->
            val client = CheckClient("""{"verdict":"$word","reason":"because"}""")
            assertEquals(expected, TopicChecker(client).check(SOURDOUGH, "k").verdict)
        }
    }

    @Test
    fun `the reason is carried through for the reader to act on`() = runTest {
        val client = CheckClient("""{"verdict":"ask","reason":"This reads as a recipe, not a subject."}""")
        val result = TopicChecker(client).check(SOURDOUGH, "k")

        assertEquals("This reads as a recipe, not a subject.", result.reason)
    }

    // --- failing open -------------------------------------------------------

    /**
     * The rule the whole design rests on. A dropped call must not stop someone
     * writing a book about bread — the real guard is downstream and is not
     * weakened by this being skipped, so blocking here buys nothing and costs
     * the app's usability.
     */
    @Test
    fun `a dropped call allows the book`() = runTest {
        val client = CheckClient(fail = GeminiHttpException("no network"))
        assertEquals(TopicVerdict.ALLOW, TopicChecker(client).check(SOURDOUGH, "k").verdict)
    }

    @Test
    fun `a malformed reply allows the book`() = runTest {
        val client = CheckClient("not json at all")
        assertEquals(TopicVerdict.ALLOW, TopicChecker(client).check(SOURDOUGH, "k").verdict)
    }

    @Test
    fun `a verdict word nobody recognises allows the book`() = runTest {
        val client = CheckClient("""{"verdict":"maybe","reason":"hmm"}""")
        assertEquals(TopicVerdict.ALLOW, TopicChecker(client).check(SOURDOUGH, "k").verdict)
    }

    /**
     * A refusal the reader cannot read is a dead end with nothing to act on
     * and no way past it — the worst outcome this design has. Downgraded to
     * ask, which at least carries its own escape hatch.
     */
    @Test
    fun `a refusal with no sentence becomes an ask`() = runTest {
        val client = CheckClient("""{"verdict":"refuse","reason":"  "}""")
        assertEquals(TopicVerdict.ASK, TopicChecker(client).check(SOURDOUGH, "k").verdict)
    }

    @Test
    fun `an empty topic or a missing key never reaches the API`() = runTest {
        val client = CheckClient()
        assertEquals(TopicVerdict.ALLOW, TopicChecker(client).check(TopicCheckInputs(""), "k").verdict)
        assertEquals(TopicVerdict.ALLOW, TopicChecker(client).check(SOURDOUGH, "").verdict)
        assertEquals(0, client.calls)
    }

    // --- what the model is actually told -------------------------------------

    @Test
    fun `everything the reader typed is screened, style request included`() = runTest {
        val client = CheckClient()
        TopicChecker(client).check(
            TopicCheckInputs(
                topic = "beekeeping",
                priorKnowledge = "she is 6",
                expectations = "open a hive",
                styleRequest = "as a fairy tale",
            ),
            "k",
        )
        val prompt = client.lastUserPrompt!!
        listOf("beekeeping", "she is 6", "open a hive", "as a fairy tale").forEach {
            assertTrue(prompt.contains(it), "not screened: $it")
        }
    }

    /**
     * The direction that actually costs something. A missed harmful request is
     * caught downstream by the model that writes the chapters; a wrongly
     * refused one is caught by nothing — it is a book the reader cannot write
     * in an app they own, with nobody to appeal to.
     */
    @Test
    fun `the prompt names the legitimate subjects and leans toward allowing`() = runTest {
        val client = CheckClient()
        TopicChecker(client).check(SOURDOUGH, "k")
        val prompt = client.lastUserPrompt!!

        listOf(
            "chemistry of explosives", "toxicology", "history of terrorism",
            "lock-picking", "offensive computer security", "harm reduction",
        ).forEach { assertTrue(prompt.contains(it), "prompt does not protect: $it") }
        assertTrue(prompt.contains("whenever you are unsure whether a SUBJECT is legitimate"))
        assertTrue(prompt.contains("worse failure than"))
    }

    /**
     * The bug this replaces, reported 2026-09-24: "how to kill people quickly"
     * came back as `ask`, which is a walk-past. The refuse criterion was a
     * bare list of five categories and the model read it as the definition —
     * no named victim, no named method, so it matched none of them. A request
     * can be more obviously harmful and LESS specific at the same time, and an
     * enumeration inverts exactly that case.
     */
    @Test
    fun `the refuse criterion leads with a principle and says vagueness is no excuse`() = runTest {
        val client = CheckClient()
        TopicChecker(client).check(SOURDOUGH, "k")
        val prompt = client.lastUserPrompt!!

        assertTrue(prompt.contains("a manual for"))
        assertTrue(prompt.contains("THIS DOES NOT DEPEND ON HOW SPECIFIC THE REQUEST IS"))
        assertTrue(prompt.contains("Vagueness makes"))
        // The list must read as examples, or it becomes the definition again.
        assertTrue(prompt.contains("AMONG OTHERS"))
        assertTrue(prompt.contains("examples, not a definition"))
        // And ask must not be available as a softener for a plain refusal.
        assertTrue(prompt.contains("never use ask to"))
    }

    /**
     * Both ends pinned by worked examples. Concrete anchors calibrate a model
     * far better than abstract rules, and the allow rows are load-bearing:
     * tightening the refuse side is exactly when false refusals appear.
     */
    @Test
    fun `the calibration table pins both ends, including the reported case`() = runTest {
        val client = CheckClient()
        TopicChecker(client).check(SOURDOUGH, "k")
        val prompt = client.lastUserPrompt!!
        val table = prompt.substringAfter("CALIBRATION")

        assertTrue(table.contains("how to kill people quickly"))
        listOf("the chemistry of explosives", "field dressing a deer", "what happens to a body")
            .forEach { assertTrue(table.contains(it), "no allow anchor for: $it") }
        assertTrue(table.lines().count { it.trim().endsWith("allow") } >= 5)
        assertTrue(table.lines().count { it.trim().endsWith("refuse") } >= 4)
    }

    @Test
    fun `the prompt draws the line at instructions rather than at subjects`() = runTest {
        val client = CheckClient()
        TopicChecker(client).check(SOURDOUGH, "k")
        val prompt = client.lastUserPrompt!!

        assertTrue(prompt.contains("working set of instructions"))
        assertTrue(prompt.contains("Would a well-stocked public library carry a book on this?"))
        // The usual jailbreak wrapper is named, so a claimed purpose cannot buy a pass.
        assertTrue(prompt.contains("A stated harmless purpose does not change a refuse"))
    }

    /**
     * The four ways an over-eager gate would make this app worse, each said
     * out loud because a classifier left to its own instincts does all four.
     */
    @Test
    fun `the prompt rules out age rating, taste, quality and legality`() = runTest {
        val client = CheckClient()
        TopicChecker(client).check(SOURDOUGH, "k")
        val prompt = client.lastUserPrompt!!

        assertTrue(prompt.contains("Not an age rating"))
        assertTrue(prompt.contains("Not a taste filter"))
        assertTrue(prompt.contains("Not a quality check"))
        assertTrue(prompt.contains("Not a legality check"))
    }

    @Test
    fun `blank optional fields are left out rather than sent as empty labels`() = runTest {
        val client = CheckClient()
        TopicChecker(client).check(SOURDOUGH, "k")

        assertNull(Regex("^Who is reading.*$", RegexOption.MULTILINE).find(client.lastUserPrompt!!))
        assertTrue(client.lastUserPrompt!!.contains("Topic: sourdough bread"))
    }
}

class TopicVerdictParsingTest {

    @Test
    fun `verdict words are read case- and space-insensitively`() {
        assertEquals(TopicVerdict.REFUSE, verdictOf(" REFUSE "))
        assertEquals(TopicVerdict.ASK, verdictOf("Ask"))
        assertEquals(TopicVerdict.ALLOW, verdictOf("allow"))
        assertEquals(TopicVerdict.ALLOW, verdictOf(""))
    }

    @Test
    fun `settle only touches a refusal that cannot be read`() {
        assertEquals(
            TopicVerdict.ASK,
            settle(TopicCheckResult(TopicVerdict.REFUSE, "")).verdict,
        )
        assertEquals(
            TopicVerdict.REFUSE,
            settle(TopicCheckResult(TopicVerdict.REFUSE, "a reason")).verdict,
        )
        assertEquals(
            TopicVerdict.ASK,
            settle(TopicCheckResult(TopicVerdict.ASK, "")).verdict,
        )
    }
}
