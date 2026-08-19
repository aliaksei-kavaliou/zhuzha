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
