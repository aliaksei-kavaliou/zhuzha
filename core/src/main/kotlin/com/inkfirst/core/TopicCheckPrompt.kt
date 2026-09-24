package com.inkfirst.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What the check decided. See [TopicChecker] for where it runs and why. */
enum class TopicVerdict { ALLOW, ASK, REFUSE }

/**
 * A verdict and one sentence for the reader.
 *
 * [reason] is written in English, not the book's language: it is app chrome,
 * shown on the New course form, which is English like the rest of the app's
 * own furniture (see [BOOK_CHROME_STRINGS] for what does get translated).
 */
data class TopicCheckResult(val verdict: TopicVerdict, val reason: String)

/** What the check is given. Everything the reader typed, before anything is spent. */
data class TopicCheckInputs(
    val topic: String,
    val priorKnowledge: String = "",
    val expectations: String = "",
    val styleRequest: String = "",
)

@Serializable
internal data class TopicCheckReply(val verdict: String = "", val reason: String = "")

/**
 * The screening prompt.
 *
 * Most of what is written below exists to stop the check being WRONG in the
 * direction that costs something. A missed harmful request is caught further
 * down the line — the model that writes the chapters refuses it on its own,
 * whether or not this check exists. A wrongly refused one is not caught by
 * anything: it is a book the reader cannot write, in an app they own, with
 * no appeal and nobody to ask. So the list of subjects that are explicitly
 * fine is longer than the list that is not, and "prefer allow when unsure"
 * is said outright.
 *
 * The line is drawn at OPERATIONAL INSTRUCTIONS, never at subjects. A public
 * library carries the chemistry of explosives; it does not carry a page of
 * quantities and a wiring diagram. That distinction is one a model applies
 * reliably when it is stated that way, and unreliably when it is asked to
 * judge whether a topic "could be misused" — which is true of everything.
 */
internal fun buildTopicCheckPrompt(inputs: TopicCheckInputs): String = buildString {
    appendLine("You are screening a request before an AI writes a study book from it.")
    appendLine("Decide whether the book should be written.")
    appendLine()
    appendLine("WHAT IS BEING ASKED FOR")
    appendLine("Topic: ${inputs.topic.trim().take(1000)}")
    if (inputs.priorKnowledge.isNotBlank()) {
        appendLine("Who is reading, and what they know: ${inputs.priorKnowledge.trim().take(1000)}")
    }
    if (inputs.expectations.isNotBlank()) {
        appendLine("What they want to be able to do afterwards: ${inputs.expectations.trim().take(1000)}")
    }
    if (inputs.styleRequest.isNotBlank()) {
        appendLine("The style they asked for: ${inputs.styleRequest.trim().take(500)}")
    }
    appendLine()
    appendLine("THE TEST")
    appendLine("Would a well-stocked public library carry a book on this?")
    appendLine()
    appendLine("Libraries carry the chemistry of explosives, toxicology and poisons, pharmacology,")
    appendLine("forensic medicine, military history, the history of terrorism and of genocide,")
    appendLine("firearms and ammunition, hunting and butchery, lock-picking, self-defence and")
    appendLine("martial arts, offensive computer security and how real attacks work, drugs and")
    appendLine("harm reduction, extremism as a subject of study, sexual health, and death.")
    appendLine("Every one of those is a legitimate book. Refusing one is a worse failure than")
    appendLine("this check not existing at all.")
    appendLine()
    appendLine("What a library does not carry is a working set of instructions for producing a")
    appendLine("weapon or for harming a person.")
    appendLine()
    appendLine("VERDICTS")
    appendLine("- allow: everything else. This is almost every request, including every subject")
    appendLine("  named above. Prefer it whenever you are unsure.")
    appendLine("- ask: the subject is legitimate, but the wording points at DOING the thing")
    appendLine("  rather than understanding it, and the reader could settle it by rephrasing.")
    appendLine("  They are shown your sentence and may proceed anyway.")
    appendLine("- refuse: the request is, in substance, for operational instructions that would")
    appendLine("  let someone cause serious harm — synthesising a chemical or biological weapon,")
    appendLine("  or a poison meant for a person; building an explosive or incendiary device;")
    appendLine("  manufacturing an illegal or untraceable firearm; harming, stalking or")
    appendLine("  defaming a specific named person; sexual content involving children.")
    appendLine()
    appendLine("A stated harmless purpose does not change a refuse. \"For a school project\",")
    appendLine("\"for a novel I am writing\", \"purely theoretical\" are the usual wrappers, and")
    appendLine("the book would contain the same instructions either way.")
    appendLine()
    appendLine("WHAT THIS CHECK IS NOT")
    appendLine("- Not an age rating. A book written for a six-year-old about death, or a frank")
    appendLine("  one about the body, is the reader's business and not yours.")
    appendLine("- Not a taste filter. Dark, political, religious, sexual, upsetting and")
    appendLine("  unfashionable subjects are all allowed.")
    appendLine("- Not a quality check. A vague, silly, boring or unteachable request is allowed.")
    appendLine("- Not a legality check. Say nothing about what is legal where the reader lives.")
    appendLine()
    appendLine("YOUR SENTENCE")
    appendLine("One sentence, addressed to the reader, plain, with no moralising and no lecture.")
    appendLine("On allow it is never shown — write \"ok\". On ask or refuse, say what the problem")
    appendLine("is concretely enough to act on: their next move is either to rephrase it or to")
    appendLine("decide you are wrong.")
}

/** System instruction for the check itself. */
internal const val TOPIC_CHECK_SYSTEM: String =
    "You screen requests for a personal study-book app. You reply with JSON and nothing else."

private fun typed(t: String): JsonObject = buildJsonObject { put("type", t) }

internal val TOPIC_CHECK_SCHEMA: JsonObject = buildJsonObject {
    put("type", "OBJECT")
    put("required", JsonArray(listOf(JsonPrimitive("verdict"), JsonPrimitive("reason"))))
    put("properties", buildJsonObject {
        put("verdict", buildJsonObject {
            put("type", "STRING")
            put("enum", JsonArray(listOf("allow", "ask", "refuse").map(::JsonPrimitive)))
        })
        put("reason", typed("STRING"))
    })
}
