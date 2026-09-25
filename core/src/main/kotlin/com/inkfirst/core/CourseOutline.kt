package com.inkfirst.core

import kotlinx.serialization.Serializable

/**
 * The outline slice of course.json. See docs/design.md §1. Full course.json
 * (progress, status, per-chapter last_page) is layered on top by :app later.
 */
@Serializable
data class CourseOutline(
    val title: String,
    val chapters: List<ChapterSpec>,
)

@Serializable
data class ChapterSpec(
    val n: Int,
    val slug: String,
    val title: String,
    val objectives: List<String>,
    val estMinutes: Int,
)

/**
 * Derives a filesystem-friendly slug like "01-why-kotlin-feels-weird" from an
 * index + title.
 *
 * Shares [kebabOf] with [courseSlug], which is the point: the narrow "a-z0-9"
 * class was wrong in both, and a Russian book came out with chapters named
 * `01-chapter`, `03-c`, `04-am`, `06-f` — the numbers, and whichever chord
 * names happened to be written in Latin. Only [OutlineGenerator] calls this,
 * at the moment an outline is first written, so widening it moves no chapter
 * that already exists: a slug is stored in `course.json` and reused from
 * there for the life of the book.
 */
fun chapterSlug(n: Int, title: String): String {
    val safe = kebabOf(title).ifEmpty { "chapter" }
    return "%02d-%s".format(n, safe)
}
