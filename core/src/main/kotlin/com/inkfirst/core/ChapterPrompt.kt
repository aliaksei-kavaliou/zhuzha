package com.inkfirst.core

/**
 * Builds the user-turn prompt for a single chapter generation.
 * The style preset's systemPrompt goes through the system instruction channel.
 * The running memory.md is what makes cross-chapter continuity work — see
 * docs/design.md §3.
 */
internal fun buildChapterPrompt(
    course: Course,
    chapter: PersistedChapter,
    memory: String,
): String = buildString {
    appendLine("Write chapter ${chapter.n} of this course.")
    appendLine()
    appendLine("COURSE CONTEXT")
    appendLine("Topic: ${course.topicPrompt}")
    appendLine("Prior knowledge: ${course.priorKnowledge.ifBlank { "(none stated)" }}")
    appendLine("Learner goal: ${course.expectations.ifBlank { "(none stated)" }}")
    appendLine("Session length: ${course.dailyMinutes} minutes/day")
    appendLine()
    appendLine("FULL OUTLINE")
    course.chapters.forEach { c ->
        appendLine("${c.n}. ${c.title} — ${c.objectives.joinToString("; ")}")
    }
    appendLine()
    appendLine("WHAT WAS TAUGHT SO FAR")
    appendLine(memory.ifBlank { "(nothing yet — this is the first chapter)" })
    appendLine()
    appendLine("CURRENT CHAPTER")
    appendLine("Chapter ${chapter.n}: ${chapter.title}")
    appendLine("Estimated length: ${chapter.estMinutes} minutes of slow reading")
    appendLine("Objectives:")
    chapter.objectives.forEach { appendLine("- $it") }
    appendLine()
    appendLine("FORMAT RULES")
    appendLine("- Return Markdown. Do NOT wrap the whole output in a code fence.")
    appendLine("- Aim for content that fills ~${chapter.estMinutes} minutes of slow, e-ink reading. Redundancy is welcome if it's pedagogical.")
    appendLine("- Use custom fenced blocks where they fit:")
    appendLine("  - ```exercise … ``` for practice; put the answer inside on a line starting with \"answer: \".")
    appendLine("  - ```recap … ``` for end-of-section wrap-ups that restate the key idea differently.")
    appendLine("  - ```figure alt=\"…\" ``` for figure slots — alt text only (images come later).")
    appendLine("- Where a metaphor or term from an earlier chapter helps, reuse it explicitly.")
    appendLine("- Do NOT restate the outline or objectives — just write the chapter.")
}

/**
 * Builds the user-turn prompt for the post-chapter memory summary. The output
 * gets appended to memory.md and is what future chapters see as WHAT WAS TAUGHT.
 */
internal fun buildMemoryUpdatePrompt(
    chapter: PersistedChapter,
    chapterMarkdown: String,
    learnerGoal: String,
): String = buildString {
    appendLine("Summarize chapter ${chapter.n} for the running course memory.")
    appendLine()
    appendLine("Chapter title: ${chapter.title}")
    appendLine("Learner goal: ${learnerGoal.ifBlank { "(none stated)" }}")
    appendLine()
    appendLine("CHAPTER MARKDOWN")
    appendLine(chapterMarkdown)
    appendLine()
    appendLine("Write ~200 words, prose, no headings, capturing:")
    appendLine("- Concepts introduced (name them).")
    appendLine("- Metaphors or analogies used — be specific (\"gradient descent as walking down a hill\", not \"a physical metaphor\").")
    appendLine("- Terminology choices (if we picked one word over another, note it).")
    appendLine("- Exercises given (topic only, not the full text).")
    appendLine()
    appendLine("Future chapters will read this. Do not include a heading — just the paragraph(s).")
}
