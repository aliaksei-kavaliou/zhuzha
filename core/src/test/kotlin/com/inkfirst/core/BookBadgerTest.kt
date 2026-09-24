package com.inkfirst.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Records what each image call was given, so consistency wiring can be asserted. */
private class RecordingImageClient(
    private val failOn: Set<Int> = emptySet(),
    private val replyText: String? = "A round bolt-headed robot with one antenna and mismatched eyes.",
) : GeminiClient {
    val prompts = mutableListOf<String>()
    val references = mutableListOf<GeneratedImage?>()
    val aspectRatios = mutableListOf<String>()

    override suspend fun generateJson(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject,
        apiKey: String,
    ): String = error("badges should never call generateJson")

    override suspend fun generateText(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        apiKey: String,
    ): String = error("badges should never call generateText")

    override suspend fun generateImage(
        model: String,
        prompt: String,
        aspectRatio: String,
        apiKey: String,
        referenceImage: GeneratedImage?,
    ): GeneratedImage {
        prompts += prompt
        references += referenceImage
        aspectRatios += aspectRatio
        if (prompts.size in failOn) throw GeminiHttpException("boom", status = 500)
        return GeneratedImage(
            bytes = byteArrayOf(prompts.size.toByte()),
            mimeType = "image/png",
            text = replyText,
        )
    }
}

class BookBadgerTest {

    private val course = Course(
        title = "Neural Networks for Beginners",
        slug = "neural-networks-for-beginners",
        topicPrompt = "neural networks",
        priorKnowledge = "some python",
        dailyMinutes = 30,
        style = "headfirst-inspired",
        model = "gemini-3.7-flash",
        created = "2026-09-14",
        chapters = listOf(
            PersistedChapter(1, "01-intro", "What Even Is an AI?", listOf("everyday framing"), 30),
        ),
    )

    private fun saved() = mutableMapOf<String, ByteArray>()

    @Test
    fun `draws one badge per kind and saves each under its kind`() = runTest {
        val client = RecordingImageClient()
        val files = saved()

        val result = BookBadger(client).drawMissing(
            course = course,
            apiKey = "k",
            saveImage = { kind, ext, bytes -> files["$kind.$ext"] = bytes },
        )

        assertEquals(BADGE_KINDS.size, result.generated)
        assertEquals(0, result.failed)
        assertEquals(setOf("exercise.png", "recap.png", "answer.png"), files.keys)
        assertTrue(client.aspectRatios.all { it == BADGE_ASPECT_RATIO })
    }

    @Test
    fun `later badges carry the first picture and its description`() = runTest {
        val client = RecordingImageClient()

        val result = BookBadger(client).drawMissing(
            course = course,
            apiKey = "k",
            saveImage = { _, _, _ -> },
        )

        // The first call invents the mascot: nothing to copy yet.
        assertNull(client.references[0])
        assertTrue(client.prompts[0].contains("INVENT THE BOOK'S MASCOT"))
        assertTrue(client.prompts[0].contains(course.title))

        // Every later call copies it, by picture and by description.
        assertNotNull(client.references[1])
        assertTrue(client.prompts[1].contains("DRAW THE SAME CHARACTER AGAIN"))
        assertTrue(client.prompts[1].contains("bolt-headed robot"))
        assertFalse(client.prompts[1].contains("INVENT"))
        assertTrue(result.mascot.contains("bolt-headed robot"))
    }

    @Test
    fun `skips kinds already on disk and copies the one that exists`() = runTest {
        val client = RecordingImageClient()
        val existing = GeneratedImage(byteArrayOf(9), "image/png")
        val files = saved()

        val result = BookBadger(client).drawMissing(
            course = course,
            apiKey = "k",
            existing = setOf("exercise"),
            knownMascot = "A round bolt-headed robot.",
            loadBadge = { kind -> if (kind == "exercise") existing else null },
            saveImage = { kind, ext, bytes -> files["$kind.$ext"] = bytes },
        )

        assertEquals(BADGE_KINDS.size - 1, result.generated)
        assertEquals(setOf("recap.png", "answer.png"), files.keys)
        // The badge already on disk is what the new one is drawn from —
        // otherwise filling a gap later invents a second mascot.
        assertEquals(existing, client.references[0])
        assertTrue(client.prompts[0].contains("DRAW THE SAME CHARACTER AGAIN"))
    }

