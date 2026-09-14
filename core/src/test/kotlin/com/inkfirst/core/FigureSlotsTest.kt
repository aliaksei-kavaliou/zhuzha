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
}
