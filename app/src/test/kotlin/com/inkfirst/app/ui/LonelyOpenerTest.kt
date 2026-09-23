package com.inkfirst.app.ui

import com.inkfirst.core.Block
import com.inkfirst.core.Inline
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The first page of a chapter held nothing but the byline, with the title and
 * its cover picture on the page after — seen on the Boox on a Russian chapter
 * whose title runs to three lines (2026-09-23).
 */
class LonelyOpenerTest {

    private fun meta(index: Int) =
        PageItem(Block.CustomFence(kind = "meta", params = emptyMap(), body = "Chapter 1"), index)

    private fun heading(index: Int) =
        PageItem(Block.Heading(level = 1, inlines = listOf(Inline.Text("Title"))), index)

    private fun para(index: Int) =
        PageItem(Block.Paragraph(listOf(Inline.Text("Words."))), index)

    private fun indices(pages: List<List<PageItem>>) = pages.map { page -> page.map { it.index } }

    @Test
    fun `a page holding only the byline gives it to the page that follows`() {
        val pages = listOf(listOf(meta(0)), listOf(heading(1), para(2)))

        assertEquals(listOf(listOf(0, 1, 2)), indices(mergeLonelyOpeners(pages)))
    }

    @Test
    fun `a page holding only a heading does the same`() {
        val pages = listOf(listOf(meta(0), para(1)), listOf(heading(2)), listOf(para(3)))

        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), indices(mergeLonelyOpeners(pages)))
    }

    @Test
    fun `two lonely pages in a row both travel to the content`() {
        val pages = listOf(listOf(meta(0)), listOf(heading(1)), listOf(para(2)))

        assertEquals(listOf(listOf(0, 1, 2)), indices(mergeLonelyOpeners(pages)))
    }

    @Test
    fun `a heading with nothing after it stays where it is`() {
        // There is no page to give it to, and dropping it would lose a block.
        val pages = listOf(listOf(para(0)), listOf(heading(1)))

        assertEquals(listOf(listOf(0), listOf(1)), indices(mergeLonelyOpeners(pages)))
    }

    @Test
    fun `ordinary pages are left alone`() {
        val pages = listOf(listOf(meta(0), heading(1), para(2)), listOf(para(3)))

        assertEquals(listOf(listOf(0, 1, 2), listOf(3)), indices(mergeLonelyOpeners(pages)))
    }

    @Test
    fun `a single page is never merged into nothing`() {
        val pages = listOf(listOf(meta(0)))

        assertEquals(listOf(listOf(0)), indices(mergeLonelyOpeners(pages)))
    }
}