    @Test
    fun `nothing to do when every badge exists`() = runTest {
        val client = RecordingImageClient()

        val result = BookBadger(client).drawMissing(
            course = course,
            apiKey = "k",
            existing = BADGE_KINDS.toSet(),
            knownMascot = "A robot.",
            saveImage = { _, _, _ -> error("must not draw") },
        )

        assertEquals(0, result.generated)
        assertEquals("A robot.", result.mascot)
        assertTrue(client.prompts.isEmpty())
    }

    @Test
    fun `a failed badge does not stop the rest`() = runTest {
        val client = RecordingImageClient(failOn = setOf(1))
        val files = saved()

        val result = BookBadger(client).drawMissing(
            course = course,
            apiKey = "k",
            saveImage = { kind, ext, bytes -> files["$kind.$ext"] = bytes },
        )

        assertEquals(BADGE_KINDS.size - 1, result.generated)
        assertEquals(1, result.failed)
        assertEquals(setOf("recap.png", "answer.png"), files.keys)
    }

    @Test
    fun `badge style forbids the lettering the figure style encourages`() {
        val prompt = buildBadgePrompt(course, "exercise")
        assertTrue(prompt.contains("ABSOLUTELY NO TEXT"))
        assertFalse(prompt.contains("speech bubble is welcome"))
    }

    @Test
    fun `a formal course gets an emblem rather than a cartoon`() {
        val formal = course.copy(style = StylePresets.TEXTBOOK.key)
        assertTrue(buildBadgePrompt(formal, "recap").contains("no face"))
        assertTrue(buildBadgePrompt(course, "recap").contains("cartoon mascot"))
    }

    /**
     * The arms above are keyed on the built-in presets, so a described style
     * matches none of them and every custom book — sober or silly — used to
     * fall through to the friendly-cartoon arm. The style brief writes this
     * line in the same pass as the prompt.
     */
    @Test
    fun `a custom style brings its own mascot register`() {
        val custom = course.copy(
            style = StylePresets.CUSTOM_KEY,
            stylePrompt = "You are writing a chapter of a study book told as a fairy tale.",
            styleMascot = "a storybook animal in a cloak, wide-eyed and friendly",
        )
        val prompt = buildBadgePrompt(custom, "recap")
        assertTrue(prompt.contains("a storybook animal in a cloak"))
        assertFalse(prompt.contains("cartoon mascot with a face"))
    }

    @Test
    fun `a custom style with no mascot line falls back to the key's arm`() {
        val custom = course.copy(style = StylePresets.CUSTOM_KEY, stylePrompt = "You are writing X.")
        assertTrue(buildBadgePrompt(custom, "recap").contains("cartoon mascot"))
    }
}

class BadgeKindTest {

    @Test
    fun `every code language collapses to one code badge`() {
        listOf("python", "PY", "bash", "sql", "json", "kotlin", "").forEach {
            assertEquals("code", badgeKindFor(it), "kind=$it")
        }
    }

    @Test
    fun `pictures and page furniture get no badge`() {
        assertNull(badgeKindFor("figure"))
        assertNull(badgeKindFor("meta"))
        // A kind that couldn't be a file name is refused rather than sanitised.
        assertNull(badgeKindFor("../etc/passwd"))
    }

    @Test
    fun `a kind the model invents keeps its own badge`() {
        assertEquals("gotcha", badgeKindFor("Gotcha"))
        assertTrue(buildBadgePrompt(courseFixture, "gotcha").contains("\"gotcha\""))
    }

    @Test
    fun `discovery finds what a chapter contains, in order, with answer riding along`() {
        val md = """
            # Setting Up

            ```python
            import torch
            ```

            ```exercise
            What does it do?
            answer: it imports.
            ```

            ```figure alt="a robot"```

            ```recap
            That's the idea.
            ```
        """.trimIndent()
        // Discovery order, with "answer" appended — it is revealed inside an
        // exercise rather than being a fence of its own, so it has no place
        // of its own in the text to be found at.
        assertEquals(listOf("code", "exercise", "recap", "answer"), badgeKindsIn(md))
    }

    @Test
    fun `a chatty model cannot run up an unbounded bill`() {
        val md = (1..12).joinToString("\n\n") { "```kind$it\nbody\n```" }
        assertEquals(MAX_BADGE_KINDS, badgeKindsIn(md).size)
    }

    private val courseFixture = Course(
        title = "T", slug = "t", topicPrompt = "x", priorKnowledge = "",
        dailyMinutes = 20, style = "headfirst-inspired", model = "m",
        created = "2026-09-14", chapters = emptyList(),
    )
}
