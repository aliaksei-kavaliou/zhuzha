package com.inkfirst.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A persisted course — matches course.json in docs/design.md §1.
 * Field names are snake_case on-disk so the file reads well in 10 years'
 * time, regardless of what our Kotlin class names look like by then.
 */
@Serializable
data class Course(
    val version: Int = 1,
    val title: String,
    val slug: String,
    @SerialName("topic_prompt") val topicPrompt: String,
    @SerialName("prior_knowledge") val priorKnowledge: String,
    val expectations: String = "",
    @SerialName("daily_minutes") val dailyMinutes: Int,
    val style: String,
    val model: String,
    val created: String,
    val chapters: List<PersistedChapter>,
    val bookmarks: List<Bookmark> = emptyList(),
)

/**
 * A saved reading location. Anchored to a [blockIndex] within the chapter's
 * parsed Block list — not to a page number, so it survives font-scale changes
 * (which repaginate the same block stream into different page counts).
 */
@Serializable
data class Bookmark(
    @SerialName("chapter_slug") val chapterSlug: String,
    @SerialName("block_index") val blockIndex: Int,
    val created: String,
)

@Serializable
data class PersistedChapter(
    val n: Int,
    val slug: String,
    val title: String,
    val objectives: List<String>,
    @SerialName("est_minutes") val estMinutes: Int,
    val status: ChapterStatus = ChapterStatus.PENDING,
    @SerialName("last_page") val lastPage: Int = 0,
)

@Serializable
enum class ChapterStatus {
    @SerialName("pending") PENDING,
    @SerialName("generated") GENERATED,
    @SerialName("reading") READING,
    @SerialName("done") DONE,
}

/** A lightweight projection for the Library screen — avoids re-reading full chapter lists. */
data class CourseSummary(
    val slug: String,
    val title: String,
    val chapterCount: Int,
    val created: String,
)

/**
 * Kebab-cases a course title. Unlike [chapterSlug] this has no numeric prefix
 * because there's no natural ordinal — the *store* handles collisions by
 * appending "-2", "-3", etc.
 */
fun courseSlug(title: String): String {
    val kebab = title
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(60)
        .trim('-')
    return kebab.ifEmpty { "course" }
}

/** Turns a fresh outline into the persisted structure. Metadata is captured at creation time. */
fun CourseOutline.toCourse(
    slug: String,
    topicPrompt: String,
    priorKnowledge: String,
    expectations: String,
    dailyMinutes: Int,
    style: String,
    model: String,
    created: String,
): Course = Course(
    title = title,
    slug = slug,
    topicPrompt = topicPrompt,
    priorKnowledge = priorKnowledge,
    expectations = expectations,
    dailyMinutes = dailyMinutes,
    style = style,
    model = model,
    created = created,
    chapters = chapters.map {
        PersistedChapter(
            n = it.n,
            slug = it.slug,
            title = it.title,
            objectives = it.objectives,
            estMinutes = it.estMinutes,
        )
    },
)
