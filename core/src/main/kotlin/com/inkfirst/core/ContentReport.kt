package com.inkfirst.core

/**
 * A reader's report about a page of a generated book.
 *
 * This exists because Google Play requires it. An app that generates content
 * with AI must carry an in-app way to report or flag offensive output, usable
 * WITHOUT leaving the app — see docs/play/ai-content-policy.md. InkFirst had
 * no report affordance at all, which is a release blocker rather than a gap.
 *
 * The awkward part is that there is no backend to report TO, and there is not
 * going to be one. The requirement is about the reporting SURFACE being
 * in-app, not about where the report eventually lands, so a report is written
 * to a plain file next to the books and the reader is then OFFERED a way to
 * send it. Refusing to send still leaves a complete record on disk.
 *
 * It also has to be self-contained. "Chapter 3, page 7 was offensive" is
 * useless the moment that chapter is rewritten — the page will not exist and
 * the text will be different — so a report QUOTES the passage it is about.
 */
data class ContentReport(
    val reason: ReportReason,
    /** Which of the three AI outputs this is about — see [ReportedContent]. */
    val what: ReportedContent,
    /** The reader's own words. May be blank: a reason alone is a valid report. */
    val note: String,
    val courseSlug: String,
    val courseTitle: String,
    val chapterN: Int,
    val chapterTitle: String,
    /**
     * Block index rather than page number, for the reason the whole app uses
     * block indices: a page number means nothing once the font size changes.
     */
    val blockIndex: Int,
    /** The text complained about, quoted so the report outlives the chapter. */
    val passage: String,
    /** Supplied by the caller, as [CourseStore.addBookmark] already does. */
    val created: String,
)

/**
 * Why a page is being reported.
 *
 * Four, deliberately. A free-text-only form gets "bad" and nothing else; a
 * taxonomy of fifteen gets abandoned halfway. These four are the distinctions
 * that would actually change what gets done about it: two are Play's concern
 * (offensive, harmful), one is the failure mode a STUDY book has that a chat
 * does not (confidently wrong, which is worse in a book because a book is
 * trusted), and one is the escape hatch.
 *
 * The names are stored in the file as stable English keys, not as the
 * reader's language: the file is for whoever receives the report.
 */
enum class ReportReason(val key: String) {
    OFFENSIVE("offensive"),
    HARMFUL("harmful"),
    WRONG("wrong"),
    OTHER("other"),
}

/**
 * Which AI output is being complained about.
 *
 * Recorded because it decides what would actually be FIXED, and the three
 * come from three different places: a bad page is the chapter prompt, a bad
 * answer is [QaGenerator], a bad picture is the illustrator and its house
 * style. A report that does not say which sends whoever reads it to the
 * wrong file.
 *
 * It is also why reporting is not only on the Ask panel, which was the first
 * instinct: a fresh answer is the likeliest thing to be offensive and the
 * easiest to flag, but the chapters are the bulk of what this app generates,
 * and a reader meeting something bad mid-page needs somewhere to go.
 */
enum class ReportedContent(val key: String) {
    PAGE("page"),
    ANSWER("answer"),
    PICTURE("picture"),
}

/**
 * Where reports live, relative to the courses root.
 *
 * Not inside a course: a report must outlive the book it is about, since
 * deleting a book you complained about should not delete the complaint, and
 * one file is what you can actually send.
 */
internal const val REPORTS_FILE = "reports.md"

/** How much of the page to quote. Enough to identify it, not a whole chapter. */
private const val PASSAGE_LIMIT = 600

/**
 * Renders one report as a Markdown section, in the same appendable shape as
 * `memory.md` and `reader.md` — greppable, readable in ten years, and
 * editable by hand if the reader wants to withdraw something before sending.
 */
fun ContentReport.toMarkdown(): String = buildString {
    appendLine("## $created · ${reason.key} · ${what.key} · $courseSlug ch$chapterN")
    appendLine()
    appendLine("- **Book:** $courseTitle (`$courseSlug`)")
    appendLine("- **Chapter:** $chapterN — $chapterTitle")
    appendLine("- **Block:** $blockIndex")
    appendLine()
    val quoted = passage.trim().let {
        if (it.length > PASSAGE_LIMIT) it.take(PASSAGE_LIMIT).trimEnd() + "…" else it
    }
    if (quoted.isNotEmpty()) {
        // Quote every line, or a passage containing a blank line breaks out
        // of the block quote and the rest reads as the reader's own note.
        quoted.lines().forEach { appendLine("> ${it.trim()}") }
        appendLine()
    }
    if (note.isNotBlank()) {
        appendLine("**Reader's note:**")
        appendLine()
        appendLine(note.trim())
        appendLine()
    }
}

/**
 * The whole file, for sending. Plain text because it travels in an email
 * body: a reader forwarding this should not need anything to render it.
 */
fun reportsAsEmailBody(markdown: String): String = buildString {
    appendLine("InkFirst — content reports")
    appendLine()
    appendLine(
        "These are reports written in the app about AI-generated pages. " +
            "They were saved on the device; nothing was sent automatically.",
    )
    appendLine()
    append(markdown.trim())
}
