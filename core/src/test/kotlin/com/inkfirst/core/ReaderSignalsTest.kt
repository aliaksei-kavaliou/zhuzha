package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderSignalsTest {

    /** Exactly the shape CourseStore.appendQa writes. */
    private val qaFile = """
        ## 2026-09-16

        > a gradient is the slope of the error surface

        **Q:** what is a gradient exactly?

        **A:** It is the direction of steepest increase.

        ---

        ## 2026-09-17

        > the step size controls how far we move

        **Q:** is this the same as learning rate?

        **A:** Yes, the terms are used interchangeably.

        ---

    """.trimIndent()

    private fun ask(chapter: String, quote: String) = Annotation(
        chapterSlug = chapter, blockIndex = 3, quote = quote,
        note = "", created = "2026-09-17", kind = "ask",
    )

    private fun note(chapter: String, quote: String, text: String) = Annotation(
        chapterSlug = chapter, blockIndex = 4, quote = quote,
        note = text, created = "2026-09-17", kind = "text",
    )

    @Test
    fun `parses each question with the passage it was asked about`() {
        val qs = ReaderSignals.parseQuestions(qaFile)
        assertEquals(2, qs.size)
        assertEquals("what is a gradient exactly?", qs[0].question)
        assertEquals("a gradient is the slope of the error surface", qs[0].passage)
        assertEquals("is this the same as learning rate?", qs[1].question)
        assertEquals("the step size controls how far we move", qs[1].passage)
    }

    @Test
    fun `a passage does not leak into the next entry`() {
        // The second entry has no quote. Without clearing the buffer at the
        // separator it would inherit the first entry's passage, which is the
        // kind of wrong-but-plausible detail that then gets fed to the model.
        val qs = ReaderSignals.parseQuestions(
            """
            ## 2026-09-16

            > backpropagation walks the error backwards

            **Q:** why backwards?

            **A:** Because each layer's blame depends on the next one's.

            ---

            ## 2026-09-16

            **Q:** what is a tensor?

            **A:** An array with more than two dimensions.

            ---
            """.trimIndent(),
        )
        assertEquals(2, qs.size)
        assertEquals("backpropagation walks the error backwards", qs[0].passage)
        assertNull(qs[1].passage)
    }

    @Test
    fun `handles an empty or question-free file`() {
        assertTrue(ReaderSignals.parseQuestions("").isEmpty())
        assertTrue(ReaderSignals.parseQuestions("## 2026-09-16\n\nnothing here\n").isEmpty())
    }

    @Test
    fun `notes record questions, marked passages and written notes`() {
        val body = ReaderSignals.notesForChapter(
            chapterSlug = "03-gradients",
            annotations = listOf(
                ask("03-gradients", "a gradient is the slope of the error surface"),
                ask("03-gradients", "loss functions measure how wrong we are"),
                note("03-gradients", "backpropagation walks the error backwards", "revisit this"),
                // Belongs to another chapter and must not appear.
                ask("04-training", "epochs are full passes over the data"),
            ),
            qaMarkdown = qaFile,
        )
        requireNotNull(body)
        assertTrue(body.contains("Asked 2 questions"), body)
        assertTrue(body.contains("what is a gradient exactly?"), body)
        // Marked but never asked about — a different signal, kept separate.
        assertTrue(body.contains("Stopped at these passages without asking"), body)
        assertTrue(body.contains("loss functions measure how wrong we are"), body)
        assertTrue(body.contains("Wrote 1 note"), body)
        assertTrue(body.contains("revisit this"), body)
        assertFalse(body.contains("epochs"), body)
    }

    @Test
    fun `a passage that was asked about is not also listed as unasked`() {
        val body = ReaderSignals.notesForChapter(
            chapterSlug = "03-gradients",
            annotations = listOf(ask("03-gradients", "a gradient is the slope of the error surface")),
            qaMarkdown = qaFile,
        )
        requireNotNull(body)
        assertFalse(body.contains("Stopped at these passages"), body)
    }

    @Test
    fun `no trace means no section at all`() {
        // Saying nothing is right here. Recording an absence would invite the
        // model to read meaning into a chapter the reader simply read quietly.
        assertNull(
            ReaderSignals.notesForChapter(
                chapterSlug = "03-gradients",
                annotations = listOf(ask("04-training", "something else")),
                qaMarkdown = "",
            ),
        )
    }

    @Test
    fun `singular and plural read correctly`() {
        val body = ReaderSignals.notesForChapter(
            chapterSlug = "c",
            annotations = emptyList(),
            qaMarkdown = "**Q:** why?\n\n**A:** because\n\n---\n",
        )
        requireNotNull(body)
        assertTrue(body.contains("Asked 1 question:"), body)
    }

    @Test
    fun `long quotes are trimmed on a word boundary`() {
        val long = "a gradient is the slope of the error surface and it points " +
            "in the direction of steepest increase which is why we walk the other way"
        val body = ReaderSignals.notesForChapter(
            chapterSlug = "c",
            annotations = listOf(ask("c", long)),
            qaMarkdown = "",
        )
        requireNotNull(body)
        assertTrue(body.contains("…"), body)
        // Never mid-word: the character before the ellipsis ends a word.
        val cut = body.substringBefore("…")
        assertFalse(cut.endsWith(" "), cut)
        assertTrue(long.startsWith(cut.substringAfter("\"")), cut)
    }
}

