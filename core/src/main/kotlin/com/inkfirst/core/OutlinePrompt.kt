package com.inkfirst.core

internal data class OutlineRequestInputs(
    val topic: String,
    val dailyMinutes: Int,
    val priorKnowledge: String,
    val expectations: String,
    val style: StylePreset,
)

/**
 * Builds the user-turn prompt for the outline call. The style preset's
 * systemPrompt is sent separately as the system instruction (see GeminiClient).
 */
internal fun buildOutlinePrompt(inputs: OutlineRequestInputs): String = buildString {
    appendLine("Design a course outline for the following learner request.")
    appendLine()
    appendLine("Topic: ${inputs.topic}")
    appendLine("Daily study time: ${inputs.dailyMinutes} minutes")
    appendLine("Prior knowledge: ${inputs.priorKnowledge.ifBlank { "(none stated)" }}")
    appendLine("What the learner wants to achieve: ${inputs.expectations.ifBlank { "(none stated)" }}")
    appendLine()
    appendLine("Produce a JSON object matching the provided schema. Rules:")
    appendLine("- 6 to 12 chapters, numbered sequentially starting at 1.")
    appendLine("- Each chapter's estMinutes should be roughly a small multiple of the daily study time.")
    appendLine("- Objectives are concrete outcomes the learner will be able to do, 2-5 per chapter.")
    appendLine("- The slug field is a placeholder; leave it empty, it will be derived deterministically.")
    appendLine("- Titles should reflect the chosen teaching style (see system instruction).")
}
