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

/** Derives a filesystem-friendly slug like "01-why-kotlin-feels-weird" from an index + title. */
fun chapterSlug(n: Int, title: String): String {
    val kebab = title
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(60)
        .trim('-')
    val safe = kebab.ifEmpty { "chapter" }
    return "%02d-%s".format(n, safe)
}
