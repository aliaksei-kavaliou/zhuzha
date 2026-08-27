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
        expectations = "goals",
        dailyMinutes = 30,
        style = "headfirst-inspired",
        model = "gemini-3.7-flash",
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
            expectations = "write a small backend service",
            dailyMinutes = 45,
            style = "headfirst-inspired",
            model = "gemini-3.7-flash",
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

    @Test
    fun `saveChapter and readChapter round-trip`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("kotlin")
        assertNull(store.readChapter("kotlin", "01-intro"), "missing chapter reads as null")
        store.saveChapter("kotlin", "01-intro", "# Chapter 1\n\nHello.")
        assertEquals("# Chapter 1\n\nHello.", store.readChapter("kotlin", "01-intro"))

        val file = File(tmp, "kotlin/chapters/01-intro.md")
        assertTrue(file.exists())
    }

    @Test
    fun `appendMemory writes delimited blocks, preserves prior content`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("kotlin")

        store.appendMemory("kotlin", 1, "Intro", "Introduced Kotlin using the PHP-analogue lens.")
        store.appendMemory("kotlin", 2, "Nulls", "Contrasted T? with PHP's ?? default.")

        val memory = File(tmp, "kotlin/memory.md").readText()
        assertTrue(memory.contains("--- Chapter 1: Intro ---"))
        assertTrue(memory.contains("PHP-analogue lens"))
        assertTrue(memory.contains("--- Chapter 2: Nulls ---"))
        assertTrue(memory.contains("PHP's ?? default"))
        // Chapter 1's block still comes before chapter 2's.
        assertTrue(memory.indexOf("Chapter 1") < memory.indexOf("Chapter 2"))
    }

    @Test
    fun `readMemory returns empty string when memory_md is missing`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("kotlin")
        // create() wrote an empty memory.md — delete it to simulate a legacy course.
        File(tmp, "kotlin/memory.md").delete()
        assertEquals("", store.readMemory("kotlin"))
    }

    @Test
    fun `updateChapterStatus flips the target chapter and leaves others alone`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("kotlin")

        val updated = store.updateChapterStatus("kotlin", 1, ChapterStatus.GENERATED)
        assertNotNull(updated)
        assertEquals(ChapterStatus.GENERATED, updated!!.chapters.first { it.n == 1 }.status)
        assertEquals(ChapterStatus.PENDING, updated.chapters.first { it.n == 2 }.status)

        // Persisted on disk too.
        val reloaded = store.load("kotlin")!!
        assertEquals(ChapterStatus.GENERATED, reloaded.chapters.first { it.n == 1 }.status)
    }

    @Test
    fun `updateChapterStatus returns null for missing course`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        assertNull(store.updateChapterStatus("no-such-course", 1, ChapterStatus.DONE))
    }

    @Test
    fun `addBookmark persists and is idempotent`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("kotlin")

        val after1 = store.addBookmark("kotlin", "01-intro", 5, "2026-08-26")!!
        assertEquals(1, after1.bookmarks.size)
        assertEquals("01-intro", after1.bookmarks[0].chapterSlug)
        assertEquals(5, after1.bookmarks[0].blockIndex)

        // Same (chapter, block) again → no duplicate.
        val after2 = store.addBookmark("kotlin", "01-intro", 5, "2026-08-27")!!
        assertEquals(1, after2.bookmarks.size)

        // Different block → separate bookmark.
        val after3 = store.addBookmark("kotlin", "01-intro", 12, "2026-08-27")!!
        assertEquals(2, after3.bookmarks.size)

        // Reloading from disk still sees them.
        val reloaded = store.load("kotlin")!!
        assertEquals(2, reloaded.bookmarks.size)
    }

    @Test
    fun `removeBookmark deletes matching entry only`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("kotlin")
        store.addBookmark("kotlin", "01-intro", 3, "2026-08-26")
        store.addBookmark("kotlin", "02-null", 7, "2026-08-26")

        val after = store.removeBookmark("kotlin", "01-intro", 3)!!
        assertEquals(1, after.bookmarks.size)
        assertEquals("02-null", after.bookmarks[0].chapterSlug)

        // Removing a missing bookmark is a no-op (course still returned).
        val same = store.removeBookmark("kotlin", "nowhere", 99)!!
        assertEquals(1, same.bookmarks.size)
    }
}
