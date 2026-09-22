package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChapterPromptTest {

    private val course = Course(
        title = "Neural Networks for Beginners",
        slug = "neural-networks-for-beginners",
        topicPrompt = "neural networks",
        priorKnowledge = "some python",
        expectations = "build basic ai model",
        dailyMinutes = 30,
        style = "headfirst-inspired",
        model = "gemini-3.7-flash",
        created = "2026-08-18",
        chapters = listOf(
            PersistedChapter(1, "01-intro", "What Even Is an AI?", listOf("everyday framing", "components"), 30),
            PersistedChapter(2, "02-neuron", "The Artificial Neuron", listOf("weighted sums"), 30),
        ),
    )

    // --- rewrites ---------------------------------------------------------

    @Test
    fun `an ordinary chapter says nothing about rewriting`() {
        val prompt = buildChapterPrompt(course, course.chapters[1], memory = "")
        assertFalse(prompt.contains("THIS IS A REWRITE"))
        assertFalse(prompt.contains("rejected"))
    }

    @Test
    fun `a rewrite carries the reader's reason and asks for a different route`() {
        val prompt = buildChapterPrompt(
            course, course.chapters[1], memory = "",
            isRewrite = true,
            rewriteFeedback = "Too abstract, I never saw a real number.",
        )
        assertTrue(prompt.contains("THIS IS A REWRITE"))
        assertTrue(prompt.contains("Too abstract, I never saw a real number."))
        // Same destination, different road — the objectives come from the
        // outline and a rewrite does not touch those.
        assertTrue(prompt.contains("Same"))
        assertTrue(prompt.contains("objectives"))
        assertTrue(prompt.contains("weighted sums"))
    }

    @Test
    fun `a rewrite with no reason given still says the last one failed`() {
        // "Just try again" is a legitimate request of a stochastic model, and
        // the prompt has to survive it without a dangling empty section.
        val prompt = buildChapterPrompt(
            course, course.chapters[1], memory = "", isRewrite = true, rewriteFeedback = "",
        )
        assertTrue(prompt.contains("THIS IS A REWRITE"))
        assertTrue(prompt.contains("gave no reason"))
    }

    @Test
    fun `the rewritten chapter must not mention that it is a rewrite`() {
        // The reader asked for a chapter, not an apology from a book.
        val prompt = buildChapterPrompt(
            course, course.chapters[1], memory = "", isRewrite = true, rewriteFeedback = "dull",
        )
        assertTrue(prompt.contains("do not apologise"))
        assertTrue(prompt.contains("as far as the"))
    }

    @Test
    fun `chapter prompt includes course context, full outline, and chapter spec`() {
        val prompt = buildChapterPrompt(course, course.chapters[1], memory = "")

        assertTrue(prompt.contains("neural networks"))
        assertTrue(prompt.contains("some python"))
        assertTrue(prompt.contains("build basic ai model"))
        assertTrue(prompt.contains("30 minutes/day"))
        assertTrue(prompt.contains("1. What Even Is an AI?"))
        assertTrue(prompt.contains("2. The Artificial Neuron"))
        assertTrue(prompt.contains("Chapter 2: The Artificial Neuron"))
        assertTrue(prompt.contains("weighted sums"))
    }

    @Test
    fun `chapter prompt names all three custom fenced blocks`() {
        val prompt = buildChapterPrompt(course, course.chapters[0], memory = "")
        assertTrue(prompt.contains("```exercise"))
        assertTrue(prompt.contains("```recap"))
        assertTrue(prompt.contains("```figure"))
    }

    // --- pictures ---------------------------------------------------------

    @Test
    fun `the figure section names the ceiling the illustrator will enforce`() {
        val prompt = buildChapterPrompt(course, course.chapters[0], memory = "")
        assertTrue(prompt.contains("never more than SIX"))

        val generous = buildChapterPrompt(course.copy(figures = "generous"), course.chapters[0], memory = "")
        assertTrue(generous.contains("never more than TEN"))
    }

    @Test
    fun `a book with no pictures is told not to write briefs at all`() {
        val prompt = buildChapterPrompt(course.copy(figures = "none"), course.chapters[0], memory = "")

        // The figure syntax is never offered — the one mention of it is the
        // instruction not to use it.
        assertFalse(prompt.contains("for figure slots"))
        assertFalse(prompt.contains("kind=\"show\""))
        assertTrue(prompt.contains("Do NOT include any"))
        assertTrue(prompt.contains("written without pictures"))
        // Still a book: what a picture would have carried goes into the words.
        assertTrue(prompt.contains("carried by the prose"))
    }

    @Test
    fun `the writer is told what this screen can and cannot print`() {
        // Otherwise a cooking chapter briefs "the finished risotto, creamy and
        // golden" — impossible on a small grey panel, and the same defect as
        // the capybara: the brief could not be filled, whoever drew it.
        val prompt = buildChapterPrompt(course, course.chapters[0], memory = "")

        assertTrue(prompt.contains("WHAT A PICTURE HERE CAN CARRY"))
        assertTrue(prompt.contains("CANNOT show colour"))
        assertTrue(prompt.contains("Put those in the PROSE"))
    }

    @Test
    fun `blank memory renders as nothing yet fallback for chapter 1`() {
        val prompt = buildChapterPrompt(course, course.chapters[0], memory = "")
        assertTrue(prompt.contains("(nothing yet — this is the first chapter)"))
    }

    @Test
    fun `non-blank memory is passed through verbatim`() {
        val mem = "In chapter 1 we introduced neurons using the light-switch metaphor."
        val prompt = buildChapterPrompt(course, course.chapters[1], memory = mem)
        assertTrue(prompt.contains(mem))
        assertFalse(prompt.contains("(nothing yet"))
    }

    @Test
    fun `chapter prompt tells the model which language to write in`() {
        val ru = course.copy(language = "Belarusian")
        val prompt = buildChapterPrompt(ru, ru.chapters[0], memory = "")
        assertTrue(prompt.contains("in Belarusian"))
    }

    @Test
    fun `memory-update prompt includes chapter markdown and asks for prose`() {
        val md = "# The Artificial Neuron\n\nA neuron is like a tiny scale..."
        val prompt = buildMemoryUpdatePrompt(course.chapters[1], md, learnerGoal = "build basic ai model")
        assertTrue(prompt.contains(md))
        assertTrue(prompt.contains("The Artificial Neuron"))
        assertTrue(prompt.contains("build basic ai model"))
        assertTrue(prompt.contains("prose"))
        assertTrue(prompt.contains("Metaphors"))
    }
}
