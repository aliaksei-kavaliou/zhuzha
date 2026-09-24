package com.inkfirst.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Records the outgoing brief call so the prompt's rules can be asserted on. */
private class RecordingBriefClient(private val response: String) : GeminiClient {
    var lastSystem: String? = null
    var lastUserPrompt: String? = null
    var lastSchema: JsonObject? = null

    override suspend fun generateJson(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject,
        apiKey: String,
    ): String {
        lastSystem = systemInstruction
        lastUserPrompt = userPrompt
        lastSchema = responseSchema
        return response
    }

    override suspend fun generateText(
        model: String, systemInstruction: String, userPrompt: String, apiKey: String,
    ): String = error("style briefs go through generateJson")

    override suspend fun generateImage(
        model: String,
        prompt: String,
        aspectRatio: String,
        apiKey: String,
        referenceImage: GeneratedImage?,
    ): GeneratedImage = error("style briefs draw nothing")
}

private const val GOOD_REPLY = """
    {
      "prompt": "You are writing a chapter of a study book told as a fairy tale. Open each section with a small scene. Let a character discover the idea by needing it.",
      "mascot": "A storybook animal in a cloak, wide-eyed and friendly, the kind that would appear in a picture book."
    }
"""

class StyleBriefGeneratorTest {

    @Test
    fun `expands a wish into a prompt and a mascot line`() = runTest {
        val client = RecordingBriefClient(GOOD_REPLY)
        val brief = StyleBriefGenerator(client).expand("write it as a fairy tale", apiKey = "k")

        assertTrue(brief.prompt.startsWith("You are writing a chapter"))
        assertTrue(brief.mascot.contains("storybook animal"))
    }

    @Test
    fun `the wish goes through verbatim`() = runTest {
        val client = RecordingBriefClient(GOOD_REPLY)
        StyleBriefGenerator(client).expand("like a friendly no-jargon guide", apiKey = "k")

        assertTrue(client.lastUserPrompt!!.contains("like a friendly no-jargon guide"))
    }

    @Test
    fun `the brief is told to drop a named series and keep its pedagogy`() = runTest {
        val client = RecordingBriefClient(GOOD_REPLY)
        StyleBriefGenerator(client).expand("like those yellow For Dummies books", apiKey = "k")

        val prompt = client.lastUserPrompt!!
        assertTrue(prompt.contains("Never repeat the name"))
        assertTrue(prompt.contains("trade dress"))
    }

    /**
     * The four things the request already sets elsewhere. The brief lands in
     * the SYSTEM channel, which outranks the user turn, so a brief with an
     * opinion about chapter length would quietly overrule estMinutes and a
     * brief with an opinion about language would overrule the course's own.
     */
    @Test
    fun `the brief is kept off the settings the rest of the request owns`() = runTest {
        val client = RecordingBriefClient(GOOD_REPLY)
        StyleBriefGenerator(client).expand("as a fairy tale", apiKey = "k")

        val prompt = client.lastUserPrompt!!
        assertTrue(prompt.contains("LEAVE THESE ALONE"))
        listOf("language", "How long a chapter is", "Markdown", "pictures").forEach {
            assertTrue(prompt.contains(it), "missing guard for: $it")
        }
    }

    @Test
    fun `malformed JSON is reported, not swallowed`() = runTest {
        val client = RecordingBriefClient("not json at all")
        val e = assertThrows(StyleBriefException::class.java) {
            kotlinx.coroutines.runBlocking {
                StyleBriefGenerator(client).expand("as a fairy tale", apiKey = "k")
            }
        }
        assertEquals("not json at all", e.rawResponse)
    }

    /**
     * Gemini honours a required field by emitting the KEY, not by filling it.
     * An empty prompt reaching the course would strip the system instruction
     * from every call the book ever makes, and nothing downstream would look
     * wrong enough to notice.
     */
    @Test
    fun `an empty instruction is refused rather than stored`() = runTest {
        val client = RecordingBriefClient("""{"prompt": "   ", "mascot": "a fox"}""")
        assertThrows(StyleBriefException::class.java) {
            kotlinx.coroutines.runBlocking {
                StyleBriefGenerator(client).expand("as a fairy tale", apiKey = "k")
            }
        }
    }

    @Test
    fun `a missing mascot is fine — badges have a fallback, prompts do not`() = runTest {
        val client = RecordingBriefClient("""{"prompt": "You are writing a chapter of X."}""")
        val brief = StyleBriefGenerator(client).expand("as a fairy tale", apiKey = "k")
        assertEquals("", brief.mascot)
    }

    @Test
    fun `a blank wish never reaches the API`() = runTest {
        val client = RecordingBriefClient(GOOD_REPLY)
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                StyleBriefGenerator(client).expand("   ", apiKey = "k")
            }
        }
        assertEquals(null, client.lastUserPrompt)
    }
}

class StylePresetResolutionTest {

    private val course = Course(
        title = "Sourdough",
        slug = "sourdough",
        topicPrompt = "sourdough bread",
        priorKnowledge = "",
        dailyMinutes = 30,
        style = "headfirst-inspired",
        model = "gemini-3.7-flash",
        created = "2026-09-24",
        chapters = emptyList(),
    )

    @Test
    fun `a built-in key resolves to its own preset`() {
        val resolved = StylePresets.forCourse(course.copy(style = StylePresets.TEXTBOOK.key))
        assertSame(StylePresets.TEXTBOOK, resolved)
    }

    @Test
    fun `a custom style sends the brief that was written for it`() {
        val resolved = StylePresets.forCourse(
            course.copy(style = StylePresets.CUSTOM_KEY, stylePrompt = "You are writing a fairy tale."),
        )
        assertEquals(StylePresets.CUSTOM_KEY, resolved.key)
        assertEquals("You are writing a fairy tale.", resolved.systemPrompt)
    }

    /**
     * Both of these are the same decision: a book must stay writable. A
     * hand-edited file, or a course.json from before custom styles existed,
     * gets the house style rather than an exception or — far worse — an empty
     * system instruction on every call.
     */
    @Test
    fun `an unknown key and a custom style with no brief both fall back`() {
        assertSame(StylePresets.DEFAULT, StylePresets.forCourse(course.copy(style = "retired-preset")))
        assertSame(
            StylePresets.DEFAULT,
            StylePresets.forCourse(course.copy(style = StylePresets.CUSTOM_KEY)),
        )
    }

    /**
     * byKey feeds the generators directly in older call paths and must never
     * hand back a preset with no prompt in it.
     */
    @Test
    fun `the custom row is offered by the picker but never returned by byKey`() {
        assertEquals(null, StylePresets.byKey(StylePresets.CUSTOM_KEY))
        assertTrue(StylePresets.CUSTOM in StylePresets.CHOICES)
        assertFalse(StylePresets.CUSTOM in StylePresets.ALL)
        assertTrue(StylePresets.ALL.all { it.systemPrompt.isNotBlank() })
    }
}
