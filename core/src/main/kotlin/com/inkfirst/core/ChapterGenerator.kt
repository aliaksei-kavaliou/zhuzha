package com.inkfirst.core

/**
 * Compiles a single chapter of a course. See docs/design.md §3 for the
 * compile-a-book strategy — this is the per-chapter half.
 */
class ChapterGenerator(
    private val client: GeminiClient,
    private val model: String = DEFAULT_GEMINI_MODEL,
) {
    /**
     * Generates chapter [n] of [course]. [memory] is the current contents of
     * `memory.md` (may be blank for the first chapter). Returns the raw
     * Markdown body — the caller writes it to disk and updates status.
     *
     * @param course the full persisted Course (drives style + outline + context).
     * @param n 1-indexed chapter number.
     * @param memory current memory.md contents; blank on the first chapter.
     * @param apiKey Gemini API key (BYO — see CLAUDE.md).
     */
    suspend fun generate(
        course: Course,
        n: Int,
        memory: String,
        apiKey: String,
    ): String {
        val chapter = course.chapters.firstOrNull { it.n == n }
            ?: throw IllegalArgumentException("Course has no chapter $n")
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }

        val style = StylePresets.byKey(course.style) ?: StylePresets.DEFAULT
        val prompt = buildChapterPrompt(course, chapter, memory)

        return client.generateText(
            model = model,
            systemInstruction = style.systemPrompt,
            userPrompt = prompt,
            apiKey = apiKey,
        )
    }
}
