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
 * Everything about the model's outline that is OURS to decide rather than
 * its: the slugs, because they double as filenames, and the numbering, which
 * the app prints itself.
 *
 * Asking in the prompt is not enough on its own — the same reasoning as the
 * figure ceiling, which is enforced in `illustrate` rather than requested.
 * The prompt now says it too, but an outline is written once and then names
 * the folder, the reader's header, the Contents and the EPUB for the life of
 * the book, so a title that slips through is permanent.
 */
internal fun CourseOutline.withDerivedSlugs(): CourseOutline = copy(
    chapters = chapters.map {
        val title = it.title.withoutChapterNumber()
        it.copy(title = title, slug = chapterSlug(it.n, title))
    },
)

/**
 * Strips a leading chapter number from a title the model wrote.
 *
 * Found on a live outline (2026-09-29): every chapter came back as "Chapter 1:
 * Letting Go of the Fear…", so the Contents read "1. Chapter 1: Letting Go…".
 * The rule existed in the CHAPTER prompt — where it stops the h1 repeating the
 * number — and had never been said to the outline model, so whether a book got
 * prefixed titles was luck. Earlier books happened to come back clean, which
 * is why nobody saw it.
 *
 * Deliberately narrow: only a leading number-ish prefix goes, and only when
 * something is left afterwards. A title that is genuinely "1984" or
 * "Chapter and Verse" must survive intact, and a strip that empties the title
 * is worse than the prefix it removed.
 */
internal fun String.withoutChapterNumber(): String {
    // "Chapter 3:", "Chapter 3 -", "Часть 2." — the word, a number, a
    // separator. The word is optional; a bare "3. " counts too.
    val prefix = Regex("""^\s*(\p{L}{3,12}\s+)?\d{1,3}\s*[:.)\u2014-]\s*""")
    val stripped = prefix.replace(trim(), "").trim()
    return stripped.ifBlank { trim() }
}

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
