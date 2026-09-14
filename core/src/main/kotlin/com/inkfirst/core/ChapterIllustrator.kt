package com.inkfirst.core

import kotlinx.coroutines.CancellationException

/**
 * Fills a chapter's ```figure slots with generated sketches. The companion
 * to [ChapterGenerator]: that one compiles the words, this one draws the
 * pictures. See docs/design.md §6 (phase 3).
 *
 * Two properties worth relying on:
 *
 * - **Idempotent.** Slots that already carry a `src=` are skipped, so
 *   re-running only fills what is missing. That makes it safe to retry after
 *   a flaky network, and it is how an already-written course gets pictures
 *   without regenerating a single word of its prose.
 * - **Best-effort.** One figure failing never fails the chapter. A slot with
 *   no picture keeps rendering its alt text, exactly as before this existed.
 */
class ChapterIllustrator(
    private val client: GeminiClient,
    private val model: String = DEFAULT_GEMINI_IMAGE_MODEL,
) {

    /**
     * Draws the chapter's opening picture — the one on its title page,
     * standing for the *problem* the chapter is about to solve.
     *
     * Two calls, not one: [textModel] writes the scene brief first, because
     * handing an image model a title and a list of objectives produces a
     * literal illustration of the words rather than a scene. Best-effort like
     * everything else here — no cover just means no cover.
     *
     * Returns the file name written, or null if nothing was drawn.
     */
    suspend fun drawCover(
        course: Course,
        chapter: PersistedChapter,
        apiKey: String,
        textModel: String,
        saveImage: (fileName: String, bytes: ByteArray) -> Unit,
    ): String? {
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
        return try {
            val brief = client.generateText(
                model = textModel,
                systemInstruction = "You write briefs for illustrators. You reply with the brief and nothing else.",
                userPrompt = buildCoverBriefPrompt(course, chapter),
                apiKey = apiKey,
            ).trim().ifBlank { chapter.title }

            val image = client.generateImage(
                model = model,
                prompt = buildCoverPrompt(course, chapter, brief),
                aspectRatio = FIGURE_ASPECT_RATIO,
                apiKey = apiKey,
            )
            val fileName = chapterCoverName(chapter.slug, image.extension)
            saveImage(fileName, image.bytes)
            fileName
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            System.err.println(
                "cover for ${chapter.slug} failed: ${e::class.simpleName}: ${e.message}",
            )
            null
        }
    }

    /**
     * Generates a picture for every pending figure slot in [markdown].
     *
     * [saveImage] receives each finished picture (file name + bytes) as it
     * arrives, so a long chapter writes pictures to disk progressively rather
     * than holding them all in memory. :core stays Android-free by never
     * touching the filesystem itself — the caller supplies the sink.
     *
     * Returns the rewritten Markdown, which the caller must persist for the
     * pictures to be found again on the next read.
     */
    suspend fun illustrate(
        course: Course,
        chapter: PersistedChapter,
        markdown: String,
        apiKey: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        saveImage: (fileName: String, bytes: ByteArray) -> Unit,
    ): IllustrateResult {
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }

        val pending = findFigureSlots(markdown).filter { it.isPending }
        if (pending.isEmpty()) return IllustrateResult(markdown, generated = 0, failed = 0)

        val srcByOrdinal = mutableMapOf<Int, String>()
        var failed = 0
        onProgress(0, pending.size)

        for ((index, slot) in pending.withIndex()) {
            try {
                val image = client.generateImage(
                    model = model,
                    prompt = buildFigurePrompt(
                        course = course,
                        chapterTitle = chapter.title,
                        brief = slot.brief,
                        context = contextAround(markdown, slot),
                    ),
                    aspectRatio = FIGURE_ASPECT_RATIO,
                    apiKey = apiKey,
                )
                val fileName = "${chapter.slug}-${slot.ordinal}.${image.extension}"
                saveImage(fileName, image.bytes)
                srcByOrdinal[slot.ordinal] = fileName
            } catch (e: CancellationException) {
                // Navigating away mid-generation. Keep whatever finished.
                throw e
            } catch (e: Exception) {
                failed++
                System.err.println(
                    "figure ${slot.ordinal} of ${chapter.slug} failed: " +
                        "${e::class.simpleName}: ${e.message}",
                )
            }
            onProgress(index + 1, pending.size)
        }

        return IllustrateResult(
            markdown = withFigureSrc(markdown, srcByOrdinal),
            generated = srcByOrdinal.size,
            failed = failed,
        )
    }
}

/**
 * [markdown] is the chapter text with `src=` params filled in — unchanged
 * from the input if nothing was generated.
 */
data class IllustrateResult(
    val markdown: String,
    val generated: Int,
    val failed: Int,
)
