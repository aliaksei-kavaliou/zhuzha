package com.inkfirst.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CourseStoreTest {

    private val sampleOutline = CourseOutline(
        title = "Kotlin for Python Developers",
        chapters = listOf(
            ChapterSpec(1, "01-intro", "Why Kotlin feels weird", listOf("understand", "practice"), 40),
            ChapterSpec(2, "02-null", "Null safety", listOf("optional-vs-nullable"), 35),
        ),
    )

    private fun CourseStore.createTest(
        slug: String,
        created: String = "2026-08-14",
    ): Course = create(
        outline = sampleOutline,
        proposedSlug = slug,
        topicPrompt = "topic",
        priorKnowledge = "prior",
        dailyMinutes = 30,
        style = "headfirst-inspired",
        model = "gemini-2.5-flash",
        created = created,
    )

    @Test
    fun `create persists course_json with snake_case fields and returns the Course`(@TempDir tmp: File) {
        val store = CourseStore(tmp)

        val course = store.create(
            outline = sampleOutline,
            proposedSlug = "kotlin-for-python-devs",
            topicPrompt = "kotlin for a python dev",
            priorKnowledge = "10 years python",
            dailyMinutes = 45,
            style = "headfirst-inspired",
            model = "gemini-2.5-flash",
            created = "2026-08-14",
        )

        assertEquals("kotlin-for-python-devs", course.slug)
        assertEquals("Kotlin for Python Developers", course.title)
        assertEquals(2, course.chapters.size)
        assertEquals(ChapterStatus.PENDING, course.chapters.first().status)

        val jsonFile = File(tmp, "kotlin-for-python-devs/course.json")
        assertTrue(jsonFile.exists(), "course.json should be written")

        val parsed = Json.parseToJsonElement(jsonFile.readText()).jsonObject
        assertEquals("2026-08-14", parsed["created"]?.jsonPrimitive?.content)
        assertEquals(45, parsed["daily_minutes"]?.jsonPrimitive?.content?.toInt())
        assertEquals("headfirst-inspired", parsed["style"]?.jsonPrimitive?.content)

        assertTrue(File(tmp, "kotlin-for-python-devs/chapters").isDirectory)
        assertTrue(File(tmp, "kotlin-for-python-devs/notes").isDirectory)
        assertTrue(File(tmp, "kotlin-for-python-devs/memory.md").exists())
    }

    @Test
    fun `slug collision appends -2 -3 etc`(@TempDir tmp: File) {
        val store = CourseStore(tmp)

        val a = store.createTest("kotlin")
        val b = store.createTest("kotlin")
        val c = store.createTest("kotlin")

        assertEquals("kotlin", a.slug)
        assertEquals("kotlin-2", b.slug)
        assertEquals("kotlin-3", c.slug)
        assertTrue(File(tmp, "kotlin/course.json").exists())
        assertTrue(File(tmp, "kotlin-2/course.json").exists())
        assertTrue(File(tmp, "kotlin-3/course.json").exists())
    }

    @Test
    fun `list returns summaries sorted newest-first`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("alpha", created = "2026-08-10")
        store.createTest("bravo", created = "2026-08-14")
        store.createTest("charlie", created = "2026-08-12")

        val summaries = store.list()
        assertEquals(listOf("bravo", "charlie", "alpha"), summaries.map { it.slug })
        assertEquals(2, summaries.first().chapterCount)
    }

    @Test
    fun `load returns null for missing slug and Course for present`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("kotlin")

        val hit = store.load("kotlin")
        assertNotNull(hit)
        assertEquals("Kotlin for Python Developers", hit!!.title)

        assertNull(store.load("nope"))
    }

    @Test
    fun `list skips subdirectories without course_json`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        File(tmp, "not-a-course").mkdirs()
        store.createTest("kotlin")

        val summaries = store.list()
        assertEquals(listOf("kotlin"), summaries.map { it.slug })
    }

    @Test
    fun `courseSlug produces filesystem-safe kebab-case`() {
        assertEquals("kotlin-for-python-developers", courseSlug("Kotlin for Python Developers!"))
        assertEquals("rust-basics", courseSlug("   Rust   Basics   "))
        assertEquals("course", courseSlug("!!!"))
    }
}
