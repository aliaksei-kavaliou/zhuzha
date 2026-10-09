package com.inkfirst.core

/**
 * Finding a passage again in a book you have already read.
 *
 * "Where did it explain gradient descent?" is the question a book answers and
 * a chat window cannot, and the material is already sitting on disk as plain
 * Markdown — so this is mostly a matter of searching the same BLOCKS the
 * reader paginates, rather than raw file offsets. A block index is the app's
 * one unit of position: it is what bookmarks, annotations and ink all anchor
 * to, so a hit can be opened at exactly the right page without knowing
 * anything about the current font size or margins.
 *
 * Matching is a case-insensitive substring of the whole query. Not word
 * stemming, not fuzzy, not all-terms-anywhere: a reader typing "gradient
 * descent" means those two words in that order, and a search that quietly
 * broadens the question is one you cannot predict or narrow.
 */
object CourseSearch {

    /** What kind of thing a hit was found in — the reader's book, or their own marks. */
    enum class Kind { CHAPTER, NOTE, QUESTION }

    data class Hit(
        val kind: Kind,
        val chapterN: Int,
        val chapterSlug: String,
        val chapterTitle: String,
        /** Where to open — the same index a bookmark would store. */
        val blockIndex: Int,
        /** A window of text around the match, for the results page. */
        val snippet: String,
    )

    /** How much context to show on each side of a match. */
    private const val CONTEXT = 48

    /**
     * Searches one chapter's Markdown. [chapterMarkdown] is the file as
     * written; block indices in the results line up with [parseMarkdown],
     * which is what the reader paginates.
     */
    fun inChapter(
        chapterMarkdown: String,
        query: String,
        chapterN: Int,
        chapterSlug: String,
        chapterTitle: String,
    ): List<Hit> {
        val needle = query.trim()
        if (needle.isEmpty() || chapterMarkdown.isBlank()) return emptyList()
        return parseMarkdown(chapterMarkdown).mapIndexedNotNull { index, block ->
            val text = block.plainText()
            if (!text.contains(needle, ignoreCase = true)) return@mapIndexedNotNull null
            Hit(
                kind = Kind.CHAPTER,
                chapterN = chapterN,
                chapterSlug = chapterSlug,
                chapterTitle = chapterTitle,
                blockIndex = index,
                snippet = snippet(text, needle),
            )
        }
    }

    /**
     * Searches the reader's own marks — the notes they typed and the passages
     * they asked about.
     *
     * A question's stored text is the PASSAGE, not the question: the words
     * asked live in the chapter's Q&A transcript. Searching what the reader
     * pointed at is the useful half anyway — "I asked about this bit" is how
     * you remember it.
     */
    fun inMarks(course: Course, query: String): List<Hit> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val chapters = course.chapters.associateBy { it.slug }
        return course.annotations.mapNotNull { a ->
            val haystack = (a.quote + " " + a.note).trim()
            if (!haystack.contains(needle, ignoreCase = true)) return@mapNotNull null
            val chapter = chapters[a.chapterSlug]
            Hit(
                kind = if (a.kind == "ask") Kind.QUESTION else Kind.NOTE,
                chapterN = chapter?.n ?: 0,
                chapterSlug = a.chapterSlug,
                chapterTitle = chapter?.title ?: a.chapterSlug,
                blockIndex = a.blockIndex,
                snippet = snippet(
                    if (a.note.isBlank()) a.quote else "${a.quote} — ${a.note}",
                    needle,
                ),
            )
        }
    }

    /**
     * Everything in one book, the reader's own marks first.
     *
     * Marks lead because there are few of them and they are the reader's own
     * words: if you wrote a note about the thing you are looking for, that
     * note is a better answer than the paragraph that prompted it.
     * [readChapter] returns a chapter's Markdown, or null if it has not been
     * generated yet — unwritten chapters are simply not searched.
     */
    fun inCourse(
        course: Course,
        query: String,
        readChapter: (chapterSlug: String) -> String?,
    ): List<Hit> {
        if (query.isBlank()) return emptyList()
        val marks = inMarks(course, query)
        val text = course.chapters.flatMap { chapter ->
            val markdown = readChapter(chapter.slug) ?: return@flatMap emptyList()
            inChapter(markdown, query, chapter.n, chapter.slug, chapter.title)
        }
        return marks + text
    }

    /** A window of [CONTEXT] characters either side of the first match. */
    private fun snippet(text: String, needle: String): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        val at = flat.indexOf(needle, ignoreCase = true)
        if (at < 0) return flat.take(CONTEXT * 2)
        val from = (at - CONTEXT).coerceAtLeast(0)
        val to = (at + needle.length + CONTEXT).coerceAtMost(flat.length)
        return buildString {
            if (from > 0) append("…")
            append(flat.substring(from, to))
            if (to < flat.length) append("…")
        }
    }

}
