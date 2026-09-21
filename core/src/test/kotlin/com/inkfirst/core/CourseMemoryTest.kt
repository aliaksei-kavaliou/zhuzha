package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * memory.md is what every later chapter is written against, so a stale block
 * in it is not a cosmetic problem — it is the book teaching on top of prose
 * the reader never read.
 */
class CourseMemoryTest {

    private val memory = """
        --- Chapter 1: Beginnings ---
        Introduced gradients as walking downhill.

        --- Chapter 2: Middles ---
        Built on the hill metaphor with a blindfold.

        --- Chapter 3: Ends ---
        Closed the loop.

    """.trimIndent()

    @Test
    fun `replacing a chapter keeps its place in the order`() {
        val out = CourseMemory.replaceChapter(memory, 2, "Middles", "Used a maze instead.")

        assertTrue(out.contains("Used a maze instead."))
        assertTrue(!out.contains("blindfold"))
        // Order is what makes this file readable as a narrative by the model.
        assertTrue(out.indexOf("Chapter 1") < out.indexOf("Chapter 2"))
        assertTrue(out.indexOf("Chapter 2") < out.indexOf("Chapter 3"))
    }

    @Test
    fun `replacing leaves every other chapter untouched`() {
        val out = CourseMemory.replaceChapter(memory, 2, "Middles", "New.")
        assertTrue(out.contains("Introduced gradients as walking downhill."))
        assertTrue(out.contains("Closed the loop."))
    }

    @Test
    fun `a chapter with no block yet is appended`() {
        // Happens when the memory pass failed on the first attempt — the
        // chapter exists, its summary never landed.
        val out = CourseMemory.replaceChapter(memory, 4, "Extras", "Late arrival.")
        assertTrue(out.contains("--- Chapter 4: Extras ---"))
        assertTrue(out.indexOf("Chapter 3") < out.indexOf("Chapter 4"))
    }

    @Test
    fun `replacing into empty memory just writes the block`() {
        val out = CourseMemory.replaceChapter("", 1, "First", "Only.")
        assertEquals("--- Chapter 1: First ---\nOnly.\n\n", out)
    }

    @Test
    fun `withoutChapter drops exactly one block`() {
        val out = CourseMemory.withoutChapter(memory, 2)
        assertTrue(!out.contains("Chapter 2"))
        assertTrue(out.contains("Chapter 1"))
        assertTrue(out.contains("Chapter 3"))
    }

    @Test
    fun `text before the first header survives, because a reader may have written it`() {
        // memory.md is hand-editable by design. Someone's own note at the top
        // of the file must not be eaten by a rewrite.
        val withPreamble = "My own notes about this book.\n\n$memory"
        val out = CourseMemory.replaceChapter(withPreamble, 1, "Beginnings", "Redone.")
        assertTrue(out.startsWith("My own notes about this book."))
        assertTrue(out.contains("Redone."))
    }

    @Test
    fun `a chapter that is not there leaves the file alone`() {
        assertEquals(memory.trimEnd() + "\n", CourseMemory.withoutChapter(memory, 9))
    }
}
