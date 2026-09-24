package com.inkfirst.core

/**
 * User-turn prompt for a selection-anchored Q&A. Feeds the model the whole
 * chapter Markdown (so it can quote or reference other passages), the
 * highlighted passage (if any), and the learner's question.
 */
internal fun buildQaUserPrompt(
    course: Course,
    chapter: PersistedChapter,
    chapterMarkdown: String,
    selection: String?,
    question: String,
): String = buildString {
    appendLine("Course topic: ${course.topicPrompt}")
    if (course.expectations.isNotBlank()) {
        appendLine("Learner goal: ${course.expectations}")
    }
    if (course.priorKnowledge.isNotBlank()) {
        appendLine("Learner prior knowledge: ${course.priorKnowledge}")
    }
    appendLine()
    appendLine("Chapter ${chapter.n}: ${chapter.title}")
    appendLine("Objectives: ${chapter.objectives.joinToString("; ")}")
    appendLine()
    appendLine("CHAPTER MARKDOWN")
    appendLine(chapterMarkdown)
    appendLine()
    if (!selection.isNullOrBlank()) {
        appendLine("PASSAGE THE LEARNER HIGHLIGHTED")
        appendLine(selection.trim())
        appendLine()
    }
    appendLine("QUESTION")
    appendLine(question.trim())
    appendLine()
    appendLine(measurementRule(course.units))
    appendLine("Answer the question in ${course.language}. Keep it grounded in this chapter (use the highlighted passage as focal point if given). Be concise — a few short paragraphs at most, no preamble.")
}

/**
 * System instruction for Q&A. Layered on top of the course's style preset so
 * the voice stays consistent with the chapter itself.
 */
internal fun buildQaSystemInstruction(course: Course): String = buildString {
    val style = StylePresets.forCourse(course)
    appendLine(style.systemPrompt)
    appendLine()
    appendLine("You are answering questions from a learner reading this course. Match the chapter's voice. Prefer concrete examples over abstract restatement. If the learner's question can't be answered from the chapter, say so briefly and suggest what to reread.")
}
