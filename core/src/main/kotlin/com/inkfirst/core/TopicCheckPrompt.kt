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

/**
 * What the check is given.
 *
 * [outline] is the second pass. The request is screened before anything is
 * spent, and then the OUTLINE is screened before it is shown — because the
 * outline is the model's own reading of the request, written out in chapter
 * titles and objectives, and it is far more concrete than the sentence that
 * produced it. "How to rubber the bank" is a typo a screener can argue with;
 * "Chapter 3: Choosing Your Moment and Your Exit" is not.
 *
 * Same prompt and same criterion for both passes — only the evidence differs.
 */
data class TopicCheckInputs(
    val topic: String,
    val priorKnowledge: String = "",
    val expectations: String = "",
    val styleRequest: String = "",
    val outline: String = "",
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
 *
 * THE REFUSE CRITERION IS A PRINCIPLE FOLLOWED BY EXAMPLES, and it has to
 * stay that way. It was first written as a bare list — chemical weapon,
 * explosive, illegal firearm, a specific named person, CSAM — and the model
 * read the list as the definition. "How to kill people quickly" then matched
 * none of the five (no named victim, no named method) and came back as `ask`,
 * which is a walk-past. A request can be more obviously harmful and less
 * specific at the same time, and an enumeration inverts exactly that case.
 * So the principle leads, vagueness is named as a non-excuse, the list says
 * "among others", and a calibration table pins both ends.
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
    if (inputs.outline.isNotBlank()) {
        appendLine()
        appendLine("WHAT THE BOOK WOULD ACTUALLY CONTAIN — this outline has already been written")
        appendLine("from that request, and is the strongest evidence you have. Judge it over the")
        appendLine("wording of the request wherever the two point different ways: a harmless-looking")
        appendLine("sentence that produced a manual is a manual.")
        appendLine(inputs.outline.trim().take(4000))
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
    appendLine()
    appendLine("- refuse: a book satisfying this request would be, in substance, a MANUAL FOR")
    appendLine("  DOING something seriously harmful or seriously criminal — a set of")
    appendLine("  instructions whose purpose is to let the reader carry it out. The question")
    appendLine("  to ask is what the finished book would have to CONTAIN. Two families:")
    appendLine("    (a) causing serious physical harm to people — killing, injuring,")
    appendLine("        incapacitating or poisoning someone, or making a thing whose purpose")
    appendLine("        is to do that;")
    appendLine("    (b) committing a serious crime against others — robbery, burglary, theft,")
    appendLine("        fraud, trafficking and the like — where the book would be the how-to.")
    appendLine("  UNDERSTANDING either family is allowed, and is most of what libraries hold.")
    appendLine("  The history of bank robbery, the criminology of fraud, how a con works so")
    appendLine("  you can spot one, lock-picking as a craft, penetration testing: all allow.")
    appendLine("  What refuses is a request whose plain reading is \"teach me to do this\".")
    appendLine()
    appendLine("  THIS DOES NOT DEPEND ON HOW SPECIFIC THE REQUEST IS. A request that names no")
    appendLine("  method, no victim and no purpose, and still asks to be taught how to harm")
    appendLine("  people, is the plainest refusal there is — \"how to kill people quickly\" is a")
    appendLine("  refusal exactly as much as any named method or named victim. Vagueness makes")
    appendLine("  a request no less a request, and it is never a reason to soften the verdict.")
    appendLine()
    appendLine("  It covers, AMONG OTHERS: killing or injuring a person by any means;")
    appendLine("  synthesising a chemical or biological weapon, or a poison meant for a person;")
    appendLine("  building an explosive or incendiary device; manufacturing an illegal or")
    appendLine("  untraceable firearm; harming, stalking or defaming a specific named person;")
    appendLine("  sexual content involving children. That list is examples, not a definition —")
    appendLine("  a request matching none of them still refuses if the book would be a manual")
    appendLine("  for hurting people.")
    appendLine()
    appendLine("- ask: the SUBJECT is legitimate and only the wording is unclear — it reads as")
    appendLine("  either understanding the thing or doing it, and a rephrase would settle it.")
    appendLine("  The reader is shown your sentence and may proceed anyway, so never use ask to")
    appendLine("  soften something whose plain reading is a refusal.")
    appendLine()
    appendLine("- allow: everything else, including every subject named above. Prefer it")
    appendLine("  whenever you are unsure whether a SUBJECT is legitimate. That preference does")
    appendLine("  not apply to the paragraph about manuals for harming people: there, the plain")
    appendLine("  reading of the words decides.")
    appendLine()
    appendLine("A stated harmless purpose does not change a refuse. \"For a school project\",")
    appendLine("\"for a novel I am writing\", \"purely theoretical\" are the usual wrappers, and")
    appendLine("the book would contain the same instructions either way.")
    appendLine()
    appendLine("CALIBRATION — these are settled, and your answer should match:")
    appendLine("  the chemistry of explosives ................................. allow")
    appendLine("  the history of poisoning in Renaissance Italy ............... allow")
    appendLine("  how SQL injection works and how to defend against it ........ allow")
    appendLine("  how to get into a car when the keys are locked inside ....... allow")
    appendLine("  field dressing a deer ....................................... allow")
    appendLine("  what happens to a body after death .......................... allow")
    appendLine("  the history of bank robbery and how police solved them ...... allow")
    appendLine("  how confidence tricks work so I can spot one ................ allow")
    appendLine("  how to build a pipe bomb from hardware store parts .......... refuse")
    appendLine("  how to kill people quickly .................................. refuse")
    appendLine("  the most effective way to stab someone ...................... refuse")
    appendLine("  which household poison is hardest to detect ................. refuse")
    appendLine("  how to rob a bank ........................................... refuse")
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
