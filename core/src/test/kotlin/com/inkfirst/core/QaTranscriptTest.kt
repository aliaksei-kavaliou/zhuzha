package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QaTranscriptTest {

    /** Exactly the shape CourseStore.appendQa writes. */
    private fun entry(date: String, quote: String?, q: String, a: String) = buildString {
        appendLine("## $date")
        appendLine()
        if (quote != null) {
            quote.lines().forEach { appendLine("> $it") }
            appendLine()
        }
        appendLine("**Q:** $q")
        appendLine()
        appendLine("**A:** $a")
        appendLine()
        appendLine("---")
        appendLine()
    }

    private fun ann(quote: String, created: String = "2026-09-18", kind: String = "ask") =
        Annotation(
            chapterSlug = "ch1",
            blockIndex = 0,
            quote = quote,
            note = "",
            created = created,
            kind = kind,
        )

    @Test
    fun `parses a single exchange with its passage`() {
        val md = entry("2026-09-18", "a gradient is the slope", "what is that?", "It is the slope.")
        val out = QaTranscript.parse(md)
        assertEquals(1, out.size)
        assertEquals("a gradient is the slope", out[0].passage)
        assertEquals("what is that?", out[0].question)
        assertEquals("It is the slope.", out[0].answer)
        assertEquals("2026-09-18", out[0].created)
    }

    @Test
    fun `a question with no passage parses with a null passage`() {
        val out = QaTranscript.parse(entry("2026-09-18", null, "general question", "an answer"))
        assertEquals(1, out.size)
        assertNull(out[0].passage)
    }

    @Test
    fun `a multi-line answer keeps its paragraph breaks`() {
        val md = """
            ## 2026-09-18

            **Q:** explain

            **A:** first paragraph

            second paragraph

            ---
        """.trimIndent()
        val out = QaTranscript.parse(md)
        assertEquals(1, out.size)
        assertTrue(out[0].answer.contains("first paragraph"))
        assertTrue(out[0].answer.contains("second paragraph"))
        assertTrue(out[0].answer.contains("\n\n"), "paragraph break should survive: ${out[0].answer}")
    }

    @Test
    fun `a passage does not leak into the next entry`() {
        // The bug this guards: a quote buffered from one entry being attached
        // to the question of the one after it.
        val md = entry("2026-09-17", "first passage", "q1", "a1") +
            entry("2026-09-18", null, "q2", "a2")
        val out = QaTranscript.parse(md)
        assertEquals(2, out.size)
        assertEquals("first passage", out[0].passage)
        assertNull(out[1].passage)
    }

    @Test
    fun `survives a hand-edited file with the separator deleted`() {
        // The file is meant to be editable by hand, so losing a `---` must not
        // blank someone's history.
        val md = """
            ## 2026-09-17

            **Q:** q1

            **A:** a1

            ## 2026-09-18

            **Q:** q2

            **A:** a2
        """.trimIndent()
        val out = QaTranscript.parse(md)
        assertEquals(2, out.size)
        assertEquals("q1", out[0].question)
        assertEquals("q2", out[1].question)
    }

    @Test
    fun `empty input is an empty feed, not a crash`() {
        assertEquals(emptyList<QaTranscript.Entry.Asked>(), QaTranscript.parse(""))
        assertEquals(emptyList<QaTranscript.Entry>(), QaTranscript.feed("", emptyList(), "ch1"))
    }

    // --- feed assembly ---------------------------------------------------

    @Test
    fun `a marked passage with no question becomes its own entry`() {
        val feed = QaTranscript.feed(
            qaMarkdown = "",
            annotations = listOf(ann("backprop applies the chain rule")),
            chapterSlug = "ch1",
        )
        assertEquals(1, feed.size)
        val m = feed[0] as QaTranscript.Entry.Marked
        assertEquals("backprop applies the chain rule", m.passage)
    }

    @Test
    fun `a passage that was asked about is not also shown as a bare mark`() {
        // Otherwise every answered question would print its passage twice.
        val feed = QaTranscript.feed(
            qaMarkdown = entry("2026-09-18", "a gradient is the slope", "what?", "the slope"),
            annotations = listOf(ann("a gradient is the slope")),
            chapterSlug = "ch1",
        )
        assertEquals(1, feed.size)
        assertTrue(feed[0] is QaTranscript.Entry.Asked)
    }

    @Test
    fun `annotations from other chapters are ignored`() {
        val other = ann("some other chapter passage").copy(chapterSlug = "ch2")
        val feed = QaTranscript.feed("", listOf(other), "ch1")
        assertEquals(emptyList<QaTranscript.Entry>(), feed)
    }

    @Test
    fun `notes are not marks`() {
        // kind "text" is a written note, which the Notes section owns.
        val feed = QaTranscript.feed("", listOf(ann("a note", kind = "text")), "ch1")
        assertEquals(emptyList<QaTranscript.Entry>(), feed)
    }

    @Test
    fun `the feed is chronological and the pending passage lands last`() {
        val feed = QaTranscript.feed(
            qaMarkdown = entry("2026-09-16", "old passage", "q", "a"),
            annotations = listOf(ann("marked yesterday", created = "2026-09-17")),
            chapterSlug = "ch1",
            pendingSelection = "just marked now",
            today = "2026-09-18",
        )
        assertEquals(3, feed.size)
        assertEquals("2026-09-16", feed[0].created)
        assertEquals("marked yesterday", (feed[1] as QaTranscript.Entry.Marked).passage)
        assertEquals("just marked now", (feed[2] as QaTranscript.Entry.Marked).passage)
    }

    @Test
    fun `the pending passage is not duplicated when already annotated`() {
        val feed = QaTranscript.feed(
            qaMarkdown = "",
            annotations = listOf(ann("the same passage")),
            chapterSlug = "ch1",
            pendingSelection = "the same passage",
            today = "2026-09-18",
        )
        assertEquals(1, feed.size)
    }
}
