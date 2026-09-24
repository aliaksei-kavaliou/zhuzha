package com.inkfirst.core

/**
 * Teaching-style system prompts. See docs/design.md §3.
 * The [key] is what gets stored in course.json; the [systemPrompt] is what
 * the generator injects for outline/chapter calls.
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

    /** What a course.json says when the style was described rather than chosen. */
    const val CUSTOM_KEY: String = "custom"

    /**
     * The picker row for "describe it yourself". It carries no prompt of its
     * own — a custom style has none until one is written for it, and the
     * written one lives in the course, not here. See [StyleBriefGenerator].
     *
     * This is deliberately NOT in [ALL], so [byKey] can never return it: a
     * preset with an empty systemPrompt reaching a generation call would
     * silently strip the system instruction from every chapter of the book,
     * and nothing downstream would look wrong enough to notice.
     */
    val CUSTOM = StylePreset(
        key = CUSTOM_KEY,
        displayName = "Custom (describe it yourself)",
        systemPrompt = "",
    )

    /** The built-in presets — the ones that carry their own prompt. */
    val ALL: List<StylePreset> = listOf(HEADFIRST_INSPIRED, TEXTBOOK, PRACTICAL)

    /** What the course-creation picker offers. [ALL] plus the custom row. */
    val CHOICES: List<StylePreset> = ALL + CUSTOM

    val DEFAULT: StylePreset = HEADFIRST_INSPIRED

    fun byKey(key: String): StylePreset? = ALL.firstOrNull { it.key == key }

    /**
     * The style a given book is written in — the one call every generator
     * should make.
     *
     * Falls back to [DEFAULT] three different ways, and all three are the
     * same decision: a book must stay writable. An unknown key (a hand-edited
     * file, a preset we retired), or a custom style whose brief went missing,
     * gets the house style rather than an exception or an empty system
     * instruction.
     */
    fun forCourse(course: Course): StylePreset {
        byKey(course.style)?.let { return it }
        if (course.style == CUSTOM_KEY && course.stylePrompt.isNotBlank()) {
            return CUSTOM.copy(systemPrompt = course.stylePrompt.trim())
        }
        return DEFAULT
    }
}
