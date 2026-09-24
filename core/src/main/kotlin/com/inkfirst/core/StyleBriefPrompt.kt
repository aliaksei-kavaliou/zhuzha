package com.inkfirst.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What a one-line wish becomes: the two things a custom style has to supply
 * that a built-in preset supplies as constants.
 *
 * [prompt] is the system instruction for every text call this book makes;
 * [mascot] is the one sentence the badge illustrator needs, because the
 * mascot arms in BadgePrompt are keyed on the built-in styles and a custom
 * one has no arm of its own.
 */
@Serializable
data class StyleBrief(
    val prompt: String = "",
    val mascot: String = "",
)

/**
 * The brief for writing a brief.
 *
 * The reader types a wish — "as a fairy tale", "like one of those friendly
 * no-jargon guides" — and this turns it into an instruction shaped like the
 * built-in presets. The shape matters more than it looks: the result goes
 * into the SYSTEM channel of every outline, chapter, memory and Q&A call, so
 * it outranks the user turn wherever the two disagree. Most of the rules
 * below exist to stop it disagreeing at all.
 */
internal fun buildStyleBriefPrompt(request: String): String = buildString {
    appendLine("Someone is having a study book written for them, and has described the")
    appendLine("teaching style they want it in. Turn their description into a system")
    appendLine("instruction for the model that will write the book.")
    appendLine()
    appendLine("WHAT THEY ASKED FOR")
    appendLine(request.trim().take(500))
    appendLine()
    appendLine("THE INSTRUCTION YOU WRITE")
    appendLine("- Address the writer directly, in the second person, as \"You are writing a chapter of…\".")
    appendLine("- Three to six sentences. Concrete: say how a section is built, how ideas are introduced, what the voice does.")
    appendLine("- Say what to DO. An instruction that is mostly prohibitions produces careful, lifeless prose.")
    appendLine("- It must work for EVERY chapter of the book, so say nothing about the subject matter. You have not been told what the book is about, and that is deliberate.")
    appendLine()
    appendLine("LEAVE THESE ALONE — they are set elsewhere in the same request, and an")
    appendLine("instruction here would outrank and fight them:")
    appendLine("- Which language the book is written in.")
    appendLine("- How long a chapter is, or how many sections it has.")
    appendLine("- Which Markdown or block syntax to use.")
    appendLine("- Whether the book has pictures.")
    appendLine()
    appendLine("IF THEY NAMED A BOOK, SERIES OR PUBLISHER, describe the teaching approach")
    appendLine("that makes it work and nothing else. Never repeat the name, never imitate")
    appendLine("its branding, trade dress, cover look, catchphrases or recurring named")
    appendLine("features. \"Cheerful, informal, assumes no prior knowledge, jokes in the")
    appendLine("margins\" is the useful half; the title is the half we cannot use.")
    appendLine()
    appendLine("ALSO RETURN A MASCOT LINE: one sentence, under 25 words, describing the")
    appendLine("kind of character or emblem that should mark this book's exercise and recap")
    appendLine("blocks. Describe a KIND, not a specific creature — the illustrator invents")
    appendLine("the creature from the book's subject, and this only sets the register.")
    appendLine("A playful style wants a cartoon with a face; a sober one wants a plain")
    appendLine("emblem with no face at all.")
    appendLine()
    appendLine("The instruction below is an example of the SHAPE and length wanted. Its")
    appendLine("content has nothing to do with what was asked for — do not borrow from it:")
    appendLine("\"${StylePresets.TEXTBOOK.systemPrompt.replace("\n", " ").replace(Regex("\\s+"), " ").trim()}\"")
}

/** System instruction for the brief call itself. */
internal const val STYLE_BRIEF_SYSTEM: String =
    "You write system instructions for other language models. You reply with JSON and nothing else."

private fun typed(t: String): JsonObject = buildJsonObject { put("type", t) }

internal val STYLE_BRIEF_SCHEMA: JsonObject = buildJsonObject {
    put("type", "OBJECT")
    put("required", JsonArray(listOf(JsonPrimitive("prompt"), JsonPrimitive("mascot"))))
    put("properties", buildJsonObject {
        put("prompt", typed("STRING"))
        put("mascot", typed("STRING"))
    })
}
