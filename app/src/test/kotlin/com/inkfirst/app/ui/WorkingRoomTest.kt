package com.inkfirst.app.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The model sizes an exercise's working room as a percentage of one page.
 * The conversion is the only part of that which can be tested without a
 * device: a row is a font size, a percentage is a page, and this turns one
 * into the other.
 */
class WorkingRoomTest {
    private val budget = 1000      // a page
    private val rowPx = 50f        // twenty rows to a page

    @Test
    fun `a full page of room is a full page of rows`() {
        assertEquals(20, roomRowsFor(100, budget, rowPx))
    }

    @Test
    fun `half a page is half the rows`() {
        assertEquals(10, roomRowsFor(50, budget, rowPx))
    }

    @Test
    fun `a yes or no answer still gets a line`() {
        // 5% of this page is one row exactly; anything smaller must not
        // round away to nothing, because the model asked for somewhere to
        // write and no room at all is the one answer it did not give.
        assertEquals(1, roomRowsFor(5, budget, rowPx))
        assertEquals(1, roomRowsFor(1, budget, rowPx))
    }

    @Test
    fun `nothing asked for is nothing drawn`() {
        assertEquals(0, roomRowsFor(0, budget, rowPx))
        assertEquals(0, roomRowsFor(-10, budget, rowPx))
    }

    @Test
    fun `more than a page is capped at a page`() {
        // Over-sized requests are the splitter's problem, not this one's:
        // it hands back a page of rows and the page split takes it from
        // there. What it must never do is return a count no page can hold.
        assertEquals(roomRowsFor(100, budget, rowPx), roomRowsFor(400, budget, rowPx))
    }

    @Test
    fun `a row count never exceeds the page it was measured against`() {
        for (pct in 1..100) {
            val rows = roomRowsFor(pct, budget, rowPx)
            assertTrue(rows * rowPx <= budget, "room of $pct% overflows the page")
        }
    }

    @Test
    fun `a zero row height cannot divide`() {
        assertEquals(0, roomRowsFor(50, budget, 0f))
    }
}
