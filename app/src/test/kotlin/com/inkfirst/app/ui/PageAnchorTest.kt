package com.inkfirst.app.ui

import com.inkfirst.core.Block
import com.inkfirst.core.Inline
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Which block index a page is remembered by.
 *
 * The rule looks like an off-by-one until you see a split paragraph: two
 * consecutive pages carry the same index, so choosing the first block on the
 * page makes "which page is this" ambiguous, and the ambiguity resolved the
 * wrong way — a bookmark gave back the page before the one it was set on.
 */
class PageAnchorTest {

    private fun para(text: String) = Block.Paragraph(listOf(Inline.Text(text)))

    private fun page(vararg items: PageItem) = items.toList()

    @Test
    fun `an ordinary page is remembered by its first block`() {
        val p = page(PageItem(para("a"), 10), PageItem(para("b"), 11))
        assertEquals(10, anchorForPage(p))
    }

    @Test
    fun `a page opening on a continuation skips it`() {
        // Block 12 started on the previous page. Naming it here would resolve
        // back to that page — the bookmark bug.
        val p = page(
            PageItem(para("…rest of 12"), 12, skippedPx = 300),
            PageItem(para("13"), 13),
        )
        assertEquals(13, anchorForPage(p))
    }

    @Test
    fun `a page that is nothing but a continuation has only that index`() {
        // One paragraph long enough to fill a page on its own. Ambiguous, and
        // there is nothing better to say — but it must not return 0 and send
        // the reader to the front of the chapter.
        val p = page(PageItem(para("middle of a very long one"), 12, skippedPx = 600))
        assertEquals(12, anchorForPage(p))
    }

    // --- which page wears the ribbon -------------------------------------

    @Test
    fun `a bookmark shows on the page it opens, and on no other`() {
        val blocks = (0..14).map { para("block $it") }
        val pages = listOf(
            page(PageItem(blocks[10], 10), PageItem(blocks[11], 11)),
            page(PageItem(blocks[12], 12), PageItem(blocks[13], 13)),
            page(PageItem(blocks[14], 14)),
        )
        assertEquals(12, bookmarkOnPage(pages, blocks, 1, listOf(12)))
        assertNull(bookmarkOnPage(pages, blocks, 0, listOf(12)))
        assertNull(bookmarkOnPage(pages, blocks, 2, listOf(12)))
    }

    @Test
    fun `a bookmark survives the reflow that orphaned it`() {
        // The reported bug. The bookmark was set when block 12 began a page;
        // widening the margins pushed it into the middle of one. It still
        // OPENED that page — the jump asks "which page contains 12" — while
        // the ribbon asked "is 12 this page's anchor" and got 11. No dog-ear,
        // and the menu offered to bookmark a page that already was.
        val blocks = (0..14).map { para("block $it") }
        val reflowed = listOf(
            page(PageItem(blocks[10], 10)),
            page(PageItem(blocks[11], 11), PageItem(blocks[12], 12), PageItem(blocks[13], 13)),
        )
        assertEquals(11, anchorForPage(reflowed[1]))
        // The page the bookmark opens…
        assertEquals(1, findPageContainingBlock(reflowed, blocks, 12))
        // …is the page that shows it.
        assertEquals(12, bookmarkOnPage(reflowed, blocks, 1, listOf(12)))
    }

    @Test
    fun `a bookmark on a split paragraph claims only the page it opens`() {
        // Block 12 appears on two pages. The jump resolves to the first, so
        // the ribbon must appear there and nowhere else — otherwise one
        // bookmark wears two dog-ears.
        val blocks = (0..14).map { para("block $it") }
        val pages = listOf(
            page(PageItem(blocks[11], 11), PageItem(blocks[12], 12)),
            page(PageItem(blocks[12], 12, skippedPx = 300), PageItem(blocks[13], 13)),
        )
        assertEquals(12, bookmarkOnPage(pages, blocks, 0, listOf(12)))
        assertNull(bookmarkOnPage(pages, blocks, 1, listOf(12)))
    }

    @Test
    fun `no bookmarks means no ribbon anywhere`() {
        val blocks = listOf(para("only"))
        val pages = listOf(page(PageItem(blocks[0], 0)))
        assertNull(bookmarkOnPage(pages, blocks, 0, emptyList()))
    }

    @Test
    fun `an empty or missing page is the start of the chapter`() {
        assertEquals(0, anchorForPage(null))
        assertEquals(0, anchorForPage(emptyList()))
    }

    @Test
    fun `the anchor resolves back to the page it came from`() {
        // The property that matters, stated directly: round-tripping an
        // anchor through findPageContainingBlock has to be the identity.
        val blocks = (0..14).map { para("block $it") }
        val pages = listOf(
            page(PageItem(blocks[10], 10), PageItem(blocks[11], 11), PageItem(blocks[12], 12)),
            page(PageItem(blocks[12], 12, skippedPx = 300), PageItem(blocks[13], 13)),
            page(PageItem(blocks[14], 14)),
        )
        pages.indices.forEach { i ->
            assertEquals(i, findPageContainingBlock(pages, blocks, anchorForPage(pages[i])))
        }
    }
}
