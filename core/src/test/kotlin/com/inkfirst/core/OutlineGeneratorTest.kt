package com.inkfirst.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Records what the generator sent so tests can assert on the outgoing payload. */
private class RecordingGeminiClient(
    private val response: String,
) : GeminiClient {
    var lastModel: String? = null
    var lastSystem: String? = null
    var lastUserPrompt: String? = null
    var lastSchema: JsonObject? = null
    var lastApiKey: String? = null

    override suspend fun generateJson(
        model: String,
        systemInstruction: String,
        userPrompt: String,
        responseSchema: JsonObject,
        apiKey: String,
    ): String {
        lastModel = model
        lastSystem = systemInstruction
        lastUserPrompt = userPrompt
        lastSchema = responseSchema
        lastApiKey = apiKey
        return response
    }

    override suspend fun generateText(
        model: String, systemInstruction: String, userPrompt: String, apiKey: String,
    ): String = error("outline path should never call generateText")

    override suspend fun generateImage(
        model: String,
        prompt: String,
        aspectRatio: String,
        apiKey: String,
        referenceImage: GeneratedImage?,
    ): GeneratedImage = error("outline path should never call generateImage")

}

class OutlineGeneratorTest {

    private val validJson = """
        {
          "title": "Kotlin for PHP Developers",
          "chapters": [
            {"n": 1, "slug": "", "title": "Why Kotlin feels weird",
             "objectives": ["explain null safety", "read a data class"], "estMinutes": 40},
            {"n": 2, "slug": "", "title": "Collections & lambdas",
             "objectives": ["map/filter", "trailing lambda syntax"], "estMinutes": 45}
          ]
        }
    """.trimIndent()

    @Test
    fun `happy path parses outline and derives slugs`() = runTest {
        val client = RecordingGeminiClient(validJson)
        val generator = OutlineGenerator(client, model = "gemini-test-model")

        val outline = generator.generate(
            topic = "Kotlin for PHP devs",
            dailyMinutes = 45,
            priorKnowledge = "PHP for 10 years",
            style = StylePresets.HEADFIRST_INSPIRED,
            apiKey = "sk-fake",
        )

        assertEquals("Kotlin for PHP Developers", outline.title)
        assertEquals(2, outline.chapters.size)
        assertEquals("01-why-kotlin-feels-weird", outline.chapters[0].slug)
        assertEquals("02-collections-lambdas", outline.chapters[1].slug)
        assertEquals(40, outline.chapters[0].estMinutes)
    }

    @Test
    fun `style preset system prompt is forwarded to the client`() = runTest {
        val client = RecordingGeminiClient(validJson)
        OutlineGenerator(client).generate(
            topic = "Rust ownership",
            dailyMinutes = 30,
            priorKnowledge = "",
            style = StylePresets.PRACTICAL,
            apiKey = "sk-fake",
        )

        assertEquals(StylePresets.PRACTICAL.systemPrompt, client.lastSystem)
        assertTrue(client.lastUserPrompt!!.contains("Rust ownership"))
        assertNotNull(client.lastSchema)
    }

    @Test
    fun `model parameter is forwarded, defaults to constant`() = runTest {
        val client = RecordingGeminiClient(validJson)
        OutlineGenerator(client).generate(
            topic = "X", dailyMinutes = 20, priorKnowledge = "", apiKey = "k",
        )
        assertEquals(DEFAULT_GEMINI_MODEL, client.lastModel)

        val client2 = RecordingGeminiClient(validJson)
        OutlineGenerator(client2, model = "gemini-custom").generate(
            topic = "X", dailyMinutes = 20, priorKnowledge = "", apiKey = "k",
        )
        assertEquals("gemini-custom", client2.lastModel)
    }

    @Test
    fun `malformed JSON from Gemini surfaces OutlineParseException with raw payload`() = runTest {
        val garbage = "sure! here is your outline: {chapters: bad}"
        val client = RecordingGeminiClient(garbage)
        val ex = assertThrows(OutlineParseException::class.java) {
            kotlinx.coroutines.runBlocking {
                OutlineGenerator(client).generate(
                    topic = "X", dailyMinutes = 20, priorKnowledge = "", apiKey = "k",
                )
            }
        }
        assertEquals(garbage, ex.rawResponse)
    }

    @Test
    fun `blank inputs are rejected before any network call`() = runTest {
        val client = RecordingGeminiClient("")
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                OutlineGenerator(client).generate("", 30, "", apiKey = "k")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                OutlineGenerator(client).generate("X", 0, "", apiKey = "k")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                OutlineGenerator(client).generate("X", 30, "", apiKey = "")
            }
        }
    }
}
