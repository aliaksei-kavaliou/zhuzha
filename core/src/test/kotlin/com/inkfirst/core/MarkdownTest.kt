package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownTest {

    @Test
    fun `plain paragraph parses as a single Paragraph block`() {
        val blocks = parseMarkdown("Hello world.")
        assertEquals(1, blocks.size)
        val p = blocks.single() as Block.Paragraph
        assertEquals(listOf(Inline.Text("Hello world.")), p.inlines)
    }

    @Test
    fun `blank line splits paragraphs`() {
        val md = """
            First paragraph.

            Second paragraph.
        """.trimIndent()
        val blocks = parseMarkdown(md)
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it is Block.Paragraph })
    }

    @Test
    fun `multi-line paragraph joins with spaces`() {
        val md = "One line\nSecond line\nThird line."
        val p = parseMarkdown(md).single() as Block.Paragraph
        assertEquals("One line Second line Third line.", (p.inlines.single() as Inline.Text).text)
    }

    @Test
    fun `heading levels are captured`() {
        val md = "# H1\n\n## H2\n\n### H3"
        val blocks = parseMarkdown(md)
        assertEquals(3, blocks.size)
        assertEquals(1, (blocks[0] as Block.Heading).level)
        assertEquals(2, (blocks[1] as Block.Heading).level)
        assertEquals(3, (blocks[2] as Block.Heading).level)
    }

    @Test
    fun `bullet and ordered lists produce list items`() {
        val md = """
            - first bullet
            - second bullet
            1. first ordered
            2. second ordered
        """.trimIndent()
        val blocks = parseMarkdown(md)
        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is Block.BulletItem)
        assertTrue(blocks[1] is Block.BulletItem)
        assertEquals(1, (blocks[2] as Block.OrderedItem).n)
        assertEquals(2, (blocks[3] as Block.OrderedItem).n)
    }

    @Test
    fun `inline bold, italic, and code parse into typed segments`() {
        val md = "This is **bold** and *italic* and `code`."
        val p = parseMarkdown(md).single() as Block.Paragraph
        val kinds = p.inlines.map { it::class.simpleName }
        assertEquals(listOf("Text", "Bold", "Text", "Italic", "Text", "Code", "Text"), kinds)
        assertEquals("bold", (p.inlines[1] as Inline.Bold).text)
        assertEquals("italic", (p.inlines[3] as Inline.Italic).text)
        assertEquals("code", (p.inlines[5] as Inline.Code).text)
    }

    @Test
    fun `unclosed inline marker is treated as a literal`() {
        val md = "This has an *unclosed italic."
        val p = parseMarkdown(md).single() as Block.Paragraph
        // No Italic emitted; the * survives as text.
        assertTrue(p.inlines.none { it is Inline.Italic })
    }

    @Test
    fun `custom fence with no params captures kind and raw body`() {
        val md = """
            ```exercise
            What is 2+2?
            answer: 4
            ```
        """.trimIndent()
        val f = parseMarkdown(md).single() as Block.CustomFence
        assertEquals("exercise", f.kind)
        assertTrue(f.params.isEmpty())
        assertTrue(f.body.contains("What is 2+2?"))
        assertTrue(f.body.contains("answer: 4"))
    }

    @Test
    fun `figure fence extracts alt param`() {
        val md = """
            ```figure alt="A diagram of the loop"
            ```
        """.trimIndent()
        val f = parseMarkdown(md).single() as Block.CustomFence
        assertEquals("figure", f.kind)
        assertEquals("A diagram of the loop", f.params["alt"])
    }

    @Test
    fun `real chapter fixture with all block types parses without loss`() {
        val md = """
            # Chapter Title

            Welcome to chapter one. This has **bold**, *italic*, and `code`.

            ## A subsection

            Regular paragraph.

            - one bullet
            - two bullets

            ```exercise
            Question here.
            answer: The answer.
            ```

            ```figure alt="Diagram"
            ```

            ```recap
            Key idea recap.
            ```
        """.trimIndent()
        val blocks = parseMarkdown(md)
        val counts = blocks.groupingBy { it::class.simpleName }.eachCount()
        assertEquals(1, counts["Heading"]?.let { it - 1 } ?: -1, "should have 2 headings")
        assertTrue(counts["Paragraph"]!! >= 2)
        assertEquals(2, counts["BulletItem"])
        assertEquals(3, counts["CustomFence"])
    }
}
