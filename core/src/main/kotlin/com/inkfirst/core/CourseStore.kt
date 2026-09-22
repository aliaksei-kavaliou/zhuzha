package com.inkfirst.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
        units: String = DEFAULT_MEASUREMENT_SYSTEM,
        figures: String = DEFAULT_FIGURE_POLICY,
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
            units = units,
            figures = figures,
            created = created,
        )

        val courseDir = File(root, slug).apply { mkdirs() }
        File(courseDir, "chapters").mkdirs()
        File(courseDir, "notes").mkdirs()
        File(courseDir, "memory.md").writeText("")
        writeCourse(slug, course)
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

    /**
     * Sets a chapter's current body and Q&A transcript aside as
     * `chapters/<slug>.previous.md` and `notes/<slug>.previous.qa.md`,
     * replacing whatever was there before. Returns the chapter file's new
     * name, or null if there was nothing to set aside.
     *
     * Called before a rewrite. Nothing in the app reads these back. They
     * exist because the study material is meant to be plain inspectable files
     * that outlive the app (CLAUDE.md, decision 2), and because a chapter
     * someone read and annotated should not be unrecoverable after one
     * confirm dialog. Recovery is by hand — rename the file back.
     *
     * ONE generation deep, on purpose. A numbered chain (`.r1`, `.r2`, …)
     * implies a version history, and there is no way to navigate one: the app
     * offers no rollback and is not going to grow a revision browser. It also
     * fills a directory the reader is supposed to be able to browse with
     * files nobody can act on, which costs the very thing the archive is
     * defending. "Previous" is the whole of what anyone actually wants — a
     * rewrite that missed is noticed immediately, not five rewrites later.
     *
     * Cheap either way: a chapter is around 10 KB of Markdown, against about
     * 1.9 MB of inline figures that the same rewrite deletes. Rewriting
     * frees roughly two hundred times what this keeps.
     *
     * The transcript travels WITH the body, because it is only meaningful
     * next to the prose it quotes: every exchange in it names a passage, and
     * on its own those are quotations from a text nobody can read any more.
     */
    fun archiveChapter(courseSlug: String, chapterSlug: String): String? {
        val live = File(root, "$courseSlug/chapters/$chapterSlug.md")
            .takeIf { it.isFile } ?: return null
        val archived = File(root, "$courseSlug/chapters/$chapterSlug.previous.md")
        // renameTo will not replace an existing file on every filesystem.
        archived.delete()
        if (!live.renameTo(archived)) return null
        val qa = File(root, "$courseSlug/notes/$chapterSlug.qa.md")
        val qaArchived = File(root, "$courseSlug/notes/$chapterSlug.previous.qa.md")
        qaArchived.delete()
        if (qa.isFile) qa.renameTo(qaArchived)
        return archived.name
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

    /**
     * Replaces chapter [chapterN]'s block in memory.md, keeping its position
     * among the others — see [CourseMemory].
     *
     * Used when a chapter is rewritten. Appending the new summary instead
     * would leave the old one in the file, and memory.md is read whole by
     * every later chapter: the book would go on building on prose that no
     * longer exists.
     */
    fun replaceMemory(courseSlug: String, chapterN: Int, chapterTitle: String, summary: String) {
        val memoryFile = File(root, "$courseSlug/memory.md").apply { parentFile?.mkdirs() }
        val existing = if (memoryFile.exists()) memoryFile.readText() else ""
        memoryFile.writeText(CourseMemory.replaceChapter(existing, chapterN, chapterTitle, summary))
    }

    /**
     * Drops everything anchored by POSITION into one chapter's text —
     * annotations, bookmarks and ink — and the inline figures drawn for its
     * figure slots. For use immediately before rewriting that chapter.
     *
     * The rule is what a mark POINTS AT. An annotation names a block index in
     * prose that is about to be replaced, so it would land on an unrelated
     * paragraph; a figure is stored as `<chapter>-<ordinal>` and matched by
     * slot ordinal, so the new chapter's third figure slot would silently
     * show the old chapter's third picture.
     *
     * The chapter's reading history goes too — the Q&A transcript and this
     * chapter's section of reader.md. An earlier version of this kept the
     * transcript, on the theory that a conversation that really happened is a
     * record rather than a pointer. That was wrong twice over. Every exchange
     * in it quotes a PASSAGE, so it is a pointer like any other; and the
     * reader's question history is split across two files — answered
     * questions in the transcript, marked-without-asking in the annotations —
     * so keeping one and dropping the other left half a history behind,
     * chosen by nothing but which file it happened to live in.
     * [ReaderNotes.removeChapter] explains why reader.md cannot simply be
     * left alone either.
     *
     * The cover and closing pictures DO survive: they illustrate the
     * chapter's title and objectives, and those come from the outline, which
     * a rewrite does not touch.
     *
     * Nothing here is lost from disk when [archiveChapter] runs first, which
     * is what the rewrite path does — the chapter body and its transcript are
     * filed away under a revision number instead.
     *
     * Returns the updated Course, or null if the course is gone.
     */
    fun clearChapterMarks(courseSlug: String, chapterSlug: String): Course? {
        val course = load(courseSlug) ?: return null
        val updated = course.copy(
            annotations = course.annotations.filterNot { it.chapterSlug == chapterSlug },
            bookmarks = course.bookmarks.filterNot { it.chapterSlug == chapterSlug },
        )
        writeCourse(courseSlug, updated)
        File(root, "$courseSlug/ink/$chapterSlug.ink.json").delete()
        File(root, "$courseSlug/notes/$chapterSlug.qa.md").delete()
        deleteChapterFigures(courseSlug, chapterSlug)
        course.chapters.firstOrNull { it.slug == chapterSlug }?.let { chapter ->
            val notes = readReaderNotes(courseSlug)
            if (notes.isNotBlank()) {
                writeReaderNotes(courseSlug, ReaderNotes.removeChapter(notes, chapter.n))
            }
        }
        return updated
    }

    /**
     * Removes a chapter's inline figures, leaving its cover and closing
     * picture in place. Matches the `<chapterSlug>-<ordinal>.<ext>` names
     * written by [ChapterIllustrator] — and only those, so `-cover` and
     * `-tail` survive by not being numbers.
     */
    private fun deleteChapterFigures(courseSlug: String, chapterSlug: String) {
        val dir = File(root, "$courseSlug/figures")
        if (!dir.isDirectory) return
        dir.listFiles().orEmpty().forEach { file ->
            val suffix = file.nameWithoutExtension.removePrefix("$chapterSlug-")
            if (suffix != file.nameWithoutExtension && suffix.toIntOrNull() != null) {
                file.delete()
            }
        }
    }

    /** Reads memory.md, or empty string if never written. Never null — callers pass it as prompt input. */
    fun readMemory(courseSlug: String): String {
        val file = File(root, "$courseSlug/memory.md")
        return if (file.exists()) file.readText() else ""
    }

    /**
     * Reads reader.md — how the READING went, as opposed to what the book
     * taught. Empty string if never written; callers pass it as prompt input.
     */
    fun readReaderNotes(courseSlug: String): String {
        val file = File(root, "$courseSlug/reader.md")
        return if (file.exists()) file.readText() else ""
    }

    /**
     * Replaces reader.md wholesale — what the in-app editor writes after the
     * reader strikes an observation out.
     *
     * Deletes the file when nothing is left, so a course with every
     * observation crossed out is indistinguishable from one that never
     * recorded any. That is the right answer: the absence of the file is what
     * [buildChapterPrompt] already reads as "no reader notes".
     */
    fun writeReaderNotes(courseSlug: String, markdown: String) {
        val file = File(root, "$courseSlug/reader.md").apply { parentFile?.mkdirs() }
        if (markdown.isBlank()) file.delete() else file.writeText(markdown)
    }

    /**
     * Appends one chapter's reading observations to reader.md:
     *
     *     ## Chapter 5 — how the reading went
     *     <body>
     *
     * Returns false without writing if that chapter already has a section, so
     * the caller can run this on every compile without tracking what it has
     * already recorded.
     *
     * Deliberately a plain file the reader can open and edit, which matters
     * more here than anywhere else in the app: if the book is going to form
     * opinions about someone's understanding, they need to be able to read
     * them and cross one out. See docs/design.md §13.
     */
    fun appendReaderNotes(
        courseSlug: String,
        chapterN: Int,
        chapterTitle: String,
        body: String,
    ): Boolean {
        if (body.isBlank()) return false
        val file = File(root, "$courseSlug/reader.md").apply { parentFile?.mkdirs() }
        val existing = if (file.exists()) file.readText() else ""
        val heading = "## Chapter $chapterN —"
        if (existing.lineSequence().any { it.trimStart().startsWith(heading) }) return false
        val block = buildString {
            if (existing.isNotBlank() && !existing.endsWith("\n\n")) appendLine()
            appendLine("$heading how the reading went")
            appendLine("<!-- $chapterTitle -->")
            appendLine()
            appendLine(body.trim())
            appendLine()
        }
        file.writeText(existing + block)
        return true
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
        kind: String = "text",
        /** Full timestamp for ordering; see [Annotation.at]. */
        at: String = "",
    ): Course? {
        val course = load(courseSlug) ?: return null
        val updated = course.copy(
            annotations = course.annotations + Annotation(
                chapterSlug = chapterSlug,
                blockIndex = blockIndex,
                quote = quote,
                note = note,
                created = created,
                at = at,
                kind = kind,
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

    /**
     * The chapter's opening picture, or null if it was never drawn. Stored
     * beside the figures because it is one — it just has no fence in the
     * Markdown pointing at it, so it is addressed by chapter slug instead.
     */
    fun coverFile(courseSlug: String, chapterSlug: String): File? {
        val dir = File(root, "$courseSlug/figures")
        if (!dir.isDirectory) return null
        return dir.listFiles().orEmpty().firstOrNull {
            it.isFile && it.length() > 0L && it.nameWithoutExtension == "$chapterSlug-cover"
        }
    }

    /**
     * The chapter's closing picture — the cover's payoff — or null.
     */
    fun tailFile(courseSlug: String, chapterSlug: String): File? {
        val dir = File(root, "$courseSlug/figures")
        if (!dir.isDirectory) return null
        return dir.listFiles().orEmpty().firstOrNull {
            it.isFile && it.length() > 0L && it.nameWithoutExtension == "$chapterSlug-tail"
        }
    }

    /**
     * The scene brief the cover was drawn from, saved beside it.
     *
     * The closing picture is a rewrite of that one scene rather than a second
     * idea about the chapter, so it needs the original wording — the image
     * alone would let the model drift to a different metaphor.
     */
    fun saveCoverBrief(courseSlug: String, chapterSlug: String, brief: String) {
        if (brief.isBlank()) return
        val dir = File(root, "$courseSlug/figures").apply { mkdirs() }
        File(dir, "$chapterSlug-cover.txt").writeText(brief.trim())
    }

    fun readCoverBrief(courseSlug: String, chapterSlug: String): String {
        val f = File(root, "$courseSlug/figures/$chapterSlug-cover.txt")
        return if (f.isFile) f.readText().trim() else ""
    }

    /**
     * Badge kinds become file names, so they are held to plain letters — a
     * kind is a fence keyword from the chapter model, and nothing stops that
     * model from emitting one with a slash in it.
     */
    private val safeKind = Regex("""^[a-z0-9_-]{1,40}$""")

    /**
     * Writes a course badge to badges/<kind>.<ext>. Unlike a figure, a badge
     * is addressed by the block kind it decorates rather than by a name in
     * the chapter text — nothing in the Markdown points at it, so the kind
     * has to be the file name.
     */
    fun saveBadge(courseSlug: String, kind: String, extension: String, bytes: ByteArray) {
        if (!kind.matches(safeKind)) return
        val dir = File(root, "$courseSlug/badges").apply { mkdirs() }
        // Drop any earlier drawing of this kind first: the extension follows
        // whatever the model returned, so a redraw can land on a different
        // one and leave two files claiming the same badge.
        dir.listFiles().orEmpty()
            .filter { it.isFile && it.nameWithoutExtension == kind }
            .forEach { it.delete() }
        File(dir, "$kind.${extension.ifBlank { "png" }}").writeBytes(bytes)
    }

    /** The badge drawn for [kind], or null if this course has none. */
    fun badgeFile(courseSlug: String, kind: String): File? {
        if (!kind.matches(safeKind)) return null
        val dir = File(root, "$courseSlug/badges")
        if (!dir.isDirectory) return null
        return dir.listFiles().orEmpty()
            .firstOrNull { it.isFile && it.nameWithoutExtension == kind && it.length() > 0L }
    }

    /**
     * Block kinds that already have a badge on disk. Read from the directory
     * rather than filtered against a fixed list, because the set a course
     * needs is discovered from its chapters (see [badgeKindsIn]) and grows as
     * the book turns out to use new kinds of block.
     */
    fun badgeKinds(courseSlug: String): Set<String> {
        val dir = File(root, "$courseSlug/badges")
        if (!dir.isDirectory) return emptySet()
        return dir.listFiles().orEmpty()
            .filter { it.isFile && it.length() > 0L && it.extension.lowercase() != "txt" }
            .map { it.nameWithoutExtension }
            .toSet()
    }

    /**
     * The one-line description of the course mascot, written next to the
     * badges it describes. Kept as a plain file rather than a course.json
     * field: it is generated data that belongs with the pictures, and this
     * way an old course.json needs no migration.
     */
    fun readMascot(courseSlug: String): String {
        val f = File(root, "$courseSlug/badges/mascot.txt")
        return if (f.isFile) f.readText().trim() else ""
    }

    fun saveMascot(courseSlug: String, description: String) {
        if (description.isBlank()) return
        val dir = File(root, "$courseSlug/badges").apply { mkdirs() }
        File(dir, "mascot.txt").writeText(description.trim())
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

    /**
     * Removes a course and everything under it — chapters, notes, Q&A,
     * figures, badges, ink. Irreversible, and there is no trash to fish it
     * back out of: the files ARE the course.
     *
     * This is the only destructive operation in the store, so it is
     * deliberately fussy about what it will accept. The directory has to sit
     * directly under the courses root and it has to contain a course.json —
     * a recursive delete aimed at the wrong path is not a bug you get to
     * apologise for afterwards.
     *
     * Returns false when there was no such course to remove.
     */
    fun deleteCourse(slug: String): Boolean {
        val dir = File(root, slug)
        if (!dir.isDirectory) return false
        if (dir.canonicalFile.parentFile != root.canonicalFile) return false
        if (!File(dir, "course.json").exists()) return false
        val removed = dir.deleteRecursively()
        // Otherwise the next cold start reads this pointer and opens a book
        // that is no longer on disk.
        if (removed && readLastRead()?.courseSlug == slug) clearLastRead()
        return removed
    }

    /**
     * Writes course.json so that being killed mid-write cannot damage it.
     *
     * This used to write straight over the file, which truncates it first.
     * Two facts make that window dangerous rather than theoretical. The file
     * holds the OUTLINE — chapter titles and objectives, the one thing an API
     * call produced that cannot be reconstructed by re-reading anything — and
     * [updateChapterProgress] rewrites the whole file on every page turn, so
     * the window opened several times a minute while reading. Android killing
     * a backgrounded app mid-rename is ordinary on an e-reader, where the
     * usual way to stop reading is to shut the cover.
     *
     * The damage was also silent. [tryDecode] answers null rather than
     * throwing, and [list] drops a course it cannot decode — so a half-written
     * file did not show an error, it made the book VANISH from the Library
     * with its chapters and figures still sitting on disk.
     *
     * The fix is the standard one: write a sibling temp file, push it to the
     * physical disk, then rename it over the target. A rename within one
     * filesystem is atomic, so a reader sees either the whole old file or the
     * whole new one and never half of either. Android's own `AtomicFile` does
     * exactly this; it is spelled out by hand here because this module holds
     * no Android dependencies.
     */
    private fun writeCourse(courseSlug: String, course: Course) {
        val target = File(root, "$courseSlug/course.json")
        val temp = File(root, "$courseSlug/course.json.tmp")
        val bytes = json.encodeToString(Course.serializer(), course).toByteArray()

        // `use` closes the stream on the way out however the block ends —
        // Kotlin's try-with-resources.
        FileOutputStream(temp).use { out ->
            out.write(bytes)
            // Without this the bytes can still be sitting in the OS page cache
            // when the rename lands, and a power cut would publish an empty
            // file over a good one.
            out.fd.sync()
        }
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            // Not expected on the app's own storage — Android's SharedPreferences
            // and AtomicFile both rename on this path — but an unsupported
            // ATOMIC_MOVE would throw on EVERY page turn, which is a far worse
            // outcome than the problem being fixed. Without the flag the same
            // rename is still attempted first; it just stops being a promise.
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
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
