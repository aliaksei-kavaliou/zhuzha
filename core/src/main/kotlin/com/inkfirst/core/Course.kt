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
    // Human-readable language name — "English", "Belarusian", "Español", etc.
    // Threaded into every generation prompt (outline, chapters, Q&A) as a
    // "write in this language" instruction. Free-text so exotic choices work;
    // memory.md stays in English regardless (internal-only artifact).
    val language: String = "English",
    // Which measurement system examples are written in — see
    // [MeasurementSystem]. Defaults to metric, which also means every course
    // written before this field existed reads as metric from here on.
    val units: String = DEFAULT_MEASUREMENT_SYSTEM,
    /**
     * How many pictures this book may draw — see [FigurePolicy]. Defaulted
     * rather than required, so every course.json written before this existed
     * still loads, and loads as the behaviour it already had.
     */
    val figures: String = DEFAULT_FIGURE_POLICY,
    val created: String,
    val chapters: List<PersistedChapter>,
    val bookmarks: List<Bookmark> = emptyList(),
    val annotations: List<Annotation> = emptyList(),
)

/**
 * A passage-anchored note. The reader selects text ("quote"), taps "Add note"
 * on the selection chip, types a "note", and the pair is saved. [blockIndex]
 * anchors to the first block on the page where the annotation was made so
 * jumping back from the TOC lands on the right page.
 */
@Serializable
data class Annotation(
    @SerialName("chapter_slug") val chapterSlug: String,
    @SerialName("block_index") val blockIndex: Int,
    val quote: String,
    val note: String,
    val created: String,
    /**
     * When this was made, to the second — "2026-09-20T21:14:07".
     *
     * [created] is a DATE, which is the right granularity for showing a note
     * in the Contents and is what identifies one for deletion. It is the
     * wrong granularity for ORDER: everything made in one evening carries the
     * same string, so the Q&A feed could only fall back to the order its two
     * sources happened to be concatenated in — every answered question, then
     * every unasked mark — and a session of reading came out sorted by where
     * on the page things happened rather than by when.
     *
     * Empty for anything written before this field existed; [created] is the
     * fallback, which sorts such entries before the same day's timestamped
     * ones. That is the truth: they are older.
     */
    val at: String = "",
    // "text" for hand-selected annotations; "ask" for a passage the reader
    // asked the AI about; "curve" for stylus lasso-selected annotations
    // (render the stored path). Both text and ask underline their quote and
    // put a glyph in the margin — \u270e for something you wrote, ? for
    // something you wanted explained. An "ask" was originally a margin mark
    // with no underline, to keep the page clean; on the device that left a ?
    // beside a paragraph with nothing saying which sentence it was about.
    // Kept as free-text string not enum so JSON migration is a non-event
    // when we add more kinds.
    val kind: String = "text",
)

/**
 * A single freehand ink stroke — one continuous pen contact. Anchored to a
 * chapter block so the stroke survives font/spacing changes: [InkPoint.x]
 * and [InkPoint.y] are relative to that block's top-left corner at the
 * moment of drawing. Coordinates are in device pixels; the reader converts
 * when rendering.
 */
@Serializable
data class Stroke(
    @SerialName("block_index") val blockIndex: Int,
    val points: List<InkPoint>,
    /** Free-text brush hint for future styling ("pen", "pencil", "marker"). Default "pen". */
    val brush: String = "pen",
    val created: String,
    // Text under the stroke, extracted at draw time by walking the block's
    // character layout and collecting chars whose bbox overlaps the stroke
    // bbox. Nullable because (a) old strokes on disk predate this field, and
    // (b) a stroke that lives entirely in the margin has no underlying text.
    // Locked at draw time so a later font-scale change doesn't drift the
    // meaning of "what I drew over".
    @SerialName("annotated_text") val annotatedText: String? = null,
)

@Serializable
data class InkPoint(
    val x: Float,
    val y: Float,
    /** 0f..1f pressure if the device reports it; 0.5f for no-pressure sources. */
    val pressure: Float = 0.5f,
    /** Optional monotonic timestamp (ms since epoch). Handy for future replay. */
    val t: Long = 0,
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
    // Index into the chapter's parsed Block list — NOT a page number. Pages
    // recompute with font/spacing/screen changes; block index stays valid.
    @SerialName("last_block_index") val lastBlockIndex: Int = 0,
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
 * Pointer to "where the reader was last." Lives at <courses-root>/last_read.json
 * so cold-starts can resume straight into the Reader. Just a pointer — the
 * actual block index is looked up in course.json (source of truth for
 * per-chapter progress).
 */
@Serializable
data class LastRead(
    @SerialName("course_slug") val courseSlug: String,
    @SerialName("chapter_slug") val chapterSlug: String,
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
    language: String,
    units: String = DEFAULT_MEASUREMENT_SYSTEM,
    figures: String = DEFAULT_FIGURE_POLICY,
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
    language = language,
    units = units,
    figures = figures,
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
