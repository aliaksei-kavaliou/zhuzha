package com.inkfirst.app.ui

import com.inkfirst.core.Inline
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The chapter title is printed twice if nobody stops it: once in the byline
 * the app writes ("Chapter 6 · ~30 min · Day 8"), once in whatever heading
 * the model typed.
 */
class ChapterTitleTest {

    private fun text(inlines: List<Inline>) =
        inlines.joinToString("") {
            when (it) {
                is Inline.Text -> it.text
                is Inline.Bold -> it.text
                is Inline.Italic -> it.text
                is Inline.Code -> it.text
            }
        }

    private fun strip(s: String) = text(stripChapterNumber(listOf(Inline.Text(s))))

    @Test
    fun `a colon prefix goes`() {
        assertEquals("Writing Your First Code", strip("Chapter 6: Writing Your First Code"))
    }

    @Test
    fun `so do the dash and full-stop forms`() {
        assertEquals("Null Safety", strip("Chapter 2 — Null Safety"))
        assertEquals("Null Safety", strip("Chapter 2 – Null Safety"))
        assertEquals("Null Safety", strip("Chapter 2 - Null Safety"))
        assertEquals("Null Safety", strip("Chapter 12. Null Safety"))
    }

    @Test
    fun `case does not matter, because the model's does not either`() {
        assertEquals("Beginnings", strip("CHAPTER 1: Beginnings"))
        assertEquals("Beginnings", strip("chapter 1: Beginnings"))
    }

    @Test
    fun `a title that merely mentions a chapter is left alone`() {
        assertEquals(
            "What Chapter 6 Taught Us",
            strip("What Chapter 6 Taught Us"),
        )
        assertEquals("Gradient Descent", strip("Gradient Descent"))
    }

    @Test
    fun `a heading that is nothing but the number keeps it`() {
        // Badly written, but blanking the title is worse than repeating the
        // number — an empty heading reads as a rendering bug.
        assertEquals("Chapter 6:", strip("Chapter 6:"))
    }

    @Test
    fun `markup after the prefix survives`() {
        val inlines = listOf(Inline.Text("Chapter 3: Adding "), Inline.Bold("Layers"))
        assertEquals("Adding Layers", text(stripChapterNumber(inlines)))
    }

    @Test
    fun `a title starting with markup is untouched`() {
        val inlines = listOf(Inline.Bold("Layers"), Inline.Text(" of Thought"))
        assertEquals("Layers of Thought", text(stripChapterNumber(inlines)))
    }
}
