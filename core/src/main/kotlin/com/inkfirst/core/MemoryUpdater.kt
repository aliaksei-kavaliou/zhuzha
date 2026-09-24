package com.inkfirst.core

/**
 * After a chapter is written, this summarizes it into ~200 words for
 * memory.md. Kept separate from [ChapterGenerator] so a failure here
 * doesn't cost the chapter itself — the caller can save the chapter
 * and treat the memory update as best-effort.
 */
class MemoryUpdater(
    private val client: GeminiClient,
    private val model: String = DEFAULT_GEMINI_MODEL,
) {
    /**
     * Produces a summary of the freshly-generated chapter. Return value is
     * pure prose; the caller appends it to memory.md with the delimiter of
     * its choosing (see CourseStore.appendMemory).
     */
    suspend fun summarize(
        course: Course,
        n: Int,
        chapterMarkdown: String,
        apiKey: String,
    ): String {
        val chapter = course.chapters.firstOrNull { it.n == n }
            ?: throw IllegalArgumentException("Course has no chapter $n")
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
        require(chapterMarkdown.isNotBlank()) { "chapterMarkdown must not be blank" }

        val style = StylePresets.forCourse(course)
        val prompt = buildMemoryUpdatePrompt(chapter, chapterMarkdown, course.expectations)

        return client.generateText(
            model = model,
            systemInstruction = style.systemPrompt,
            userPrompt = prompt,
            apiKey = apiKey,
        )
    }
}
