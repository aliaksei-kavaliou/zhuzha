package com.inkfirst.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Translates the book's furniture once per language.
 *
 * One call, not one per book: the result is written to
 * `<courses-root>/labels/<language>.json` and every later book in that
 * language reads it. It is a text call against the cheap model, so the cost
 * is a fraction of a cent — but the reason to cache is agreement rather than
 * money. Two Russian books that disagreed about the word for "Contents"
 * would be worse than two English ones.
 *
 * Everything it returns is checked before it is kept, because a label is not
 * prose: it goes on a row of a small screen, and a translation that lost a
 * `{n}` or ran to forty characters breaks the layout rather than reading
 * badly. Anything that fails a check is simply dropped, and a dropped label
 * falls back to English — which is why none of this throws.
 */
class LabelGenerator(
    private val client: GeminiClient,
    private val model: String = DEFAULT_GEMINI_MODEL,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun translate(
        language: String,
        apiKey: String,
        strings: List<String> = BOOK_CHROME_STRINGS,
    ): Map<String, String> {
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
        if (!needsTranslation(language) || strings.isEmpty()) return emptyMap()

        val raw = client.generateJson(
            model = model,
            systemInstruction = "You translate user-interface text. You reply with JSON and nothing else.",
            userPrompt = buildLabelPrompt(language, strings),
            responseSchema = LABELS_SCHEMA,
            apiKey = apiKey,
        )

        val parsed = try {
            json.decodeFromString(LabelReply.serializer(), raw)
        } catch (e: SerializationException) {
            System.err.println("label translation for $language was malformed: ${e.message}")
            return emptyMap()
        }
        return keepUsable(parsed.labels, strings)
    }
}

/**
 * Which of the model's answers are fit to put on a screen.
 *
 * Pure, and separated from the call so every rule below is a test rather than
 * something found on a device in another language.
 */
internal fun keepUsable(
    candidates: List<LabelPair>,
    requested: List<String>,
): Map<String, String> {
    val wanted = requested.toSet()
    val out = LinkedHashMap<String, String>()
    candidates.forEach { (source, target) ->
        val clean = target.trim()
        when {
            // Not one of ours: the model invented a row, or hallucinated a
            // source. Translating something the app never says is harmless
            // but storing it is clutter.
            source !in wanted -> Unit
            clean.isEmpty() -> Unit
            // Left in English. Not an error — some words travel — but no
            // reason to store a mapping that changes nothing.
            clean == source -> Unit
            // A label is furniture, and furniture has to fit. The limit is
            // generous because some languages genuinely are longer, and it
            // exists to catch the model answering with a sentence.
            clean.length > source.length * LENGTH_FACTOR + LENGTH_SLACK -> Unit
            // A translation that dropped {n} would render "Page  of" — the
            // only failure here that produces nonsense rather than English.
            placeholdersIn(source) != placeholdersIn(clean) -> Unit
            else -> out[source] = clean
        }
    }
    return out
}

private fun placeholdersIn(text: String): Set<String> =
    PLACEHOLDER.findAll(text).map { it.value }.toSet()

// Both braces escaped. The JVM accepts a bare "}" in a pattern and Android's
// ICU engine does not — it throws PatternSyntaxException at class init, which
// arrives as an ExceptionInInitializerError and therefore sails straight
// through a `catch (e: Exception)`. Unit tests on the JVM were green while
// the app died on the emulator the first time a book asked to be translated.
private val PLACEHOLDER = Regex("\\{[a-z]+\\}")
private const val LENGTH_FACTOR = 2.0
private const val LENGTH_SLACK = 12

@kotlinx.serialization.Serializable
internal data class LabelReply(val labels: List<LabelPair> = emptyList())

@kotlinx.serialization.Serializable
internal data class LabelPair(val source: String = "", val target: String = "")

private fun typed(t: String): JsonObject = buildJsonObject { put("type", t) }

internal val LABELS_SCHEMA: JsonObject = buildJsonObject {
    put("type", "OBJECT")
    put("required", JsonArray(listOf(JsonPrimitive("labels"))))
    put("properties", buildJsonObject {
        put("labels", buildJsonObject {
            put("type", "ARRAY")
            put("items", buildJsonObject {
                put("type", "OBJECT")
                put("required", JsonArray(listOf(JsonPrimitive("source"), JsonPrimitive("target"))))
                put("properties", buildJsonObject {
                    put("source", typed("STRING"))
                    put("target", typed("STRING"))
                })
            })
        })
    })
}
