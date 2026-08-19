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
     * Flips the status field on chapter [chapterN] of the given course.
     * Rewrites the whole course.json (small file, atomicity below single-record
     * granularity isn't worth the ceremony here).
     */
    fun updateChapterStatus(courseSlug: String, chapterN: Int, status: ChapterStatus): Course? {
        val course = load(courseSlug) ?: return null
        val updated = course.copy(
            chapters = course.chapters.map { if (it.n == chapterN) it.copy(status = status) else it },
        )
        File(root, "$courseSlug/course.json")
            .writeText(json.encodeToString(Course.serializer(), updated))
        return updated
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
