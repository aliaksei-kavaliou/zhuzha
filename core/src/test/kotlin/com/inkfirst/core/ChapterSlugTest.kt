package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChapterSlugTest {

    @Test
    fun `basic kebab-case with zero-padded index`() {
        assertEquals("01-why-kotlin-feels-weird", chapterSlug(1, "Why Kotlin feels weird"))
    }

    @Test
    fun `strips punctuation and collapses repeats`() {
        assertEquals("02-null-safety-hello", chapterSlug(2, "Null-safety: hello!!!"))
    }

    @Test
    fun `handles double-digit index`() {
        assertEquals("12-generics", chapterSlug(12, "Generics"))
    }

    @Test
    fun `falls back to 'chapter' when title has no alphanumerics`() {
        assertEquals("03-chapter", chapterSlug(3, "???"))
    }

    @Test
    fun `trims trailing dashes after truncation`() {
        val slug = chapterSlug(1, "a".repeat(65))
        // 60-char cap plus zero-padded prefix = "01-" + 60 chars.
        assertEquals("01-${"a".repeat(60)}", slug)
    }

    @Test
    fun `a chapter title in another script keeps its words`() {
        // The ukulele book is the evidence: its chapters were stored as
        // 01-chapter, 03-c, 04-am, 06-f — the index, plus whichever chord
        // names happened to be spelled in Latin. Everything else was dropped
        // by the same [^a-z0-9] class that broke courseSlug.
        assertEquals(
            "03-самый-первый-аккорд-знакомимся-с-аккордом-c",
            chapterSlug(3, "Самый первый аккорд: знакомимся с аккордом C"),
        )
    }

    @Test
    fun `an ascii chapter title slugs exactly as it always did`() {
        assertEquals("01-why-kotlin-feels-weird", chapterSlug(1, "Why Kotlin Feels Weird"))
    }

    @Test
    fun `a title with nothing sluggable still gets its number`() {
        assertEquals("07-chapter", chapterSlug(7, "!!!"))
    }
}
