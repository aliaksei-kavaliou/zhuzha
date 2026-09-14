package com.inkfirst.core

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Records what the chapter generator sent so tests can assert on the outgoing payload. */
private class RecordingChapterClient(
    private val response: String = "# chapter body\n\nHello.",
) : GeminiClient {
    var lastModel: String? = null
    var lastSystem: String? = null
    var lastUserPrompt: String? = null
    var lastApiKey: String? = null

    override suspend fun generateJson(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject,
        apiKey: String,
    ): String = error("chapter path should never call generateJson")

    override suspend fun generateText(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        apiKey: String,
    ): String {
        lastModel = model
        lastSystem = systemInstruction
        lastUserPrompt = userPrompt
        lastApiKey = apiKey
        return response
    }

    override suspend fun generateImage(
        model: String,
        prompt: String,
        aspectRatio: String,
        apiKey: String,
    ): GeneratedImage = error("chapter path should never call generateImage")

}

class ChapterGeneratorTest {

    private val course = Course(
        title = "Neural Networks for Beginners",
        slug = "neural-networks-for-beginners",
        topicPrompt = "neural networks",
        priorKnowledge = "some python",
        expectations = "build basic ai model",
        dailyMinutes = 30,
        style = "practical",
        model = "gemini-3.7-flash",
        created = "2026-08-18",
        chapters = listOf(
            PersistedChapter(1, "01-intro", "What Even Is an AI?", listOf("everyday framing"), 30),
            PersistedChapter(2, "02-neuron", "The Artificial Neuron", listOf("weighted sums"), 30),
        ),
    )

    @Test
    fun `happy path returns model output verbatim`() = runTest {
        val client = RecordingChapterClient(response = "# ch1\n\ncontent")
        val md = ChapterGenerator(client).generate(course, n = 1, memory = "", apiKey = "sk")
        assertEquals("# ch1\n\ncontent", md)
    }

    @Test
    fun `style preset system prompt is forwarded`() = runTest {
        val client = RecordingChapterClient()
        ChapterGenerator(client).generate(course, n = 1, memory = "", apiKey = "sk")
        assertEquals(StylePresets.PRACTICAL.systemPrompt, client.lastSystem)
    }

    @Test
    fun `unknown style key falls back to DEFAULT preset without crashing`() = runTest {
        val badStyle = course.copy(style = "totally-made-up")
        val client = RecordingChapterClient()
        ChapterGenerator(client).generate(badStyle, n = 1, memory = "", apiKey = "sk")
        assertEquals(StylePresets.DEFAULT.systemPrompt, client.lastSystem)
    }

    @Test
    fun `memory is included in the user prompt`() = runTest {
        val mem = "In ch1 we used the light-switch metaphor for a neuron."
        val client = RecordingChapterClient()
        ChapterGenerator(client).generate(course, n = 2, memory = mem, apiKey = "sk")
        assertTrue(client.lastUserPrompt!!.contains(mem))
    }

    @Test
    fun `unknown chapter number throws`() {
        val client = RecordingChapterClient()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ChapterGenerator(client).generate(course, n = 99, memory = "", apiKey = "k") }
        }
    }

    @Test
    fun `blank api key throws before hitting client`() {
        val client = RecordingChapterClient()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ChapterGenerator(client).generate(course, n = 1, memory = "", apiKey = "") }
        }
    }

    @Test
    fun `model override is forwarded, default is DEFAULT_GEMINI_MODEL`() = runTest {
        val c1 = RecordingChapterClient()
        ChapterGenerator(c1).generate(course, n = 1, memory = "", apiKey = "k")
        assertEquals(DEFAULT_GEMINI_MODEL, c1.lastModel)

        val c2 = RecordingChapterClient()
        ChapterGenerator(c2, model = "gemini-custom").generate(course, n = 1, memory = "", apiKey = "k")
        assertEquals("gemini-custom", c2.lastModel)
    }
}
