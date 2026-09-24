package com.inkfirst.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    var lastTemperature: Double? = null

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

    override suspend fun generateJson(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject,
        apiKey: String,
        temperature: Double,
    ): String {
        lastTemperature = temperature
        return generateJson(model, systemInstruction, userPrompt, responseSchema, apiKey)
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

        assertTrue(prompt.contains("a MANUAL FOR"))
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

    /**
     * The defect behind "we need more serious protection": nothing set a
     * temperature anywhere, so the check ran at the model's default (~1.0) and
     * the same request came back refused on one run and allowed on the next.
     * A verdict that is not reproducible is not a verdict.
     */
    @Test
    fun `the check is pinned to a judging temperature, not left to sample`() = runTest {
        val client = CheckClient()
        TopicChecker(client).check(SOURDOUGH, "k")

        assertEquals(0.0, client.lastTemperature)
    }

    /**
     * The second gate. The outline is the model's own reading of the request,
     * written out as chapter titles, and it is far more concrete than the
     * sentence that produced it — which is what the first gate has least to go
     * on.
     */
    @Test
    fun `the outline is screened as the strongest evidence when present`() = runTest {
        val client = CheckClient()
        TopicChecker(client).check(
            SOURDOUGH.copy(outline = "1. Choosing Your Moment\n   - pick an exit"),
            "k",
        )
        val prompt = client.lastUserPrompt!!

        assertTrue(prompt.contains("WHAT THE BOOK WOULD ACTUALLY CONTAIN"))
        assertTrue(prompt.contains("Choosing Your Moment"))
        assertTrue(prompt.contains("Judge it over the"))
    }

    @Test
    fun `without an outline the prompt does not pretend to have one`() = runTest {
        val client = CheckClient()
        TopicChecker(client).check(SOURDOUGH, "k")

        assertFalse(client.lastUserPrompt!!.contains("WHAT THE BOOK WOULD ACTUALLY CONTAIN"))
    }

    /**
     * "How to rubber the bank" rendered an outline. The refuse principle only
     * covered physical harm to PEOPLE, so a robbery manual sat outside it —
     * the model applied the rule correctly and the rule was too narrow.
     */
    @Test
    fun `the refuse principle covers serious crime, not only physical harm`() = runTest {
        val client = CheckClient()
        TopicChecker(client).check(SOURDOUGH, "k")
        val prompt = client.lastUserPrompt!!

        assertTrue(prompt.contains("seriously criminal"))
        assertTrue(prompt.contains("robbery, burglary, theft"))
        // And the counterweight, or this is where false refusals start.
        assertTrue(prompt.contains("UNDERSTANDING either family is allowed"))
        assertTrue(prompt.contains("how a con works so"))
        val table = prompt.substringAfter("CALIBRATION")
        assertTrue(table.contains("how to rob a bank"))
        assertTrue(table.contains("the history of bank robbery"))
    }
}

/**
 * The last gate, and the only one that sees actual text. Its whole job is
 * narrower than the request gate's, and its failure is more expensive: a
 * false refusal here destroys a chapter already generated and paid for.
 */
class ChapterCheckTest {

    private val chapter = "Why Things Go Boom"
    private val body = "An explosion is a very fast release of gas."

    @Test
    fun `a refused chapter reports what crossed the line`() = runTest {
        val client = CheckClient("""{"verdict":"refuse","reason":"Section 3 gives reagent ratios."}""")
        val result = TopicChecker(client).checkChapter(chapter, body, "k")

        assertEquals(TopicVerdict.REFUSE, result.verdict)
        assertEquals("Section 3 gives reagent ratios.", result.reason)
    }

    /**
     * A refusal the reader cannot act on is worse than none: the chapter is
     * gone and they cannot steer the rewrite away from whatever caused it. So
     * the gate never destroys work it cannot explain destroying.
     */
    @Test
    fun `a refusal with no sentence keeps the chapter`() = runTest {
        val client = CheckClient("""{"verdict":"refuse","reason":""}""")
        assertEquals(TopicVerdict.ALLOW, TopicChecker(client).checkChapter(chapter, body, "k").verdict)
    }

    /**
     * Ask exists so a reader can rephrase and try again. There is nothing to
     * rephrase once the text is written — the question would have no answer
     * and the reader no move.
     */
    @Test
    fun `ask is not a verdict a written chapter can act on, so it keeps`() = runTest {
        val client = CheckClient("""{"verdict":"ask","reason":"not sure about section 2"}""")
        assertEquals(TopicVerdict.ALLOW, TopicChecker(client).checkChapter(chapter, body, "k").verdict)
    }

    @Test
    fun `a dropped call or a malformed reply keeps the chapter`() = runTest {
        assertEquals(
            TopicVerdict.ALLOW,
            TopicChecker(CheckClient(fail = GeminiHttpException("no network")))
                .checkChapter(chapter, body, "k").verdict,
        )
        assertEquals(
            TopicVerdict.ALLOW,
            TopicChecker(CheckClient("not json")).checkChapter(chapter, body, "k").verdict,
        )
    }

    @Test
    fun `it judges at a pinned temperature like every other verdict`() = runTest {
        val client = CheckClient()
        TopicChecker(client).checkChapter(chapter, body, "k")
        assertEquals(0.0, client.lastTemperature)
    }

    /**
     * The bar here is NARROWER than at the request stage, and saying so is
     * what stops this gate relitigating a subject two earlier gates approved.
     * A book about explosives chemistry is supposed to contain chemistry.
     */
    @Test
    fun `the prompt settles the subject and asks only about actionable instruction`() = runTest {
        val client = CheckClient()
        TopicChecker(client).checkChapter(chapter, body, "k")
        val prompt = client.lastUserPrompt!!

        assertTrue(prompt.contains("THE SUBJECT IS SETTLED AND IS NOT YOUR QUESTION"))
        assertTrue(prompt.contains("already paid for"))
        assertTrue(prompt.contains("WORKING PROCEDURE"))
        assertTrue(prompt.contains("carry it out FROM THIS PAGE"))
        assertTrue(prompt.contains("prefer allow"))
        // And the chapter itself is what is being read.
        assertTrue(prompt.contains(body))
        assertTrue(prompt.contains(chapter))
    }

    @Test
    fun `an empty chapter or a missing key never reaches the API`() = runTest {
        val client = CheckClient()
        TopicChecker(client).checkChapter(chapter, "", "k")
        TopicChecker(client).checkChapter(chapter, body, "")
        assertEquals(0, client.calls)
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
