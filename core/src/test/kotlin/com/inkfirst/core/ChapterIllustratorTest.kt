package com.inkfirst.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fake image client. [failOn] lists the 1-based call numbers that should
 * blow up, so tests can prove a single bad figure doesn't sink the chapter.
 */
private class FakeImageClient(
    private val failOn: Set<Int> = emptySet(),
    private val mimeType: String = "image/png",
) : GeminiClient {
    val prompts = mutableListOf<String>()
    var lastModel: String? = null
    var lastAspectRatio: String? = null

    override suspend fun generateJson(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject,
        apiKey: String,
    ): String = error("illustrator should never call generateJson")

    override suspend fun generateText(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        apiKey: String,
    ): String = error("illustrator should never call generateText")

    override suspend fun generateImage(
        model: String,
        prompt: String,
        aspectRatio: String,
        apiKey: String,
        referenceImage: GeneratedImage?,
    ): GeneratedImage {
        prompts += prompt
        lastModel = model
        lastAspectRatio = aspectRatio
        if (prompts.size in failOn) throw GeminiHttpException("boom", status = 500)
        return GeneratedImage(bytes = byteArrayOf(1, 2, 3), mimeType = mimeType)
    }
}

class ChapterIllustratorTest {

    private val course = Course(
        title = "Neural Networks for Beginners",
        slug = "neural-networks-for-beginners",
        topicPrompt = "neural networks",
        priorKnowledge = "some python",
        dailyMinutes = 30,
        style = "headfirst-inspired",
        model = "gemini-3.7-flash",
        created = "2026-09-10",
        chapters = listOf(
            PersistedChapter(1, "01-intro", "What Even Is an AI?", listOf("everyday framing"), 30),
        ),
    )
    private val chapter = course.chapters.first()

    private val markdown = """
        # What Even Is an AI?

        Machines that learn are less magical than they sound.

        ```figure alt="A very tired robot sorting a mountain of socks"```

        The sorting never really stops.

        ```figure alt="A kitchen with exactly one overworked cook"```
    """.trimIndent()

    @Test
    fun `generates a picture per pending slot and writes src back`() = runTest {
        val client = FakeImageClient()
        val saved = mutableMapOf<String, ByteArray>()

        val result = ChapterIllustrator(client, model = "test-image-model")
            .illustrate(course, chapter, markdown, apiKey = "k") { name, bytes ->
                saved[name] = bytes
            }

        assertEquals(2, result.generated)
        assertEquals(0, result.failed)
        assertEquals(setOf("01-intro-1.png", "01-intro-2.png"), saved.keys)
        assertEquals("test-image-model", client.lastModel)
        assertEquals(FIGURE_ASPECT_RATIO, client.lastAspectRatio)

        val slots = findFigureSlots(result.markdown)
        assertEquals(listOf("01-intro-1.png", "01-intro-2.png"), slots.map { it.src })
        assertTrue(slots.none { it.isPending })
    }

    @Test
    fun `a chapter stops at its ceiling and says how many it left`() = runTest {
        // Eight briefs, a ceiling of two: the model is not misbehaving by
        // asking for eight, and the reader is not charged for eight.
        val eight = (1..8).joinToString("\n\n") { """```figure alt="brief $it"```""" }
        val client = FakeImageClient()
        val saved = mutableMapOf<String, ByteArray>()

        val result = ChapterIllustrator(client)
            .illustrate(course, chapter, eight, apiKey = "k", maxFigures = 2) { name, bytes ->
                saved[name] = bytes
            }

        assertEquals(2, result.generated)
        assertEquals(6, result.skipped)
        assertEquals(2, saved.size)
    }

    @Test
    fun `the ones over the ceiling keep their briefs and stay pending`() = runTest {
        // Nothing is lost by stopping: an undrawn slot renders its alt text
        // and the reader's menu still offers to draw it.
        val three = (1..3).joinToString("\n\n") { """```figure alt="brief $it"```""" }

        val result = ChapterIllustrator(FakeImageClient())
            .illustrate(course, chapter, three, apiKey = "k", maxFigures = 1) { _, _ -> }

        val slots = findFigureSlots(result.markdown)
        assertEquals(listOf(false, true, true), slots.map { it.isPending })
        assertTrue(slots.all { it.alt.startsWith("brief") })
    }

    @Test
    fun `a book set to no pictures draws none of them`() = runTest {
        val client = FakeImageClient()

        val result = ChapterIllustrator(client)
            .illustrate(course.copy(figures = "none"), chapter, markdown, apiKey = "k") { _, _ -> }

        assertEquals(0, result.generated)
        assertEquals(2, result.skipped)
        assertTrue(client.prompts.isEmpty())
    }

