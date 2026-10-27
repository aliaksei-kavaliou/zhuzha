package com.inkfirst.app.ui

import com.inkfirst.core.Block
import com.inkfirst.core.Inline
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Who owns the white at the foot of a page.
 *
 * A bar drawn in the blank half of a page saved correctly and then rendered
 * nowhere, because ink is anchored to a block and a block owned only down to
 * its own box. It was also unerasable, the hit-test using those same bounds
 * on purpose. The cases below are the two that must stay unowned, and they
 * are the whole reason this is a rule rather than "the last item on the page".
 */
class TailOwnerTest {

    private fun para(text: String) = Block.Paragraph(listOf(Inline.Text(text)))
    private fun answer() = Block.CustomFence(
        kind = "exercise",
        params = mapOf(FENCE_ANSWER_PIECE to "1"),
        body = "",
    )

    @Test
    fun `the last block on a page owns the white under it`() {
        val pages = listOf(
            listOf(PageItem(para("a"), 0), PageItem(para("b"), 1)),
            listOf(PageItem(para("c"), 2)),
        )
        assertEquals(1, tailOwnerOf(pages, 0))
        assertEquals(2, tailOwnerOf(pages, 1))
    }

    @Test
    fun `a block that continues overleaf owns nothing below its box`() {
        // Block 1 is split: its head ends page 0, its tail opens page 1.
        val pages = listOf(
            listOf(PageItem(para("a"), 0), PageItem(para("head"), 1)),
            listOf(PageItem(para("tail"), 1), PageItem(para("c"), 2)),
        )
        assertNull(
            tailOwnerOf(pages, 0),
            "extending the head would draw one stroke on both pages — the 2-mugs bug",
        )
        assertEquals(2, tailOwnerOf(pages, 1), "the tail's page still has an owner")
    }

    @Test
    fun `an answer piece never owns the tail`() {
        // An answer carries its exercise's index and reports no bounds, so
        // owning the tail would hand the exercise ink lying over the answer.
        val pages = listOf(listOf(PageItem(para("task"), 7), PageItem(answer(), 7)))
        assertNull(tailOwnerOf(pages, 0))
    }

    @Test
    fun `an empty or absent page owns nothing`() {
        assertNull(tailOwnerOf(emptyList(), 0))
        assertNull(tailOwnerOf(listOf(emptyList()), 0))
        assertNull(tailOwnerOf(listOf(listOf(PageItem(para("a"), 0))), 9))
    }
}
