package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FigureSlotsTest {

    @Test
    fun `finds single-line figure fences in order`() {
        val md = """
            # Chapter

            ```figure alt="A tired robot sorting socks"```

            Some prose.

            ```figure alt="A kitchen with one cook"```
        """.trimIndent()

        val slots = findFigureSlots(md)
        assertEquals(2, slots.size)
        assertEquals(listOf(1, 2), slots.map { it.ordinal })
        assertEquals("A tired robot sorting socks", slots[0].alt)
        assertEquals("A kitchen with one cook", slots[1].alt)
        assertTrue(slots.all { it.isPending })
    }

    @Test
    fun `multi-line figure falls back to body as the brief`() {
        val md = """
            ```figure
            A whiteboard covered in arrows
            ```
        """.trimIndent()

        val slot = findFigureSlots(md).single()
        assertEquals("", slot.alt)
        assertEquals("A whiteboard covered in arrows", slot.brief)
    }

    @Test
    fun `a figure fence inside another block's body is not a slot`() {
        // The exercise body happens to mention a figure fence. Counting it
        // would shift every later ordinal and mis-file the generated images.
        val md = """
            ```exercise
            Write a caption for ```figure alt="x"```
            ```

            ```figure alt="the real one"```
        """.trimIndent()

        val slot = findFigureSlots(md).single()
        assertEquals("the real one", slot.alt)
        assertEquals(1, slot.ordinal)
    }

    @Test
    fun `an already-illustrated slot is not pending`() {
        val md = """```figure alt="A robot" src="01-intro-1.png"```"""
        val slot = findFigureSlots(md).single()
        assertEquals("01-intro-1.png", slot.src)
        assertFalse(slot.isPending)
    }

    @Test
    fun `a slot with no brief is not pending`() {
        val slot = findFigureSlots("```figure```").single()
        assertNull(slot.src)
        assertFalse(slot.isPending)
    }

    @Test
    fun `withFigureSrc fills single-line fences and keeps the closer`() {
        val md = """```figure alt="A robot"```"""
        val out = withFigureSrc(md, mapOf(1 to "01-intro-1.png"))
        assertEquals("""```figure alt="A robot" src="01-intro-1.png"```""", out)
    }

    @Test
    fun `withFigureSrc fills multi-line fences`() {
        val md = """
            ```figure alt="A robot"
            extra body
            ```
        """.trimIndent()

        val out = withFigureSrc(md, mapOf(1 to "01-intro-1.png"))
        assertTrue(out.lines()[0] == """```figure alt="A robot" src="01-intro-1.png"""")
        assertTrue(out.contains("extra body"))
    }

    @Test
    fun `withFigureSrc replaces an existing src rather than duplicating it`() {
        val md = """```figure alt="A robot" src="old.png"```"""
        val out = withFigureSrc(md, mapOf(1 to "new.png"))
        assertEquals("""```figure alt="A robot" src="new.png"```""", out)
        assertEquals(1, Regex("src=").findAll(out).count())
    }

    @Test
    fun `withFigureSrc leaves every other line and the block count untouched`() {
        // Annotations and ink strokes are anchored by block index, so filling
        // in a picture must not add, remove or reorder a single block.
        val md = """
            # Heading

            A paragraph the reader may have underlined.

            ```figure alt="A robot"```

            ```recap
            The point again.
            ```
        """.trimIndent()

        val out = withFigureSrc(md, mapOf(1 to "01-intro-1.png"))
        assertEquals(parseMarkdown(md).size, parseMarkdown(out).size)
        assertEquals(md.lines().size, out.lines().size)
        assertTrue(out.contains("A paragraph the reader may have underlined."))
        assertTrue(out.contains("The point again."))
    }

    @Test
    fun `withFigureSrc is a no-op when nothing was generated`() {
        val md = """```figure alt="A robot"```"""
        assertEquals(md, withFigureSrc(md, emptyMap()))
    }

    @Test
    fun `the renderer sees the src param after a fill`() {
        // End-to-end guard: whatever we write must survive the real parser,
        // because that is what FigureBlock reads.
        val md = """```figure alt="A robot"```"""
        val filled = withFigureSrc(md, mapOf(1 to "01-intro-1.png"))
        val fence = parseMarkdown(filled).single() as Block.CustomFence
        assertEquals("figure", fence.kind)
        assertEquals("01-intro-1.png", fence.params["src"])
        assertEquals("A robot", fence.params["alt"])
    }

    @Test
    fun `contextAround picks prose from both sides and skips structure`() {
        val md = """
            # Skipped heading

            Prose before the picture.

            ```figure alt="A robot"```

            Prose after the picture.
        """.trimIndent()

        val context = contextAround(md, findFigureSlots(md).single())
        assertTrue(context.contains("Prose before the picture."))
        assertTrue(context.contains("Prose after the picture."))
        assertFalse(context.contains("Skipped heading"))
        assertFalse(context.contains("```"))
    }

    @Test
    fun `a fence with no kind param is a mood figure`() {
        val md = """
            Some prose.

            ```figure alt="A tired robot sorting socks"
            ```
        """.trimIndent()

        assertEquals(FigureKind.MOOD, findFigureSlots(md).single().kind)
    }

    @Test
    fun `kind=show is parsed`() {
        // The passage this exists for: a chapter teaching a pencil grip got a
        // capybara in a meadow, because the prompt asked for a scene.
        val md = """
            ```figure kind="show" alt="A hand holding a pencil well back from the tip"
            ```
        """.trimIndent()

        val slot = findFigureSlots(md).single()
        assertEquals(FigureKind.SHOW, slot.kind)
        assertEquals("A hand holding a pencil well back from the tip", slot.alt)
    }

    @Test
    fun `an unrecognised kind falls back to mood rather than failing`() {
        // A generated fence is model output; a typo must cost the picture its
        // treatment, never the picture.
        val md = """
            ```figure kind="diagram" alt="something"
            ```
        """.trimIndent()

        assertEquals(FigureKind.MOOD, findFigureSlots(md).single().kind)
    }

    @Test
    fun `kind survives having src written back into the fence`() {
        // withFigureSrc rewrites the opener line in place; losing the kind
        // there would silently re-brief the picture on any later redraw.
        val md = """
            ```figure kind="show" alt="A hand holding a pencil"
            ```
        """.trimIndent()

        val filled = withFigureSrc(md, mapOf(1 to "01-intro-1.jpg"))
        val slot = findFigureSlots(filled).single()
        assertEquals(FigureKind.SHOW, slot.kind)
        assertEquals("01-intro-1.jpg", slot.src)
    }

    @Test
    fun `the two kinds brief the artist differently`() {
        val course = Course(
            title = "Sketching", slug = "sketching", topicPrompt = "draw",
            priorKnowledge = "none", dailyMinutes = 30, style = "headfirst-inspired",
            model = "gemini-x", created = "2026-09-21", chapters = emptyList(),
        )
        val mood = buildFigurePrompt(course, "Holding the pencil", "a hand on a pencil", "", FigureKind.MOOD)
        val show = buildFigurePrompt(course, "Holding the pencil", "a hand on a pencil", "", FigureKind.SHOW)

        // A show figure is told the picture has a job and that hands are the
        // thing most likely to spoil it; a mood figure is invited to be funny.
        assertTrue(show.contains("THE PICTURE HAS A JOB"))
        assertTrue(show.contains("exactly five fingers"))
        assertFalse(mood.contains("THE PICTURE HAS A JOB"))

        assertTrue(mood.contains("a little humorous"))
        assertFalse(show.contains("a little humorous"))
        // Speech bubbles are encouragement in one and forbidden in the other.
        assertTrue(mood.contains("speech bubble is welcome"))
        assertTrue(show.contains("No speech bubbles"))

        // Both still share the rules that keep the book looking like one book.
        listOf(mood, show).forEach {
            assertTrue(it.contains("pure greyscale"))
            assertTrue(it.contains("PURE LINE ART"))
        }
    }
}
