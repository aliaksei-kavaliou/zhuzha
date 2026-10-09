package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The file that exists because Play requires a way to report AI output.
 *
 * Two properties carry everything: a report must be SELF-CONTAINED, because
 * the chapter it complains about can be rewritten out from under it, and the
 * file must stay appendable and hand-editable like every other plain file
 * this app writes.
 */
class ContentReportTest {

    private fun report(
        reason: ReportReason = ReportReason.OFFENSIVE,
        what: ReportedContent = ReportedContent.PAGE,
        note: String = "",
        passage: String = "The mug is not a circle.",
    ) = ContentReport(
        reason = reason,
        what = what,
        note = note,
        courseSlug = "pen-and-paper-confidence",
        courseTitle = "Pen and Paper Confidence",
        chapterN = 2,
        chapterTitle = "Looking Really Closely at Your Coffee Mug",
        blockIndex = 24,
        passage = passage,
        created = "2026-10-09",
    )

    @Test
    fun `heading carries the facts you would grep for`() {
        val md = report().toMarkdown()
        val heading = md.lineSequence().first()
        assertTrue(heading.contains("2026-10-09"), heading)
        assertTrue(heading.contains("offensive"), heading)
        assertTrue(heading.contains("page"), heading)
        assertTrue(heading.contains("pen-and-paper-confidence"), heading)
        assertTrue(heading.contains("ch2"), heading)
    }

    @Test
    fun `the passage is quoted so the report outlives a rewrite`() {
        val md = report(passage = "The mug is not a circle.").toMarkdown()
        assertTrue(md.contains("> The mug is not a circle."), md)
    }

    /**
     * A blank line inside the passage used to end the block quote, so the
     * remainder rendered as the reader's own note — putting words in their
     * mouth in the one file where that matters.
     */
    @Test
    fun `every line of a multi-paragraph passage stays quoted`() {
        val md = report(passage = "First paragraph.\n\nSecond paragraph.").toMarkdown()
        assertTrue(md.contains("> First paragraph."), md)
        assertTrue(md.contains("> Second paragraph."), md)
        assertFalse(
            md.lines().any { it.trim() == "Second paragraph." },
            "the second paragraph escaped the quote:\n$md",
        )
    }

    @Test
    fun `a long passage is truncated rather than pasting a chapter`() {
        val md = report(passage = "x".repeat(2000)).toMarkdown()
        assertTrue(md.contains("…"), "expected an ellipsis")
        assertTrue(md.length < 1200, "report ran to ${md.length} chars")
    }

    /** A reason on its own is a complete report; the note is optional. */
    @Test
    fun `no note means no empty note section`() {
        val md = report(note = "").toMarkdown()
        assertFalse(md.contains("Reader's note"), md)
    }

    @Test
    fun `a note is included when given`() {
        val md = report(note = "This is rude about beginners.").toMarkdown()
        assertTrue(md.contains("**Reader's note:**"), md)
        assertTrue(md.contains("This is rude about beginners."), md)
    }

    @Test
    fun `the three AI outputs are distinguishable`() {
        assertTrue(report(what = ReportedContent.ANSWER).toMarkdown().contains("· answer ·"))
        assertTrue(report(what = ReportedContent.PICTURE).toMarkdown().contains("· picture ·"))
        assertTrue(report(what = ReportedContent.PAGE).toMarkdown().contains("· page ·"))
    }

    @Test
    fun `reports accumulate newest last and survive their course`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        assertFalse(store.hasReports())
        assertEquals("", store.readReports())

        store.appendReport(report(reason = ReportReason.OFFENSIVE))
        store.appendReport(report(reason = ReportReason.WRONG, note = "400 - 150 is 250"))

        assertTrue(store.hasReports())
        val all = store.readReports()
        val first = all.indexOf("offensive")
        val second = all.indexOf("wrong")
        assertTrue(first in 0 until second, "expected newest last:\n$all")
        assertTrue(all.contains("400 - 150 is 250"))

        // At the ROOT, not inside the book — deleting a book you reported
        // must not delete the complaint, and the backup's shelf archive
        // sweeps the root, so this is backed up without new code.
        assertTrue(File(tmp, "reports.md").isFile, "reports.md should sit at the courses root")
    }

    @Test
    fun `two reports do not run into each other`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.appendReport(report())
        store.appendReport(report())
        val headings = store.readReports().lines().count { it.startsWith("## ") }
        assertEquals(2, headings, store.readReports())
    }

    @Test
    fun `the email body explains itself without the app`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.appendReport(report())
        val body = reportsAsEmailBody(store.readReports())
        assertTrue(body.startsWith("InkFirst — content reports"), body)
        assertTrue(body.contains("nothing was sent automatically"), body)
        assertTrue(body.contains("Looking Really Closely"), body)
    }
}
