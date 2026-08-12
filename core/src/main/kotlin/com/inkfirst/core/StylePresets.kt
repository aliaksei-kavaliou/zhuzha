package com.inkfirst.core

/**
 * Teaching-style system prompts. See docs/design.md §3.
 * The [key] is what gets stored in course.json; the [systemPrompt] is what
 * the generator injects for outline/chapter calls.
 *
 * Prompts here are stubs — expand them once we're actually calling Gemini.
 */
data class StylePreset(
    val key: String,
    val displayName: String,
    val systemPrompt: String,
)

object StylePresets {
    val HEADFIRST_INSPIRED = StylePreset(
        key = "headfirst-inspired",
        displayName = "Head First-inspired (conversational)",
        systemPrompt = """
            You are writing a chapter of a learning book in a conversational,
            redundant-on-purpose style. Introduce concepts concretely before
            abstractly. Repeat the important idea two or three times in
            different framings. Interleave short exercises every few pages.
        """.trimIndent(),
    )

    val TEXTBOOK = StylePreset(
        key = "textbook",
        displayName = "Textbook (formal)",
        systemPrompt = """
            You are writing a chapter of a formal textbook. Structure each
            section as: definition, example, practice problem. Prefer
            precision over informality.
        """.trimIndent(),
    )

    val PRACTICAL = StylePreset(
        key = "practical",
        displayName = "Practical (hands-on)",
        systemPrompt = """
            You are writing a chapter of a practical, project-oriented guide.
            Minimize theory. Lead with runnable code or a hands-on task.
            Explain concepts as they come up in the work.
        """.trimIndent(),
    )

    val ALL: List<StylePreset> = listOf(HEADFIRST_INSPIRED, TEXTBOOK, PRACTICAL)

    val DEFAULT: StylePreset = HEADFIRST_INSPIRED

    fun byKey(key: String): StylePreset? = ALL.firstOrNull { it.key == key }
}
