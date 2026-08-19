package com.inkfirst.core

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class RecordingMemoryClient(private val response: String = "summary paragraph") : GeminiClient {
    var lastSystem: String? = null
    var lastUserPrompt: String? = null

    override suspend fun generateJson(
        model: String, systemInstruction: String, userPrompt: String,
        responseSchema: JsonObject, apiKey: String,
    ): String = error("memory update should never call generateJson")

    override suspend fun generateText(
        model: String, systemInstruction: String, userPrompt: String, apiKey: String,
    ): String {
        lastSystem = systemInstruction
        lastUserPrompt = userPrompt
        return response
    }
}

class MemoryUpdaterTest {

    private val course = Course(
        title = "NN for Beginners",
        slug = "nn",
        topicPrompt = "neural networks",
        priorKnowledge = "",
        expectations = "build basic ai model",
        dailyMinutes = 30,
        style = "headfirst-inspired",
        model = "gemini-3.7-flash",
        created = "2026-08-18",
        chapters = listOf(
            PersistedChapter(1, "01-neuron", "The Artificial Neuron", listOf("weighted sums"), 30),
        ),
    )

    @Test
    fun `happy path returns model output`() = runTest {
        val client = RecordingMemoryClient(response = "In chapter 1 we introduced neurons using the scale metaphor...")
        val out = MemoryUpdater(client).summarize(
            course, n = 1, chapterMarkdown = "# Neuron\n\ncontent", apiKey = "sk",
        )
        assertTrue(out.startsWith("In chapter 1"))
    }

    @Test
    fun `chapter markdown is included in the user prompt`() = runTest {
        val markdown = "# Neuron\n\nA neuron is like a tiny voting machine."
        val client = RecordingMemoryClient()
        MemoryUpdater(client).summarize(course, n = 1, chapterMarkdown = markdown, apiKey = "sk")
        assertTrue(client.lastUserPrompt!!.contains("voting machine"))
    }

    @Test
    fun `style preset system prompt is forwarded`() = runTest {
        val client = RecordingMemoryClient()
        MemoryUpdater(client).summarize(course, n = 1, chapterMarkdown = "# X", apiKey = "sk")
        assertEquals(StylePresets.HEADFIRST_INSPIRED.systemPrompt, client.lastSystem)
    }

    @Test
    fun `unknown chapter number throws`() {
        val client = RecordingMemoryClient()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { MemoryUpdater(client).summarize(course, n = 99, chapterMarkdown = "# X", apiKey = "sk") }
        }
    }

    @Test
    fun `blank api key and blank markdown are rejected`() {
        val client = RecordingMemoryClient()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { MemoryUpdater(client).summarize(course, n = 1, chapterMarkdown = "# X", apiKey = "") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { MemoryUpdater(client).summarize(course, n = 1, chapterMarkdown = "", apiKey = "sk") }
        }
    }
}