class ReaderNotesStorageTest {

    private fun store(dir: java.io.File) = CourseStore(dir)

    @Test
    fun `appends a section and refuses to record the same chapter twice`(
        @org.junit.jupiter.api.io.TempDir dir: java.io.File,
    ) {
        val s = store(dir)
        java.io.File(dir, "course-a").mkdirs()

        assertTrue(s.appendReaderNotes("course-a", 1, "First Steps", "Asked 1 question:\n- Asked: why?"))
        val once = s.readReaderNotes("course-a")
        assertTrue(once.contains("## Chapter 1 — how the reading went"), once)
        assertTrue(once.contains("why?"), once)

        // The compile path runs this over the whole course every time, so a
        // second call must be a no-op rather than a duplicate section.
        assertFalse(s.appendReaderNotes("course-a", 1, "First Steps", "Asked 2 questions:\n- Asked: how?"))
        assertEquals(once, s.readReaderNotes("course-a"))

        assertTrue(s.appendReaderNotes("course-a", 2, "Next", "Asked 1 question:\n- Asked: what?"))
        val both = s.readReaderNotes("course-a")
        assertTrue(both.contains("## Chapter 1 —"), both)
        assertTrue(both.contains("## Chapter 2 —"), both)
    }

    @Test
    fun `an empty body writes nothing`(@org.junit.jupiter.api.io.TempDir dir: java.io.File) {
        val s = store(dir)
        java.io.File(dir, "course-b").mkdirs()
        assertFalse(s.appendReaderNotes("course-b", 1, "T", "   "))
        assertEquals("", s.readReaderNotes("course-b"))
    }

    @Test
    fun `reading a course with no reader file is empty, not an error`(
        @org.junit.jupiter.api.io.TempDir dir: java.io.File,
    ) {
        assertEquals("", store(dir).readReaderNotes("never-written"))
    }
}

class ChapterPromptReaderNotesTest {

    private val course = Course(
        slug = "c", title = "T", topicPrompt = "topic", priorKnowledge = "", expectations = "",
        dailyMinutes = 30, style = "headfirst-inspired", model = DEFAULT_GEMINI_MODEL,
        created = "2026-09-17",
        chapters = listOf(PersistedChapter(1, "01", "One", listOf("o"), 30)),
    )

    @Test
    fun `without reader notes the prompt gains no section and no rules`() {
        val p = buildChapterPrompt(course, course.chapters[0], memory = "stuff")
        assertFalse(p.contains("HOW THE READING HAS GONE"), p)
    }

    @Test
    fun `with reader notes the section and its boundaries are both present`() {
        val p = buildChapterPrompt(
            course, course.chapters[0], memory = "stuff",
            readerNotes = "## Chapter 1\nAsked 2 questions about gradients.",
        )
        assertTrue(p.contains("HOW THE READING HAS GONE"), p)
        assertTrue(p.contains("Asked 2 questions about gradients."), p)
        // The bounding rules are the design, not decoration: an unbounded
        // "adapt to the learner" instruction is the failure mode.
        assertTrue(p.contains("not a grade"), p)
        assertTrue(p.contains("Do NOT restructure the chapter around it"), p)
        assertTrue(p.contains("say in one short sentence"), p)
    }
}
