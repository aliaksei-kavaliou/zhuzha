package com.inkfirst.app.ui

import com.inkfirst.core.Annotation
import com.inkfirst.core.Course
import com.inkfirst.core.PersistedChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Contents used to list every annotation under one "Notes" header, which
 * meant a question showed the write-glyph and an empty body — asks carry no
 * note text, their words live in the chapter's Q&A file. These pin down the
 * split that replaced it.
 */
class TocBlocksTest {

    private fun course(annotations: List<Annotation>) = Course(
        slug = "c",
        title = "A Book",
        topicPrompt = "t",
        priorKnowledge = "",
        dailyMinutes = 20,
        style = "headfirst-inspired",
        model = "gemini",
        created = "2026-09-20",
        chapters = listOf(
            PersistedChapter(
                n = 1, slug = "ch1", title = "One",
                objectives = emptyList(), estMinutes = 20,
            ),
            PersistedChapter(
                n = 2, slug = "ch2", title = "Two",
                objectives = emptyList(), estMinutes = 20,
            ),
        ),
        annotations = annotations,
    )

    private fun ann(
        chapter: String = "ch1",
        quote: String = "q",
        note: String = "",
        kind: String = "text",
        at: String = "",
        created: String = "2026-09-20",
    ) = Annotation(
        chapterSlug = chapter,
        blockIndex = 0,
        quote = quote,
        note = note,
        created = created,
        at = at,
        kind = kind,
    )

    private fun build(
        annotations: List<Annotation>,
        questions: Map<String, Map<String, String>> = emptyMap(),
    ) = buildTocBlocks(course(annotations), null, 0, questions)

    private fun headers(blocks: List<TocBlock>) =
        blocks.filterIsInstance<TocBlock.SectionHeader>().map { it.text }

    @Test
    fun `a question is never listed as a note`() {
        val blocks = build(listOf(ann(kind = "ask", quote = "gradient")))
        assertTrue(blocks.none { it is TocBlock.NoteEntry })
        assertEquals(listOf("Contents", "Questions"), headers(blocks))
    }

    @Test
    fun `notes come before questions, because they are the scarce ones`() {
        val blocks = build(
            listOf(
                ann(kind = "ask", quote = "asked"),
                ann(kind = "text", quote = "wrote", note = "my note"),
            ),
        )
        assertEquals(listOf("Contents", "Notes", "Questions"), headers(blocks))
        val noteAt = blocks.indexOfFirst { it is TocBlock.NoteEntry }
        val questionAt = blocks.indexOfFirst { it is TocBlock.QuestionEntry }
        assertTrue(noteAt < questionAt)
    }

    @Test
    fun `a section with nothing in it gets no header`() {
        // A book read without a single question should not carry an empty
        // "Questions" heading — the Contents is paginated, so a header that
        // announces nothing costs real page space.
        assertEquals(listOf("Contents", "Notes"), headers(build(listOf(ann(note = "n")))))
        assertEquals(listOf("Contents"), headers(build(emptyList())))
    }

    @Test
    fun `a question carries the text that was asked, matched by passage`() {
        val blocks = build(
            listOf(ann(kind = "ask", quote = "  gradient descent  ")),
            mapOf("ch1" to mapOf("gradient descent" to "What is a gradient?")),
        )
        val q = blocks.filterIsInstance<TocBlock.QuestionEntry>().single()
        // The annotation's quote is stored untrimmed; the transcript's is not.
        assertEquals("What is a gradient?", q.question)
    }

    @Test
    fun `a passage marked with no question asked has no question text`() {
        val blocks = build(listOf(ann(kind = "ask", quote = "gradient")))
        assertNull(blocks.filterIsInstance<TocBlock.QuestionEntry>().single().question)
    }

    @Test
    fun `marks are ordered by chapter, then by when they were made`() {
        val blocks = build(
            listOf(
                ann(chapter = "ch2", quote = "second-chapter", at = "2026-09-20T09:00:00"),
                ann(chapter = "ch1", quote = "later", at = "2026-09-20T21:00:00"),
                ann(chapter = "ch1", quote = "earlier", at = "2026-09-20T08:00:00"),
            ).map { it.copy(note = "n") },
        )
        assertEquals(
            listOf("earlier", "later", "second-chapter"),
            blocks.filterIsInstance<TocBlock.NoteEntry>().map { it.annotation.quote },
        )
    }

    @Test
    fun `entries made before the timestamp existed keep their persisted order`() {
        // `created` is a date, so everything from one evening ties. The sort
        // is stable, which is what makes the tie resolve to file order.
        val blocks = build(
            listOf(
                ann(quote = "first", note = "n"),
                ann(quote = "second", note = "n"),
                ann(quote = "third", note = "n"),
            ),
        )
        assertEquals(
            listOf("first", "second", "third"),
            blocks.filterIsInstance<TocBlock.NoteEntry>().map { it.annotation.quote },
        )
    }

    @Test
    fun `the chapter label is the chapter number, not the slug`() {
        val blocks = build(listOf(ann(chapter = "ch2", note = "n")))
        assertEquals("Ch 2", blocks.filterIsInstance<TocBlock.NoteEntry>().single().chapterLabel)
    }

    @Test
    fun `the chapters come before the things you do to the book`() {
        // They used to sit directly under the title, so opening a book on a
        // short screen showed the title, Search, the reading record, Export,
        // and then the word "Contents" at the very bottom with nothing under
        // it — you turned a page to read the book you had just opened.
        val blocks = build(emptyList())

        val contentsAt = blocks.indexOfFirst {
            it is TocBlock.SectionHeader && it.text == "Contents"
        }
        val firstChapterAt = blocks.indexOfFirst { it is TocBlock.ChapterEntry }
        val searchAt = blocks.indexOfFirst { it is TocBlock.SearchEntry }
        val readerNotesAt = blocks.indexOfFirst { it is TocBlock.ReaderNotesEntry }
        val exportAt = blocks.indexOfFirst { it is TocBlock.ExportEntry }

        assertTrue(contentsAt < firstChapterAt)
        assertTrue(firstChapterAt < searchAt, "chapters must precede Search")
        assertTrue(firstChapterAt < readerNotesAt)
        assertTrue(firstChapterAt < exportAt)
    }

    @Test
    fun `the reader's own marks stay ahead of the apparatus`() {
        // Notes, questions and bookmarks point INTO the book; search, the
        // reading record and export are things you do TO it. That is the
        // whole ordering rule, and it is the one worth pinning.
        val blocks = build(
            listOf(
                ann(kind = "ask", quote = "asked"),
                ann(kind = "text", quote = "wrote", note = "my note"),
            ),
        )

        val lastMark = maxOf(
            blocks.indexOfLast { it is TocBlock.NoteEntry },
            blocks.indexOfLast { it is TocBlock.QuestionEntry },
        )
        val firstTool = blocks.indexOfFirst {
            it is TocBlock.SearchEntry || it is TocBlock.ReaderNotesEntry || it is TocBlock.ExportEntry
        }

        assertTrue(lastMark < firstTool)
        // And the apparatus really is last — nothing trails it.
        assertTrue(blocks.last() is TocBlock.ExportEntry)
    }
}
