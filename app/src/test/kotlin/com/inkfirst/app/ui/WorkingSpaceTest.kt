package com.inkfirst.app.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * How much room an exercise offers to work in.
 *
 * Two measures because there are two kinds of work, and the split was found
 * on the Boox (2026-10-01): a reader drew eleven circles across a grid sized
 * by a 180-character sentence, and the revealed answer printed over the bottom
 * row of them. The answer's length is the right measure for something you
 * WRITE and says nothing about the room a drawing needs.
 */
class WorkingSpaceTest {

    @Test
    fun `a grid is sized from the task, not from the answer`() {
        val short = answerSpaceLineCount("Yes.", ruled = false)
        val long = answerSpaceLineCount("x".repeat(400), ruled = false)
        assertEquals(GRID_ROWS, short)
        assertEquals(GRID_ROWS, long)
        // The whole point: the answer's length no longer moves it.
        assertEquals(short, long)
    }

    @Test
    fun `a making task with no answer at all still gets the full grid`() {
        assertEquals(GRID_ROWS, answerSpaceLineCount("", ruled = false))
    }

    @Test
    fun `ruled lines are still sized from the written answer`() {
        val short = answerSpaceLineCount("Yes.", ruled = true)
        val long = answerSpaceLineCount("x".repeat(400), ruled = true)
        assertTrue(long > short, "a longer written answer earns more lines")
        // ONE LINE at the short end, from 2026-10-07. The floor was two,
        // set when this was the only sizing there was and a single rule
        // looked like a mistake on an otherwise empty page. It no longer is:
        // the block has a header band above it and a closing rule below, so
        // one ruled line reads as exactly what it is — the place to write
        // "6.0". Two ruled lines under a one-digit answer read as a mistake.
        //
        // Six still caps the other end: past that an exercise has become a
        // form.
        assertEquals(1, short)
        assertEquals(6, long)
    }

    @Test
    fun `writing lines with nothing to write toward are not drawn`() {
        assertEquals(0, answerSpaceLineCount("", ruled = true))
    }

    @Test
    fun `the grid is generous enough to be worth drawing in`() {
        // Guards the direction of the 2026-10-01 change rather than the exact
        // number: the old rule topped out at six for every treatment.
        assertTrue(GRID_ROWS > 6, "a drawing area should beat the old written-answer cap")
    }
}
