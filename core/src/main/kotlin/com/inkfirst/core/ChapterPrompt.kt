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
    appendLine("- Write the entire chapter in ${course.language}. All headings, prose, exercise text, recap text, and figure alt-text must be in ${course.language}. Technical terms may stay in English if that's how the field uses them.")
    appendLine("- Aim for content that fills ~${chapter.estMinutes} minutes of slow, e-ink reading. Redundancy is welcome if it's pedagogical.")
    appendLine("- Use custom fenced blocks where they fit:")
    appendLine("  - ```exercise … ``` for practice; put the answer inside on a line starting with \"answer: \".")
    appendLine("  - ```recap … ``` for end-of-section wrap-ups that restate the key idea differently.")
    appendLine("  - ```figure alt=\"…\" ``` for figure slots. The alt text is the drawing brief: an artist reads it and nothing else, so describe a concrete SCENE, not a diagram. \"A very tired robot trying to sort a mountain of socks\" works; \"a diagram of the classification process\" does not. One vivid subject. Words in the picture are allowed but must be few and large — a short speech bubble (a character reacting is very welcome) or a single big label. Never ask for small print, a paragraph, many labels, or numbers presenting data; if your idea only works with dense text, pick a different idea. and it should raise a smile as well as make the point. Two or three figures per chapter is plenty.")
    appendLine("- Where a metaphor or term from an earlier chapter helps, reuse it explicitly.")
    appendLine("- Do NOT restate the outline or objectives — just write the chapter.")
    appendLine("- Punctuation: put a space on each side of every em-dash. Write \"word — word\", not \"word—word\" or \"word---word\". This reads more comfortably on narrow e-ink pages.")
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
