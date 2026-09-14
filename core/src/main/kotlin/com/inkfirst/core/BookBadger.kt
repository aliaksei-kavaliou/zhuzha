package com.inkfirst.core

import kotlinx.coroutines.CancellationException

/**
 * Draws a course's *badge set* — one small mascot doodle per attention-block
 * kind ([BADGE_KINDS]), shown beside every exercise and recap in the book.
 *
 * The point of this class is that the badges belong to the **course**, not to
 * a chapter. They are drawn once, stored under `badges/`, and every chapter
 * reuses them, so a twenty-chapter course pays for two pictures in total
 * rather than two per chapter.
 *
 * Consistency is the whole trick: two independent image calls give two
 * unrelated doodles, which looks like a mistake rather than a house style. So
 * the first badge *invents* the mascot and is asked to describe it in words
 * as well; every later badge is drawn with that description **and** the first
 * picture attached as a reference. Same properties as [ChapterIllustrator]:
 *
 * - **Idempotent.** Kinds already on disk are skipped, so re-running only
 *   fills what is missing and a retry after a flaky network is free.
 * - **Best-effort.** A badge that fails to draw is simply absent; the block
 *   renders exactly as it did before badges existed.
 */
class BookBadger(
    private val client: GeminiClient,
    private val model: String = DEFAULT_GEMINI_IMAGE_MODEL,
) {

    /**
     * Draws every badge in [kinds] that [existing] says is missing. [kinds]
     * comes from [badgeKindsIn] for a real chapter, so the set follows what
     * the book actually contains.
     *
     * [loadBadge] hands back a badge already on disk so a partly-drawn set
     * still has a reference to copy — without it, filling in one missing
     * badge months later would invent a second, unrelated mascot.
     * [saveImage] receives each finished picture. :core never touches the
     * filesystem itself; the caller supplies both sides.
     */
    suspend fun drawMissing(
        course: Course,
        apiKey: String,
        kinds: List<String> = BADGE_KINDS,
        existing: Set<String> = emptySet(),
        knownMascot: String = "",
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        loadBadge: (kind: String) -> GeneratedImage? = { null },
        saveImage: (kind: String, extension: String, bytes: ByteArray) -> Unit,
    ): BadgeResult {
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }

        val missing = kinds.filter { it !in existing }
        if (missing.isEmpty()) return BadgeResult(generated = 0, failed = 0, mascot = knownMascot)

        // Whatever already exists is the reference for the rest of the set.
        var reference = kinds.firstNotNullOfOrNull { kind ->
            if (kind in existing) loadBadge(kind) else null
        }
        var mascot = knownMascot
        var generated = 0
        var failed = 0
        onProgress(0, missing.size)

        for ((index, kind) in missing.withIndex()) {
            try {
                val image = client.generateImage(
                    model = model,
                    prompt = buildBadgePrompt(
                        course = course,
                        kind = kind,
                        mascot = mascot,
                        hasReference = reference != null,
                    ),
                    aspectRatio = BADGE_ASPECT_RATIO,
                    apiKey = apiKey,
                    referenceImage = reference,
                )
                saveImage(kind, image.extension, image.bytes)
                generated++
                // The first picture of a run becomes the reference for the
                // rest of it, so a set drawn in one pass never drifts.
                if (reference == null) reference = image
                if (mascot.isBlank()) mascot = image.text?.let(::firstSentence).orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                System.err.println(
                    "badge $kind for ${course.slug} failed: ${e::class.simpleName}: ${e.message}",
                )
            }
            onProgress(index + 1, missing.size)
        }

        return BadgeResult(generated = generated, failed = failed, mascot = mascot)
    }
}

/**
 * [mascot] is the one-line description of the character, to be persisted so a
 * later redraw of a single badge stays on-model. Empty if the model never
 * sent one and none was known.
 */
data class BadgeResult(
    val generated: Int,
    val failed: Int,
    val mascot: String,
)

/**
 * Image models like to wrap an answer in chat ("Here you go! A friendly..."),
 * so the description is trimmed to its first real sentence and capped. A
 * rambling paragraph in the prompt of every later badge would compete with
 * the reference image rather than support it.
 */
private fun firstSentence(raw: String): String {
    val cleaned = raw.lines()
        .map { it.trim().removePrefix("-").trim() }
        .firstOrNull { it.length > 20 }
        ?: raw.trim()
    val end = cleaned.indexOf(". ")
    val sentence = if (end > 20) cleaned.take(end + 1) else cleaned
    return sentence.take(300).trim()
}
