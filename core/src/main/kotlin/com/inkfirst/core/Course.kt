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
    /**
     * What the reader typed when they chose [StylePresets.CUSTOM_KEY] —
     * "written as a fairy tale", "a friendly no-jargon beginner's guide".
     * Verbatim, and never sent to the model.
     *
     * It is kept because [stylePrompt] is an expansion of it, and an
     * expansion you cannot compare against the wish that produced it is
     * unreviewable: the only way to see that the model heard "fairy tale" as
     * "whimsical" is to have both on the page. Blank for the built-in styles,
     * which were chosen rather than described.
     */
    @SerialName("style_request") val styleRequest: String = "",
    /**
     * The system instruction a custom style actually sends — [styleRequest]
     * expanded once, at creation, and stored.
     *
     * Written once rather than per call for two reasons. It is inspectable
     * and hand-editable, which is the whole local-first bet; and a one-line
     * wish re-interpreted freshly by every chapter call drifts, so chapter 12
     * would not be in the same voice as chapter 1. Blank for the built-in
     * styles, whose prompt lives in [StylePresets].
     */
    @SerialName("style_prompt") val stylePrompt: String = "",
    /**
     * One sentence describing the character or mark a custom style wants on
     * its blocks, written in the same pass as [stylePrompt].
     *
     * Badges branch on the style key (see BadgePrompt), so without this a
     * custom style falls to the friendly-cartoon arm — which is right for a
     * fairy tale and wrong for anything sober. Blank means "use the arm the
     * key picks", which is exactly what every pre-existing course wants.
     */
    @SerialName("style_mascot") val styleMascot: String = "",
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

/**
 * Where the reader is in THIS book, from the book's own record.
 *
 * It used to be answered by `last_read.json`, which is a single pointer at
 * the root of the library — so it could only ever be right about ONE book.
 * Open a second and the first lost its marker, though its own `course.json`
 * had known all along. A book's position belongs to the book.
 *
 * Read as "how far this book got": the furthest chapter still being read,
 * or failing that the furthest one with any progress recorded. Several
 * chapters can carry READING at once — jumping back to re-read chapter 2
 * leaves it READING — and with no timestamps per chapter the furthest is the
 * better guess at where the book is up to.
 *
 * Null for a book never opened. Nothing is marked in that case rather than
 * chapter 1 being marked hopefully: the same rule as the bookmark ribbon,
 * which is hidden when unset instead of shown as an empty outline.
 */
val Course.currentChapter: PersistedChapter?
    get() = chapters.filter { it.status == ChapterStatus.READING }.maxByOrNull { it.n }
        ?: chapters.filter { it.lastBlockIndex > 0 }.maxByOrNull { it.n }

/** A lightweight projection for the Library screen — avoids re-reading full chapter lists. */
data class CourseSummary(
    val slug: String,
    val title: String,
    val chapterCount: Int,
    val created: String,
    /** Chapters the reader has finished. */
    val chaptersDone: Int = 0,
    /** The chapter number this book is up to, or null if it was never opened. */
    val currentChapterN: Int? = null,
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
 *
 * WHAT SURVIVES IS "A LETTER OR A DIGIT IN ANY SCRIPT", not "a-z0-9". Written
 * the narrow way, a title with no Latin in it lost every character, reduced to
 * the empty string, and came out as the fallback — so every Russian book on
 * the shelf was called `course`, then `course-2`, and the slug is the folder
 * name, the backup's filename and the name inside an exported EPUB. A reader
 * whose language is not written in Latin got that on their very first book.
 *
 * Marks are kept along with letters because in Devanagari, Thai and others a
 * vowel sign is part of the word rather than punctuation; dropping them would
 * mangle the title instead of transliterating it.
 *
 * ASCII titles are unaffected — the two classes agree on every character an
 * English title contains — so no existing course moves.
 *
 * `lowercase()` with no argument is deliberate: Kotlin's no-arg form is
 * locale-independent, where Java's `toLowerCase()` would turn "AI" into "aı"
 * on a Turkish device.
 */
fun courseSlug(title: String): String = kebabOf(title).ifEmpty { "course" }

/**
 * The slug rule itself, shared with [chapterSlug] so the two cannot drift
 * apart — they already did once, and the fix had to be made twice.
 *
 * Returns "" for a title with no letters or digits in it at all; each caller
 * supplies its own fallback, because "course" and "chapter" are different
 * words.
 *
 * A slug can still never begin with "_": underscore is connector punctuation
 * rather than a letter, so it is replaced like any other separator and then
 * trimmed. [CourseArchive.SHELF] depends on that.
 */
internal fun kebabOf(title: String): String = title
    .lowercase()
    .replace(Regex("[^\\p{L}\\p{N}\\p{M}]+"), "-")
    .trim('-')
    .take(60)
    .trim('-')

/** Turns a fresh outline into the persisted structure. Metadata is captured at creation time. */
fun CourseOutline.toCourse(
    slug: String,
    topicPrompt: String,
    priorKnowledge: String,
    expectations: String,
    dailyMinutes: Int,
    style: String,
    styleRequest: String = "",
    stylePrompt: String = "",
    styleMascot: String = "",
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
    styleRequest = styleRequest,
    stylePrompt = stylePrompt,
    styleMascot = styleMascot,
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
