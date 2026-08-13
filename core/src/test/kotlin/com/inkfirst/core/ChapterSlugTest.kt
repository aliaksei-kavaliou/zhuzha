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
}
