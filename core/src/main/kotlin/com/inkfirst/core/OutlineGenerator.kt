package com.inkfirst.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The public entry point for outline generation. `:app` will hold one of these,
 * call [generate], and persist the result into course.json.
 */
class OutlineGenerator(
    private val client: GeminiClient,
    private val model: String = DEFAULT_GEMINI_MODEL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param topic user's original ask, verbatim.
     * @param dailyMinutes minutes/day the learner has.
     * @param priorKnowledge the learner's self-description; may be blank.
     * @param expectations what the learner wants to be able to do afterwards; may be blank.
     * @param style teaching style; defaults to head-first-inspired.
     * @param apiKey Gemini API key (BYO — see CLAUDE.md).
     */
    suspend fun generate(
        topic: String,
        dailyMinutes: Int,
        priorKnowledge: String,
        expectations: String = "",
        style: StylePreset = StylePresets.DEFAULT,
        language: String = "English",
        units: String = DEFAULT_MEASUREMENT_SYSTEM,
        apiKey: String,
    ): CourseOutline {
        require(topic.isNotBlank()) { "topic must not be blank" }
        require(dailyMinutes > 0) { "dailyMinutes must be positive" }
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }

        val prompt = buildOutlinePrompt(
            OutlineRequestInputs(
                topic, dailyMinutes, priorKnowledge, expectations, style, language, units,
            ),
        )

        val raw = client.generateJson(
            model = model,
            systemInstruction = style.systemPrompt,
            userPrompt = prompt,
            responseSchema = OUTLINE_SCHEMA,
            apiKey = apiKey,
        )

        val parsed = try {
            json.decodeFromString(CourseOutline.serializer(), raw)
        } catch (e: SerializationException) {
            throw OutlineParseException("Gemini returned malformed outline JSON: ${e.message}", raw, e)
        }

        return parsed.withDerivedSlugs()
    }
}

class OutlineParseException(
    message: String,
    val rawResponse: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * We always overwrite whatever slug the model returned; slugs are ours to own
 * because they double as filenames.
 */
internal fun CourseOutline.withDerivedSlugs(): CourseOutline = copy(
    chapters = chapters.map { it.copy(slug = chapterSlug(it.n, it.title)) },
)

// Gemini's responseSchema is an OpenAPI 3.0 subset, not full JSON Schema —
// UPPERCASE "OBJECT"/"STRING" etc. is required.
private fun typed(t: String): JsonObject = buildJsonObject { put("type", t) }
private fun requiredList(vararg names: String): JsonArray =
    JsonArray(names.map { JsonPrimitive(it) })

internal val OUTLINE_SCHEMA: JsonObject = buildJsonObject {
    put("type", "OBJECT")
    put("required", requiredList("title", "chapters"))
    put("properties", buildJsonObject {
        put("title", typed("STRING"))
        put("chapters", buildJsonObject {
            put("type", "ARRAY")
            put("items", buildJsonObject {
                put("type", "OBJECT")
                put("required", requiredList("n", "slug", "title", "objectives", "estMinutes"))
                put("properties", buildJsonObject {
                    put("n", typed("INTEGER"))
                    put("slug", typed("STRING"))
                    put("title", typed("STRING"))
                    put("objectives", buildJsonObject {
                        put("type", "ARRAY")
                        put("items", typed("STRING"))
                    })
                    put("estMinutes", typed("INTEGER"))
                })
            })
        })
    })
}
