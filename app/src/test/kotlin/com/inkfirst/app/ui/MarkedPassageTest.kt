package com.inkfirst.app.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A passage marked across several paragraphs is ONE annotation whose quote is
 * the fragments joined with spaces. No single block holds that string, so the
 * old whole-quote match underlined nothing anywhere — while a one-paragraph
 * selection underlined fine. These are the cases that asymmetry turned on.
 */
class MarkedPassageTest {

    private val first = "He stopped at the door. The hall was dark and the lamp was out."
    private val middle = "Nobody had been there for a week."
    private val last = "She found the key under the mat and let herself in."

    /** What the reader marked: the tail of one block, one whole, the head of a third. */
    private val quote =
        "The hall was dark and the lamp was out. " +
            "Nobody had been there for a week. " +
            "She found the key under the mat"

    @Test
    fun `the first block underlines its tail, not its whole text`() {
        val (range, words) = longestQuoteRun(first, quote)!!
        assertEquals("The hall was dark and the lamp was out.", first.substring(range.first, range.last + 1))
        assertEquals(9, words)
        assertTrue(touchesEdge(first, range), "a first block's share runs to its end")
    }

    @Test
    fun `a block swallowed whole underlines all of itself`() {
        val (range, _) = longestQuoteRun(middle, quote)!!
        assertEquals(middle, middle.substring(range.first, range.last + 1))
        assertTrue(touchesEdge(middle, range))
    }

    @Test
    fun `the last block underlines its head`() {
        val (range, words) = longestQuoteRun(last, quote)!!
        assertEquals("She found the key under the mat", last.substring(range.first, range.last + 1))
        assertTrue(words >= MIN_SPAN_WORDS)
        assertTrue(touchesEdge(last, range), "a last block's share starts at its first word")
    }

    @Test
    fun `a coincidental repeat in the middle of another block is not claimed`() {
        // Same words, but buried — neither flush with the start nor the end.
        val elsewhere = "Years later he wrote that the hall was dark and the lamp was out, and thought no more of it."
        val (range, words) = longestQuoteRun(elsewhere, quote)!!
        assertTrue(words >= MIN_SPAN_WORDS, "the run is long enough on its own")
        assertFalse(
            touchesEdge(elsewhere, range),
            "but it meets neither edge, so a non-anchor block must not claim it",
        )
    }

    @Test
    fun `one word in common is not a run`() {
        val (_, words) = longestQuoteRun("The kitchen was cold.", quote)!!
        assertTrue(words < MIN_SPAN_WORDS, "'The' alone must not earn an underline")
    }

    @Test
    fun `nothing in common returns nothing`() {
        assertNull(longestQuoteRun("Entirely unrelated prose here.", "zzz qqq"))
    }

    @Test
    fun `empty input is handled rather than thrown at`() {
        assertNull(longestQuoteRun("", quote))
        assertNull(longestQuoteRun(first, "   "))
    }
}
