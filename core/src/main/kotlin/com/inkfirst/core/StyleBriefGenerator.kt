package com.inkfirst.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Writes the system instruction for a custom teaching style, once.
 *
 * The three built-in presets are constants; a custom style is a sentence the
 * reader typed, and a sentence is not an instruction. This expands it —
 * before the outline, so the outline's own chapter titles come out in the
 * style too — and the result is stored in course.json for the life of the
 * book. See [StyleBrief] for what comes back and docs/design.md §3.
 *
 * Unlike [LabelGenerator], which swallows everything and falls back to
 * English, this one throws. A dropped label costs you one word in the wrong
 * language; a dropped style brief silently writes twelve chapters in the
 * house style when you asked for a fairy tale, and nothing on the page says
 * so until you have paid for all of them.
 */
class StyleBriefGenerator(
    private val client: GeminiClient,
    private val model: String = DEFAULT_GEMINI_MODEL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param request what the reader typed, verbatim.
     * @param apiKey Gemini API key (BYO — see CLAUDE.md).
     */
    suspend fun expand(request: String, apiKey: String): StyleBrief {
        require(request.isNotBlank()) { "request must not be blank" }
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }

        val raw = client.generateJson(
            model = model,
            systemInstruction = STYLE_BRIEF_SYSTEM,
            userPrompt = buildStyleBriefPrompt(request),
            responseSchema = STYLE_BRIEF_SCHEMA,
            apiKey = apiKey,
        )

        val parsed = try {
            json.decodeFromString(StyleBrief.serializer(), raw)
        } catch (e: SerializationException) {
            throw StyleBriefException("Gemini returned malformed style JSON: ${e.message}", raw, e)
        }

        // The schema marks both fields required, which Gemini honours by
        // emitting the KEY — not by putting anything in it. An empty prompt
        // that reached the course would strip the system instruction from
        // every call the book ever makes, so it is caught here, where the
        // reader is still standing in front of the form.
        val prompt = parsed.prompt.trim()
        if (prompt.isBlank()) {
            throw StyleBriefException("Gemini returned an empty style instruction.", raw)
        }
        return StyleBrief(prompt = prompt, mascot = parsed.mascot.trim())
    }
}

class StyleBriefException(
    message: String,
    val rawResponse: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
