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
