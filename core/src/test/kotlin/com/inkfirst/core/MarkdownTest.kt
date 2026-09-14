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
    fun `single-line fence with same-line closer parses as empty-body`() {
        // The model routinely emits figure blocks on one line — the whole
        // opening backticks, kind, params, AND closing backticks together.
        // Without dedicated handling the trailing ``` gets swallowed into the
        // info string and the parser looks for a closer on subsequent lines.
        val md = """```figure alt="A diagram"```"""
        val f = parseMarkdown(md).single() as Block.CustomFence
        assertEquals("figure", f.kind)
        assertEquals("A diagram", f.params["alt"])
        assertEquals("", f.body)
    }

    @Test
    fun `single-line figure fence does NOT swallow following blocks`() {
        // Real chapter shape: single-line figure fence, then prose, then a
        // proper multi-line recap fence. Regression fixture — this exact
        // cascade was rendering the recap body as part of the "figure" body
        // and turning downstream fences into an empty CODE box + wrong
        // chapter-summary-inside-a-code-block on the Boox.
        val md = """
            ```figure alt="Diagram"```

            Some prose between the figure and the recap.

            ```recap
            The key point.
            ```
        """.trimIndent()
        val blocks = parseMarkdown(md)
        val fences = blocks.filterIsInstance<Block.CustomFence>()
        assertEquals(2, fences.size)
        assertEquals("figure", fences[0].kind)
        assertEquals("Diagram", fences[0].params["alt"])
        assertEquals("", fences[0].body)
        assertEquals("recap", fences[1].kind)
        assertTrue(fences[1].body.contains("The key point"))
        // Prose paragraph is preserved as its own Paragraph block.
        assertTrue(blocks.any { it is Block.Paragraph })
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
    @Test
    fun `a dashed line is a thematic break, not a paragraph`() {
        // The chapter model emits these as section separators several times
        // per chapter; before Rule existed they rendered as a literal "---".
        val blocks = parseMarkdown("Before.\n\n---\n\nAfter.")
        assertEquals(3, blocks.size)
        assertTrue(blocks[1] is Block.Rule)
    }

    @Test
    fun `asterisk and underscore breaks parse too, but a bullet does not`() {
        assertTrue(parseMarkdown("***").single() is Block.Rule)
        assertTrue(parseMarkdown("___").single() is Block.Rule)
        assertTrue(parseMarkdown("- a list item").single() is Block.BulletItem)
        // Two dashes is not a break — CommonMark wants three.
        assertTrue(parseMarkdown("--").single() is Block.Paragraph)
    }
}
