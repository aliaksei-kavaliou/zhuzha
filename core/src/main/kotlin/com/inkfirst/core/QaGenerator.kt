package com.inkfirst.core

/**
 * Ask-a-question call for the selection-anchored chat. Kept separate from
 * [ChapterGenerator] and [MemoryUpdater] because it's a different pedagogical
 * intent (answering the learner, not writing content), even though they all
 * share the same GeminiClient.generateText plumbing.
 */
class QaGenerator(
    private val client: GeminiClient,
    private val model: String = DEFAULT_GEMINI_MODEL,
) {
    suspend fun answer(
        course: Course,
        n: Int,
        chapterMarkdown: String,
        selection: String?,
        question: String,
        apiKey: String,
    ): String {
        val chapter = course.chapters.firstOrNull { it.n == n }
            ?: throw IllegalArgumentException("Course has no chapter $n")
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
        require(question.isNotBlank()) { "question must not be blank" }
        require(chapterMarkdown.isNotBlank()) { "chapterMarkdown must not be blank" }

        return client.generateText(
            model = model,
            systemInstruction = buildQaSystemInstruction(course),
            userPrompt = buildQaUserPrompt(course, chapter, chapterMarkdown, selection, question),
            apiKey = apiKey,
        )
    }
}
