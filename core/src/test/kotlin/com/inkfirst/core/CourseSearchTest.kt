package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CourseSearchTest {

    private val chapterMarkdown = """
        # What Even Is an AI?

        A neural network learns by **gradient descent** — walking downhill.

        - Gradient Descent is the whole trick
        - Something else entirely

        ```recap
        The key idea: gradient descent finds the bottom of the hill.
        ```

        ```exercise
        Describe gradient descent in your own words.
        answer: walking downhill
        ```
    """.trimIndent()

    private val course = Course(
        title = "Neural Networks",
        slug = "nn",
        topicPrompt = "neural networks",
        priorKnowledge = "",
        dailyMinutes = 30,
        style = "headfirst-inspired",
        model = "gemini",
        created = "2026-09-20",
        chapters = listOf(
            PersistedChapter(1, "01-intro", "What Even Is an AI?", listOf("framing"), 30),
            PersistedChapter(2, "02-neuron", "The Neuron", listOf("sums"), 30),
        ),
    )

    private fun search(query: String) =
        CourseSearch.inChapter(chapterMarkdown, query, 1, "01-intro", "What Even Is an AI?")

    @Test
    fun `a hit carries the block index the reader can be sent to`() {
        val hits = search("walking downhill")
        assertTrue(hits.isNotEmpty())
        // The index must address the same block list the reader paginates.
        val blocks = parseMarkdown(chapterMarkdown)
        hits.forEach { assertTrue(it.blockIndex in blocks.indices) }
    }

    @Test
    fun `matching ignores case`() {
        assertTrue(search("GRADIENT DESCENT").isNotEmpty())
        assertTrue(search("Gradient Descent").isNotEmpty())
    }

    @Test
    fun `a match inside bold markup still matches as plain words`() {
        // "**gradient descent**" parses into its own inline. Joining the
        // inlines with a separator would put a space inside the phrase.
        val hits = search("by gradient descent — walking")
        assertEquals(1, hits.size)
    }

    @Test
    fun `recap and exercise bodies are searched, because they are chapter content`() {
        // A recap is often where a concept gets its clearest one-line form.
        val hits = search("finds the bottom of the hill")
        assertEquals(1, hits.size)
    }

    @Test
    fun `headings and list items are searched too`() {
        assertTrue(search("What Even Is an AI").isNotEmpty())
        assertTrue(search("the whole trick").isNotEmpty())
    }

    @Test
    fun `a snippet shows the match in its surrounding words`() {
        val snippet = search("walking downhill").first().snippet
        assertTrue(snippet.contains("walking downhill"))
        assertTrue(snippet.contains("gradient descent"))
    }

    @Test
    fun `a long paragraph is elided on both sides of the match`() {
        val filler = "padding words ".repeat(20)
        val long = "$filler needle here $filler"
        val hit = CourseSearch.inChapter(long, "needle here", 1, "s", "t").single()
        assertTrue(hit.snippet.startsWith("…"))
        assertTrue(hit.snippet.endsWith("…"))
        assertTrue(hit.snippet.contains("needle here"))
        // Bounded, so a result row cannot run away with the page.
        assertTrue(hit.snippet.length < 130, hit.snippet)
    }

    @Test
    fun `a blank query finds nothing rather than everything`() {
        assertTrue(search("").isEmpty())
        assertTrue(search("   ").isEmpty())
    }

    @Test
    fun `nothing matches nothing`() {
        assertTrue(search("quantum chromodynamics").isEmpty())
    }

    // --- the reader's own marks ------------------------------------------

    @Test
    fun `notes and questions are told apart`() {
        val withMarks = course.copy(
            annotations = listOf(
                Annotation("01-intro", 3, "gradient descent", "this is the bit I struggled with", "2026-09-20"),
                Annotation("01-intro", 5, "gradient descent", "", "2026-09-20", kind = "ask"),
            ),
        )
        val hits = CourseSearch.inMarks(withMarks, "gradient")
        assertEquals(
            listOf(CourseSearch.Kind.NOTE, CourseSearch.Kind.QUESTION),
            hits.map { it.kind },
        )
    }

    @Test
    fun `a note matches on what the reader wrote, not only on what they marked`() {
        val withMarks = course.copy(
            annotations = listOf(
                Annotation("01-intro", 3, "a passage", "remember this for the exam", "2026-09-20"),
            ),
        )
        assertEquals(1, CourseSearch.inMarks(withMarks, "exam").size)
    }

    @Test
    fun `a mark carries its chapter number and title`() {
        val withMarks = course.copy(
            annotations = listOf(Annotation("02-neuron", 1, "sums", "n", "2026-09-20")),
        )
        val hit = CourseSearch.inMarks(withMarks, "sums").single()
        assertEquals(2, hit.chapterN)
        assertEquals("The Neuron", hit.chapterTitle)
    }

    // --- the whole book ---------------------------------------------------

    @Test
    fun `the reader's own marks come first`() {
        val withMarks = course.copy(
            annotations = listOf(Annotation("01-intro", 3, "gradient descent", "mine", "2026-09-20")),
        )
        val hits = CourseSearch.inCourse(withMarks, "gradient") { slug ->
            if (slug == "01-intro") chapterMarkdown else null
        }
        assertEquals(CourseSearch.Kind.NOTE, hits.first().kind)
        assertTrue(hits.any { it.kind == CourseSearch.Kind.CHAPTER })
    }

    @Test
    fun `an ungenerated chapter is skipped rather than failing the search`() {
        // Chapter 2 has no file yet — the common case in a course being read
        // as it is written.
        val hits = CourseSearch.inCourse(course, "gradient") { slug ->
            if (slug == "01-intro") chapterMarkdown else null
        }
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.chapterSlug == "01-intro" })
    }
}
