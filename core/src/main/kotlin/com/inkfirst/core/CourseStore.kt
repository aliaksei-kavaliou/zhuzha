package com.inkfirst.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-disk course storage. See docs/design.md §1 for the layout:
 *
 *     <root>/<course-slug>/course.json
 *                          memory.md
 *                          chapters/
 *                          notes/
 *
 * `:app` constructs one of these pointing at getExternalFilesDir(null)/courses.
 * The store never touches Android APIs — everything is plain java.io.File.
 */
class CourseStore(private val root: File) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Persists an outline as a new course. If [proposedSlug] already exists
     * on disk, tries "-2", "-3" etc. until a free directory is found.
     */
    fun create(
        outline: CourseOutline,
        proposedSlug: String,
        topicPrompt: String,
        priorKnowledge: String,
        expectations: String = "",
        dailyMinutes: Int,
        style: String,
        model: String,
        language: String = "English",
        created: String,
    ): Course {
        root.mkdirs()
        val slug = nextFreeSlug(proposedSlug)

        val course = outline.toCourse(
            slug = slug,
            topicPrompt = topicPrompt,
            priorKnowledge = priorKnowledge,
            expectations = expectations,
            dailyMinutes = dailyMinutes,
            style = style,
            model = model,
            language = language,
            created = created,
        )

        val courseDir = File(root, slug).apply { mkdirs() }
        File(courseDir, "chapters").mkdirs()
        File(courseDir, "notes").mkdirs()
        File(courseDir, "memory.md").writeText("")
        File(courseDir, "course.json").writeText(json.encodeToString(Course.serializer(), course))
        return course
    }

    fun list(): List<CourseSummary> {
        if (!root.exists()) return emptyList()
        return root.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { dir ->
                val file = File(dir, "course.json").takeIf { it.exists() } ?: return@mapNotNull null
                val course = tryDecode(file) ?: return@mapNotNull null
                CourseSummary(
                    slug = course.slug,
                    title = course.title,
                    chapterCount = course.chapters.size,
                    created = course.created,
                )
            }
            .sortedByDescending { it.created }
    }

    fun load(slug: String): Course? {
        val file = File(root, "$slug/course.json").takeIf { it.exists() } ?: return null
        return tryDecode(file)
    }

    /** Writes chapters/&lt;chapterSlug&gt;.md. Overwrites any prior body. */
    fun saveChapter(courseSlug: String, chapterSlug: String, markdown: String) {
        val chaptersDir = File(root, "$courseSlug/chapters").apply { mkdirs() }
        File(chaptersDir, "$chapterSlug.md").writeText(markdown)
    }

    /** Reads chapters/&lt;chapterSlug&gt;.md, or null if it hasn't been generated yet. */
    fun readChapter(courseSlug: String, chapterSlug: String): String? {
        val file = File(root, "$courseSlug/chapters/$chapterSlug.md")
        return if (file.exists()) file.readText() else null
    }

    /**
     * Appends a chapter summary to memory.md. Each block is prefixed with a
     * delimiter so future readers (human or model) can tell chapters apart:
     *
     *     --- Chapter 1: What Even Is an AI? ---
     *     <summary>
     *
     * Called after ChapterGenerator + MemoryUpdater — see docs/design.md §3.
     */
    fun appendMemory(courseSlug: String, chapterN: Int, chapterTitle: String, summary: String) {
        val memoryFile = File(root, "$courseSlug/memory.md").apply { parentFile?.mkdirs() }
        val existing = if (memoryFile.exists()) memoryFile.readText() else ""
        val block = buildString {
            if (existing.isNotBlank() && !existing.endsWith("\n\n")) appendLine()
            appendLine("--- Chapter $chapterN: $chapterTitle ---")
            appendLine(summary.trim())
            appendLine()
        }
        memoryFile.writeText(existing + block)
    }

    /** Reads memory.md, or empty string if never written. Never null — callers pass it as prompt input. */
    fun readMemory(courseSlug: String): String {
        val file = File(root, "$courseSlug/memory.md")
        return if (file.exists()) file.readText() else ""
    }

    /**
     * Persists the reader's current position in [chapterN] as a block index
     * (see PersistedChapter.lastBlockIndex — block index, not page number).
     * Cheap: rewrites the small course.json.
     */
    fun updateChapterProgress(courseSlug: String, chapterN: Int, blockIndex: Int): Course? {
        val course = load(courseSlug) ?: return null
        val updated = course.copy(
            chapters = course.chapters.map {
                if (it.n == chapterN) it.copy(lastBlockIndex = blockIndex) else it
            },
        )
        writeCourse(courseSlug, updated)
        return updated
    }

    /**
     * Flips the status field on chapter [chapterN] of the given course.
     * Rewrites the whole course.json (small file, atomicity below single-record
     * granularity isn't worth the ceremony here).
     */
    fun updateChapterStatus(courseSlug: String, chapterN: Int, status: ChapterStatus): Course? {
        val course = load(courseSlug) ?: return null
        val updated = course.copy(
            chapters = course.chapters.map { if (it.n == chapterN) it.copy(status = status) else it },
        )
        writeCourse(courseSlug, updated)
        return updated
    }

    /**
     * Adds a bookmark for [blockIndex] in [chapterSlug]. Idempotent — if a
     * bookmark for the same (chapter, block) already exists, the existing
     * one wins and the course is returned unchanged.
     */
    fun addBookmark(courseSlug: String, chapterSlug: String, blockIndex: Int, created: String): Course? {
        val course = load(courseSlug) ?: return null
        val already = course.bookmarks.any { it.chapterSlug == chapterSlug && it.blockIndex == blockIndex }
        if (already) return course
        val updated = course.copy(
            bookmarks = course.bookmarks + Bookmark(chapterSlug, blockIndex, created),
        )
        writeCourse(courseSlug, updated)
        return updated
    }

    fun removeBookmark(courseSlug: String, chapterSlug: String, blockIndex: Int): Course? {
        val course = load(courseSlug) ?: return null
        val updated = course.copy(
            bookmarks = course.bookmarks.filterNot {
                it.chapterSlug == chapterSlug && it.blockIndex == blockIndex
            },
        )
        writeCourse(courseSlug, updated)
        return updated
    }

    /**
     * Appends a Q&A entry to notes/<chapterSlug>.qa.md. Format:
     *
     *     ## 2026-09-01
     *
     *     > selected passage (or omitted if none)
     *
     *     **Q:** the question
     *
     *     **A:** the answer
     *
     *     ---
     *
     * Human-readable Markdown; the ChatPanel reloads the raw file to render
     * history. Each new entry is separated by a horizontal rule.
     */
    fun appendQa(
        courseSlug: String,
        chapterSlug: String,
        selection: String?,
        question: String,
        answer: String,
        created: String,
    ) {
        val notesDir = File(root, "$courseSlug/notes").apply { mkdirs() }
        val file = File(notesDir, "$chapterSlug.qa.md")
        val block = buildString {
            appendLine("## $created")
            appendLine()
            if (!selection.isNullOrBlank()) {
                selection.trim().lines().forEach { appendLine("> $it") }
                appendLine()
            }
            appendLine("**Q:** ${question.trim()}")
            appendLine()
            appendLine("**A:** ${answer.trim()}")
            appendLine()
            appendLine("---")
            appendLine()
        }
        val existing = if (file.exists()) file.readText() else ""
        val prefix = if (existing.isBlank() || existing.endsWith("\n\n")) existing else "$existing\n"
        file.writeText(prefix + block)
    }

    /** Returns raw markdown of the saved Q&A history, or empty if none saved yet. */
    fun readQa(courseSlug: String, chapterSlug: String): String {
        val file = File(root, "$courseSlug/notes/$chapterSlug.qa.md")
        return if (file.exists()) file.readText() else ""
    }

    /**
     * Adds a passage-anchored annotation to the course. Idempotency on
     * (chapter, quote, note) is intentionally weak — a user re-writing the
     * same note on the same passage creates two entries; timestamps disambiguate.
     */
    fun addAnnotation(
        courseSlug: String,
        chapterSlug: String,
        blockIndex: Int,
        quote: String,
        note: String,
        created: String,
    ): Course? {
        val course = load(courseSlug) ?: return null
        val updated = course.copy(
            annotations = course.annotations + Annotation(
                chapterSlug = chapterSlug,
                blockIndex = blockIndex,
                quote = quote,
                note = note,
                created = created,
            ),
        )
        writeCourse(courseSlug, updated)
        return updated
    }

    /**
     * Persists the full set of freehand strokes for a chapter as
     * ink/<chapterSlug>.ink.json. Overwrites — the reader keeps the working
     * set in memory and writes the complete list on close. An empty list
     * deletes the file so unused chapters don't leave stubs.
     */
    fun saveStrokes(courseSlug: String, chapterSlug: String, strokes: List<Stroke>) {
        val dir = File(root, "$courseSlug/ink").apply { mkdirs() }
        val file = File(dir, "$chapterSlug.ink.json")
        if (strokes.isEmpty()) {
            file.delete()
        } else {
            file.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Stroke.serializer()), strokes))
        }
    }

    /** Reads the freehand strokes for a chapter, or empty if none saved yet. */
    fun readStrokes(courseSlug: String, chapterSlug: String): List<Stroke> {
        val file = File(root, "$courseSlug/ink/$chapterSlug.ink.json")
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Stroke.serializer()), file.readText())
        } catch (_: SerializationException) {
            emptyList()
        }
    }

    /**
     * Writes a generated figure to figures/<fileName>. The name is chosen by
     * [ChapterIllustrator] and written into the chapter's fence as `src=`, so
     * a picture is always reachable from the chapter text that references it.
     */
    fun saveFigure(courseSlug: String, fileName: String, bytes: ByteArray) {
        val dir = File(root, "$courseSlug/figures").apply { mkdirs() }
        File(dir, fileName).writeBytes(bytes)
    }

    /**
     * Resolves a figure `src=` to a file on disk, or null if it was never
     * generated (or was deleted by hand — the reader falls back to alt text).
     * Guards against a `src` containing path separators.
     */
    fun figureFile(courseSlug: String, fileName: String): File? {
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) return null
        return File(root, "$courseSlug/figures/$fileName").takeIf { it.isFile && it.length() > 0L }
    }

    /** Chapter slugs that currently have at least one ink stroke on file. */
    fun chaptersWithInk(courseSlug: String): Set<String> {
        val dir = File(root, "$courseSlug/ink")
        if (!dir.isDirectory) return emptySet()
        return dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".ink.json") && it.length() > 0L }
            .map { it.name.removeSuffix(".ink.json") }
            .toSet()
    }

    /**
     * Removes the annotation matching the given key (chapter + quote + created).
     * (chapter, created) is unique in practice — two annotations added in the
     * same second by the same user on the same passage is not a concern; but
     * we include quote to avoid ambiguity if we ever migrate `created` to
     * lower resolution.
     */
    fun removeAnnotation(
        courseSlug: String,
        chapterSlug: String,
        quote: String,
        created: String,
    ): Course? {
        val course = load(courseSlug) ?: return null
        val updated = course.copy(
            annotations = course.annotations.filterNot {
                it.chapterSlug == chapterSlug && it.quote == quote && it.created == created
            },
        )
        writeCourse(courseSlug, updated)
        return updated
    }

    /**
     * Persists the "last chapter the user opened" pointer at
     * <root>/last_read.json. Cold starts read this to jump straight into the
     * Reader on the last-open chapter.
     */
    fun writeLastRead(courseSlug: String, chapterSlug: String) {
        root.mkdirs()
        val pointer = LastRead(courseSlug = courseSlug, chapterSlug = chapterSlug)
        File(root, "last_read.json").writeText(json.encodeToString(LastRead.serializer(), pointer))
    }

    /** Returns the last-read pointer if present and parseable, else null. */
    fun readLastRead(): LastRead? {
        val file = File(root, "last_read.json").takeIf { it.exists() } ?: return null
        return try {
            json.decodeFromString(LastRead.serializer(), file.readText())
        } catch (_: SerializationException) {
            null
        }
    }

    /** Removes the pointer (used when it references a course/chapter that no longer exists). */
    fun clearLastRead() {
        File(root, "last_read.json").delete()
    }

    private fun writeCourse(courseSlug: String, course: Course) {
        File(root, "$courseSlug/course.json")
            .writeText(json.encodeToString(Course.serializer(), course))
    }

    private fun tryDecode(file: File): Course? = try {
        json.decodeFromString(Course.serializer(), file.readText())
    } catch (_: SerializationException) {
        null
    }

    private fun nextFreeSlug(base: String): String {
        if (!File(root, base).exists()) return base
        var n = 2
        while (File(root, "$base-$n").exists()) n++
        return "$base-$n"
    }
}