    @Test
    fun `prompt carries the brief, the house style and the surrounding prose`() = runTest {
        val client = FakeImageClient()
        ChapterIllustrator(client).illustrate(course, chapter, markdown, apiKey = "k") { _, _ -> }

        val first = client.prompts.first()
        assertTrue(first.contains("A very tired robot sorting a mountain of socks"))
        assertTrue(first.contains("What Even Is an AI?"))
        assertTrue(first.contains("Machines that learn are less magical than they sound."))
        assertTrue(first.contains("hand-drawn ink sketch"))
        // Text is allowed but bounded — the rule that keeps figures legible
        // on a small grey panel. See SKETCH_HOUSE_STYLE.
        assertTrue(first.contains("TEXT SIZE IS CRITICAL"))
        assertTrue(first.contains("pure greyscale"))
    }

    @Test
    fun `the cover's brief writer is told the language too`() {
        // This was the actual bug. A chapter's own figures get their briefs
        // from the chapter model, which is told the book's language, so they
        // lettered clean Cyrillic. The COVER is written by a second call that
        // never mentioned it, so the brief came back in English — including
        // the words for the speech bubble — and the picture followed.
        val russian = course.copy(language = "Russian")

        val brief = buildCoverBriefPrompt(russian, chapter)
        val closing = buildClosingBriefPrompt(russian, chapter, coverBrief = "A bewildered beginner…")

        assertTrue(brief.contains("must be in Russian"), brief)
        assertTrue(closing.contains("must be in Russian"), closing)
    }

    @Test
    fun `a book in another language says so to the illustrator`() = runTest {
        // A Russian book opened with a character shouting "HOW DOES THIS
        // FIT?!" — observed on the Boox 2026-09-23. The picture model letters
        // Cyrillic perfectly well; nothing had told it which language to
        // letter in, and every instruction it was given was in English.
        val client = FakeImageClient()

        ChapterIllustrator(client)
            .illustrate(course.copy(language = "Russian"), chapter, markdown, apiKey = "k") { _, _ -> }

        val prompt = client.prompts.first()
        assertTrue(prompt.contains("must be in Russian"), prompt)
        // And an out, because a script it cannot letter cleanly is worse than
        // no lettering at all.
        assertTrue(prompt.contains("NO words at all"), prompt)
    }

    @Test
    fun `an English book is not lectured about language`() = runTest {
        val client = FakeImageClient()

        ChapterIllustrator(client).illustrate(course, chapter, markdown, apiKey = "k") { _, _ -> }

        assertTrue(client.prompts.first().contains("words in the picture are in English"))
    }

    @Test
    fun `one failing figure does not stop the others`() = runTest {
        val client = FakeImageClient(failOn = setOf(1))
        val saved = mutableMapOf<String, ByteArray>()

        val result = ChapterIllustrator(client)
            .illustrate(course, chapter, markdown, apiKey = "k") { name, bytes ->
                saved[name] = bytes
            }

        assertEquals(1, result.generated)
        assertEquals(1, result.failed)
        assertEquals(setOf("01-intro-2.png"), saved.keys)

        // The failed slot stays pending, so a later retry picks up exactly it.
        val slots = findFigureSlots(result.markdown)
        assertTrue(slots[0].isPending)
        assertFalse(slots[1].isPending)
    }

    @Test
    fun `already-illustrated slots are skipped so reruns only fill the gaps`() = runTest {
        val client = FakeImageClient()
        val partly = withFigureSrc(markdown, mapOf(1 to "01-intro-1.png"))

        val result = ChapterIllustrator(client)
            .illustrate(course, chapter, partly, apiKey = "k") { _, _ -> }

        assertEquals(1, client.prompts.size)
        assertTrue(client.prompts.single().contains("overworked cook"))
        assertEquals(1, result.generated)
    }

    @Test
    fun `a chapter with no figures costs nothing`() = runTest {
        val client = FakeImageClient()
        val result = ChapterIllustrator(client)
            .illustrate(course, chapter, "# Just words\n\nNo pictures here.", apiKey = "k") { _, _ -> }

        assertTrue(client.prompts.isEmpty())
        assertEquals(0, result.generated)
        assertEquals("# Just words\n\nNo pictures here.", result.markdown)
    }

    @Test
    fun `file extension follows the mime type the model returned`() = runTest {
        val client = FakeImageClient(mimeType = "image/jpeg")
        val saved = mutableListOf<String>()

        ChapterIllustrator(client).illustrate(course, chapter, markdown, apiKey = "k") { name, _ ->
            saved += name
        }

        assertEquals(listOf("01-intro-1.jpg", "01-intro-2.jpg"), saved)
    }

    @Test
    fun `progress is reported for every slot`() = runTest {
        val client = FakeImageClient()
        val ticks = mutableListOf<Pair<Int, Int>>()

        ChapterIllustrator(client).illustrate(
            course, chapter, markdown, apiKey = "k",
            onProgress = { done, total -> ticks += done to total },
            saveImage = { _, _ -> },
        )

        assertEquals(listOf(0 to 2, 1 to 2, 2 to 2), ticks)
    }
}
