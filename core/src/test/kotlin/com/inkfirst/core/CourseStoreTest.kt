package com.inkfirst.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    // --- where a book is up to ---------------------------------------------

    @Test
    fun `each book knows its own position, whichever was opened last`(@TempDir tmp: File) {
        // The bug this replaces: "you are here" came from last_read.json, one
        // pointer for the whole library, so opening a second book erased the
        // first book's marker even though its course.json still knew.
        val store = CourseStore(tmp)
        store.createTest("kotlin")
        store.createTest("python")
        store.updateChapterStatus("kotlin", 2, ChapterStatus.READING)
        store.updateChapterStatus("python", 1, ChapterStatus.READING)
        store.writeLastRead("python", "01-intro")

        assertEquals(2, store.load("kotlin")?.currentChapter?.n)
        assertEquals(1, store.load("python")?.currentChapter?.n)
    }

    @Test
    fun `the furthest chapter being read is the one the book is up to`(@TempDir tmp: File) {
        // Going back to re-read chapter 1 leaves it READING too. With no
        // per-chapter timestamps, furthest is the better guess.
        val store = CourseStore(tmp)
        store.createTest("kotlin")
        store.updateChapterStatus("kotlin", 1, ChapterStatus.READING)
        store.updateChapterStatus("kotlin", 2, ChapterStatus.READING)

        assertEquals(2, store.load("kotlin")?.currentChapter?.n)
    }

    @Test
    fun `a finished chapter still says how far the book got`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("kotlin")
        store.updateChapterStatus("kotlin", 1, ChapterStatus.DONE)
        store.updateChapterProgress("kotlin", 1, 42)

        assertEquals(1, store.load("kotlin")?.currentChapter?.n)
    }

    @Test
    fun `a book never opened is not marked hopefully at chapter one`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("kotlin")

        assertNull(store.load("kotlin")?.currentChapter)
    }

    @Test
    fun `the shelf carries each book's progress`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("kotlin")
        store.createTest("python")
        store.updateChapterStatus("kotlin", 1, ChapterStatus.DONE)
        store.updateChapterStatus("kotlin", 2, ChapterStatus.READING)

        val shelf = store.list().associateBy { it.slug }

        assertEquals(1, shelf.getValue("kotlin").chaptersDone)
        assertEquals(2, shelf.getValue("kotlin").currentChapterN)
        assertEquals(0, shelf.getValue("python").chaptersDone)
        assertNull(shelf.getValue("python").currentChapterN)
    }

    // --- pictures ---------------------------------------------------------

    @Test
    fun `redrawing a cover takes its brief with it`(@TempDir tmp: File) {
        // The brief is what the CLOSING picture is written from, and a brief
        // written before the language reached the brief writer would keep
        // producing an English speech bubble however often it was redrawn.
        val store = CourseStore(tmp)
        store.createTest("kotlin")
        val figures = File(tmp, "kotlin/figures").apply { mkdirs() }
        File(figures, "01-intro-cover.jpg").writeBytes(byteArrayOf(1))
        File(figures, "01-intro-cover.txt").writeText("a brief in the wrong language")
        File(figures, "01-intro-tail.jpg").writeBytes(byteArrayOf(1))

        assertTrue(store.deleteChapterPicture("kotlin", "01-intro", closing = false))

        assertNull(store.coverFile("kotlin", "01-intro"))
        assertEquals("", store.readCoverBrief("kotlin", "01-intro"))
        // The closing picture is a separate decision and is left alone.
        assertNotNull(store.tailFile("kotlin", "01-intro"))
    }

    @Test
    fun `redrawing the closing picture leaves the cover alone`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("kotlin")
        val figures = File(tmp, "kotlin/figures").apply { mkdirs() }
        File(figures, "01-intro-cover.jpg").writeBytes(byteArrayOf(1))
        File(figures, "01-intro-cover.txt").writeText("keep me")
        File(figures, "01-intro-tail.jpg").writeBytes(byteArrayOf(1))

        assertTrue(store.deleteChapterPicture("kotlin", "01-intro", closing = true))

        assertNull(store.tailFile("kotlin", "01-intro"))
        assertNotNull(store.coverFile("kotlin", "01-intro"))
        assertEquals("keep me", store.readCoverBrief("kotlin", "01-intro"))
    }

    @Test
    fun `a slug that tries to leave the figures directory deletes nothing`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("kotlin")
        val figures = File(tmp, "kotlin/figures").apply { mkdirs() }
        File(figures, "01-intro-cover.jpg").writeBytes(byteArrayOf(1))

        assertFalse(store.deleteChapterPicture("kotlin", "../../01-intro", closing = false))
        assertFalse(store.deleteChapterPicture("kotlin/..", "01-intro", closing = false))
        assertNotNull(store.coverFile("kotlin", "01-intro"))
    }

    @Test
    fun `deleting a picture that was never drawn is not an error`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("kotlin")

        assertFalse(store.deleteChapterPicture("kotlin", "01-intro", closing = false))
    }

    @Test
    fun `a cover's saved brief is never mistaken for the cover`(@TempDir tmp: File) {
        // Both files share a base name — <slug>-cover.jpg and -cover.txt —
        // so a lookup by base name alone comes back with whichever the
        // filesystem listed first. That is a book whose cover loads on one
        // device and not on another.
        val store = CourseStore(tmp)
        val course = store.createTest("kotlin")
        val figures = File(tmp, "kotlin/figures").apply { mkdirs() }
        File(figures, "01-intro-cover.txt").writeText("a brief nobody can draw")
        File(figures, "01-intro-cover.jpg").writeBytes(byteArrayOf(1, 2, 3))

        val cover = store.coverFile(course.slug, "01-intro")

        assertEquals("01-intro-cover.jpg", cover?.name)
    }

    @Test
    fun `a book wears the first chapter picture it has`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        val course = store.createTest("kotlin")
        val figures = File(tmp, "kotlin/figures").apply { mkdirs() }
        // Chapter 1 has none yet; chapter 2 does. The book still has a face.
        File(figures, "02-null-cover.png").writeBytes(byteArrayOf(1))

        assertEquals("02-null-cover.png", store.bookCoverFile(course.slug)?.name)

        // Once chapter 1 is drawn it takes over, and the face stops moving.
        File(figures, "01-intro-cover.png").writeBytes(byteArrayOf(1))
        assertEquals("01-intro-cover.png", store.bookCoverFile(course.slug)?.name)
    }

    @Test
    fun `a book with nothing drawn has no face, rather than a broken one`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        val course = store.createTest("kotlin")

        assertNull(store.bookCoverFile(course.slug))
    }

    @Test
    fun `archiveChapter sets the old body aside`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("book")
        store.saveChapter("book", "01-intro", "first attempt")

        assertEquals("01-intro.previous.md", store.archiveChapter("book", "01-intro"))
        assertNull(store.readChapter("book", "01-intro"))
        assertEquals("first attempt", File(tmp, "book/chapters/01-intro.previous.md").readText())
    }

    @Test
    fun `archiving is one generation deep, not a chain`(@TempDir tmp: File) {
        // A numbered chain implies a version history, and nothing can
        // navigate one — the app offers no rollback. "Previous" is all anyone
        // wants: a rewrite that missed is noticed at once, not five later.
        val store = CourseStore(tmp)
        store.createTest("book")
        store.saveChapter("book", "01-intro", "first attempt")
        store.archiveChapter("book", "01-intro")
        store.saveChapter("book", "01-intro", "second attempt")
        store.archiveChapter("book", "01-intro")

        assertEquals("second attempt", File(tmp, "book/chapters/01-intro.previous.md").readText())
        assertEquals(
            listOf("01-intro.previous.md"),
            File(tmp, "book/chapters").list().orEmpty().sorted(),
        )
    }

    @Test
    fun `the chapter list comes from course_json, so an archive is never read as one`(
        @TempDir tmp: File,
    ) {
        // Nothing enumerates the chapters directory — a chapter is addressed
        // by the exact slug held in course.json, and slugs are kebab-case
        // with no dot in them. That is what keeps the archive inert rather
        // than any filtering on read.
        val store = CourseStore(tmp)
        store.createTest("book")
        store.saveChapter("book", "01-intro", "body")
        store.archiveChapter("book", "01-intro")

        assertNull(store.readChapter("book", "01-intro"))
        assertTrue(store.load("book")!!.chapters.none { it.slug.contains('.') })
    }

    @Test
    fun `archiveChapter on a chapter that was never written does nothing`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("book")
        assertNull(store.archiveChapter("book", "01-intro"))
    }

    // --- clearChapterMarks -----------------------------------------------

    @Test
    fun `clearChapterMarks drops what points into the chapter's prose`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("book")
        store.addAnnotation("book", "01-intro", 3, "a sentence", "my note", "2026-09-20")
        store.addBookmark("book", "01-intro", 5, "2026-09-20")
        store.saveStrokes(
            "book", "01-intro",
            listOf(Stroke(blockIndex = 2, points = emptyList(), created = "2026-09-20")),
        )

        store.clearChapterMarks("book", "01-intro")

        val after = store.load("book")!!
        assertTrue(after.annotations.isEmpty())
        assertTrue(after.bookmarks.isEmpty())
        assertTrue(store.readStrokes("book", "01-intro").isEmpty())
    }

    @Test
    fun `clearChapterMarks leaves the other chapters alone`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("book")
        store.addAnnotation("book", "01-intro", 1, "doomed", "n", "2026-09-20")
        store.addAnnotation("book", "02-null", 1, "spared", "n", "2026-09-20")
        store.addBookmark("book", "02-null", 4, "2026-09-20")

        store.clearChapterMarks("book", "01-intro")

        val after = store.load("book")!!
        assertEquals(listOf("spared"), after.annotations.map { it.quote })
        assertEquals(1, after.bookmarks.size)
    }

    @Test
    fun `clearChapterMarks takes the reading history with it`(@TempDir tmp: File) {
        // Every exchange in the transcript quotes a passage, so it is a
        // pointer like any other. Keeping it while dropping the marked-but-
        // never-asked annotations left half a history behind, chosen by
        // nothing but which file it happened to live in.
        val store = CourseStore(tmp)
        store.createTest("book")
        store.appendQa("book", "01-intro", "a passage", "what is this?", "this is that", "2026-09-20")
        store.appendReaderNotes("book", 1, "Why Kotlin feels weird", "Asked about:\n- On \"a passage\" — asked: what is this?")

        store.clearChapterMarks("book", "01-intro")

        assertEquals("", store.readQa("book", "01-intro"))
        assertTrue(ReaderNotes.parse(store.readReaderNotes("book")).none { it.chapterN == 1 })
    }

    @Test
    fun `clearChapterMarks leaves another chapter's reading history alone`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("book")
        store.appendQa("book", "02-null", "elsewhere", "and this?", "that", "2026-09-20")
        store.appendReaderNotes("book", 2, "Null safety", "Wrote these notes:\n- keep me")

        store.clearChapterMarks("book", "01-intro")

        assertTrue(store.readQa("book", "02-null").contains("and this?"))
        assertTrue(store.readReaderNotes("book").contains("keep me"))
    }

    @Test
    fun `a rewritten chapter can record its reading again`(@TempDir tmp: File) {
        // appendReaderNotes refuses a chapter it has already recorded, so a
        // section left behind would freeze the old reading in place forever.
        val store = CourseStore(tmp)
        store.createTest("book")
        store.appendReaderNotes("book", 1, "Why Kotlin feels weird", "- the old reading")

        store.clearChapterMarks("book", "01-intro")

        assertTrue(store.appendReaderNotes("book", 1, "Why Kotlin feels weird", "- the new reading"))
        assertTrue(store.readReaderNotes("book").contains("the new reading"))
    }

    @Test
    fun `archiveChapter files the transcript away beside the chapter`(@TempDir tmp: File) {
        // The transcript only means anything next to the prose it quotes, so
        // it travels under the same revision number.
        val store = CourseStore(tmp)
        store.createTest("book")
        store.saveChapter("book", "01-intro", "first attempt")
        store.appendQa("book", "01-intro", "a passage", "what is this?", "this is that", "2026-09-20")

        store.archiveChapter("book", "01-intro")

        assertEquals("", store.readQa("book", "01-intro"))
        assertTrue(
            File(tmp, "book/notes/01-intro.previous.qa.md").readText().contains("what is this?"),
        )
    }

    @Test
    fun `clearChapterMarks removes inline figures but keeps cover and tail`(@TempDir tmp: File) {
        // Inline figures are addressed by slot ORDINAL, so a stale one would
        // silently illustrate an unrelated paragraph in the new chapter. The
        // cover and tail are drawn from the title and objectives, which a
        // rewrite does not touch.
        val store = CourseStore(tmp)
        store.createTest("book")
        store.saveFigure("book", "01-intro-0.png", byteArrayOf(1))
        store.saveFigure("book", "01-intro-1.png", byteArrayOf(1))
        store.saveFigure("book", chapterCoverName("01-intro", "png"), byteArrayOf(1))
        store.saveFigure("book", chapterTailName("01-intro", "png"), byteArrayOf(1))
        store.saveFigure("book", "02-null-0.png", byteArrayOf(1))

        store.clearChapterMarks("book", "01-intro")

        assertNull(store.figureFile("book", "01-intro-0.png"))
        assertNull(store.figureFile("book", "01-intro-1.png"))
        assertNotNull(store.coverFile("book", "01-intro"))
        assertNotNull(store.tailFile("book", "01-intro"))
        assertNotNull(store.figureFile("book", "02-null-0.png"))
    }

    // --- deleteCourse ----------------------------------------------------
    //
    // The only destructive operation in the store. These are less about the
    // happy path than about what it REFUSES, because the failure mode is
    // "recursively deleted the wrong directory".

    @Test
    fun `deleteCourse removes the course and everything under it`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("doomed")
        store.saveChapter("doomed", "01-intro", "# body")
        store.appendQa("doomed", "01-intro", "passage", "q", "a", "2026-09-20")
        store.saveFigure("doomed", "fig.png", byteArrayOf(1, 2, 3))

        assertTrue(store.deleteCourse("doomed"))
        assertNull(store.load("doomed"))
        assertTrue(!File(tmp, "doomed").exists())
    }

    @Test
    fun `deleteCourse leaves the other books alone`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("keep")
        store.createTest("doomed")

        store.deleteCourse("doomed")

        assertEquals(listOf("keep"), store.list().map { it.slug })
    }

    @Test
    fun `deleteCourse clears the cold-start pointer only when it pointed there`(
        @TempDir tmp: File,
    ) {
        val store = CourseStore(tmp)
        store.createTest("keep")
        store.createTest("doomed")

        store.writeLastRead("doomed", "01-intro")
        store.deleteCourse("doomed")
        // Otherwise the next launch opens a book that is not on disk.
        assertNull(store.readLastRead())

        store.writeLastRead("keep", "01-intro")
        store.deleteCourse("keep-not-a-course")
        assertEquals("keep", store.readLastRead()?.courseSlug)
    }

    @Test
    fun `deleteCourse refuses anything that is not a course directory`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("real")
        File(tmp, "stray").mkdirs()
        File(tmp, "stray/notes.txt").writeText("not a course")

        assertTrue(!store.deleteCourse("stray"))
        assertTrue(!store.deleteCourse("never-existed"))
        assertTrue(File(tmp, "stray/notes.txt").exists())
    }

    @Test
    fun `deleteCourse refuses to climb out of the courses root`(@TempDir tmp: File) {
        val outside = File(tmp, "outside").apply { mkdirs() }
        File(outside, "course.json").writeText("{}")
        val root = File(tmp, "courses").apply { mkdirs() }
        val store = CourseStore(root)

        assertTrue(!store.deleteCourse("../outside"))
        assertTrue(File(outside, "course.json").exists())
    }

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
    fun `a custom style persists its wish, its brief and its mascot line`(@TempDir tmp: File) {
        val store = CourseStore(tmp)

        store.create(
            outline = sampleOutline,
            proposedSlug = "fairy",
            topicPrompt = "fractions",
            priorKnowledge = "",
            dailyMinutes = 20,
            style = StylePresets.CUSTOM_KEY,
            styleRequest = "written as a fairy tale",
            stylePrompt = "You are writing a chapter of a study book told as a fairy tale.",
            styleMascot = "a storybook animal in a cloak",
            model = "gemini-3.7-flash",
            created = "2026-09-24",
        )

        val parsed = Json.parseToJsonElement(File(tmp, "fairy/course.json").readText()).jsonObject
        assertEquals("custom", parsed["style"]?.jsonPrimitive?.content)
        assertEquals("written as a fairy tale", parsed["style_request"]?.jsonPrimitive?.content)
        assertTrue(parsed["style_prompt"]!!.jsonPrimitive.content.startsWith("You are writing"))

        // And it comes back as the instruction every generator will send.
        val reloaded = store.load("fairy")!!
        assertEquals(
            "You are writing a chapter of a study book told as a fairy tale.",
            StylePresets.forCourse(reloaded).systemPrompt,
        )
    }

    /**
     * Every course.json written before custom styles existed has none of the
     * three fields. They are defaulted rather than required precisely so that
     * those files keep loading — and keep loading as the books they already
     * are, which for a built-in key means the style fields are ignored.
     */
    @Test
    fun `a course_json from before custom styles still loads`(@TempDir tmp: File) {
        val dir = File(tmp, "old").apply { mkdirs() }
        File(dir, "course.json").writeText(
            """
            {
              "version": 1, "title": "Old Book", "slug": "old",
              "topic_prompt": "t", "prior_knowledge": "", "daily_minutes": 30,
              "style": "textbook", "model": "gemini-3.7-flash",
              "created": "2026-08-01", "chapters": []
            }
            """.trimIndent(),
        )

        val course = CourseStore(tmp).load("old")!!
        assertEquals("", course.styleRequest)
        assertEquals("", course.stylePrompt)
        assertEquals(StylePresets.TEXTBOOK.systemPrompt, StylePresets.forCourse(course).systemPrompt)
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
    fun `a title in another script keeps its words`() {
        // It used to keep none of them: the class was [^a-z0-9], so a Cyrillic
        // title lost every character, fell through to the fallback, and every
        // Russian book on the shelf was called "course". The slug is the
        // folder name, the backup filename and the name inside an EPUB.
        assertEquals(
            "учимся-играть-на-укулеле-с-нуля-простое-руководство",
            courseSlug("Учимся играть на укулеле с нуля: простое руководство"),
        )
        assertEquals("日本語入門", courseSlug("日本語入門"))
        assertEquals("ελληνικά-για-αρχάριους", courseSlug("Ελληνικά για αρχάριους"))
    }

    @Test
    fun `a vowel sign is part of the word, not punctuation`() {
        // Devanagari and Thai write vowels as combining marks. Dropping them
        // would not shorten the slug, it would misspell it.
        assertEquals("हिंदी-सीखें", courseSlug("हिंदी सीखें"))
    }

    @Test
    fun `mixed scripts and digits survive together`() {
        assertEquals("python-для-начинающих-3", courseSlug("Python для начинающих 3"))
    }

    @Test
    fun `an ascii title slugs exactly as it always did`() {
        // The widening must move no existing course: a slug is stored in
        // course.json AND is the directory name on disk.
        assertEquals("kotlin-for-python-developers", courseSlug("Kotlin for Python Developers!"))
        assertEquals("c-and-c-plus-plus", courseSlug("C and C++ ... plus plus"))
        assertEquals("course", courseSlug("!!! ??? ---"))
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
    fun `saveStrokes and readStrokes round-trip, empty list deletes the file`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("kotlin")

        assertTrue(store.readStrokes("kotlin", "01-intro").isEmpty())

        val strokes = listOf(
            Stroke(
                blockIndex = 3,
                points = listOf(InkPoint(1f, 2f), InkPoint(3f, 4f, 0.7f, 10L)),
                created = "2026-09-03",
            ),
            Stroke(blockIndex = 5, points = listOf(InkPoint(10f, 20f)), created = "2026-09-03"),
        )
        store.saveStrokes("kotlin", "01-intro", strokes)

        val read = store.readStrokes("kotlin", "01-intro")
        assertEquals(2, read.size)
        assertEquals(3, read[0].blockIndex)
        assertEquals(2, read[0].points.size)
        assertEquals(0.7f, read[0].points[1].pressure)
        assertEquals(setOf("01-intro"), store.chaptersWithInk("kotlin"))

        store.saveStrokes("kotlin", "01-intro", emptyList())
        assertTrue(store.readStrokes("kotlin", "01-intro").isEmpty())
        assertTrue(!File(tmp, "kotlin/ink/01-intro.ink.json").exists())
    }

    @Test
    fun `addAnnotation appends and removeAnnotation deletes by key`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        store.createTest("kotlin")

        val a1 = store.addAnnotation(
            "kotlin", "01-intro", 3, "quote one", "note one", "2026-09-03",
        )!!
        assertEquals(1, a1.annotations.size)
        val a2 = store.addAnnotation(
            "kotlin", "01-intro", 5, "quote two", "note two", "2026-09-03",
        )!!
        assertEquals(2, a2.annotations.size)

        // Persisted.
        val reloaded = store.load("kotlin")!!
        assertEquals(2, reloaded.annotations.size)
        assertEquals("quote one", reloaded.annotations[0].quote)
        assertEquals("note two", reloaded.annotations[1].note)

        val after = store.removeAnnotation("kotlin", "01-intro", "quote one", "2026-09-03")!!
        assertEquals(1, after.annotations.size)
        assertEquals("quote two", after.annotations[0].quote)

        // Non-matching remove is a no-op (course still returned).
        val same = store.removeAnnotation("kotlin", "01-intro", "nope", "2026-01-01")!!
        assertEquals(1, same.annotations.size)
    }

    @Test
    fun `writeLastRead, readLastRead, clearLastRead round-trip`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        assertNull(store.readLastRead())

        store.writeLastRead("kotlin", "01-intro")
        val read = store.readLastRead()!!
        assertEquals("kotlin", read.courseSlug)
        assertEquals("01-intro", read.chapterSlug)

        // Overwrite is fine — later chapter opens replace the pointer.
        store.writeLastRead("kotlin", "02-null")
        assertEquals("02-null", store.readLastRead()!!.chapterSlug)

        store.clearLastRead()
        assertNull(store.readLastRead())
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

    @Test
    fun `writing the course leaves no temp file behind`(@TempDir tmp: File) {
        // course.json is rewritten on every page turn and holds the outline,
        // so it is written temp-then-rename. The rename must consume the temp
        // file rather than leaving one beside it for the backup to copy.
        val store = CourseStore(tmp)
        store.createTest("kotlin")
        store.updateChapterProgress("kotlin", 1, 12)
        store.updateChapterProgress("kotlin", 1, 34)

        assertFalse(File(tmp, "kotlin/course.json.tmp").exists())
        assertEquals(34, store.load("kotlin")!!.chapters[0].lastBlockIndex)
    }

    @Test
    fun `a stale temp file from an earlier crash does not block the next write`(@TempDir tmp: File) {
        // Being killed between writing the temp file and the rename is
        // exactly the case this design exists for. What it must not do is
        // wedge every later save.
        val store = CourseStore(tmp)
        store.createTest("kotlin")
        File(tmp, "kotlin/course.json.tmp").writeText("half a file, from a kill mid-write")

        val after = store.updateChapterProgress("kotlin", 2, 7)

        assertNotNull(after)
        assertEquals(7, store.load("kotlin")!!.chapters[1].lastBlockIndex)
        assertFalse(File(tmp, "kotlin/course.json.tmp").exists())
    }

    @Test
    fun `an interrupted write cannot leave course json undecodable`(@TempDir tmp: File) {
        // The failure this replaces was silent: a truncated course.json made
        // tryDecode answer null, list() drop the course, and the book vanish
        // from the Library with its chapters still on disk. Standing in for
        // the kill: whatever is at course.json is always a whole document,
        // because a half-written one only ever exists under the temp name.
        val store = CourseStore(tmp)
        store.createTest("kotlin")
        repeat(20) { store.updateChapterProgress("kotlin", 1, it) }

        assertNotNull(store.load("kotlin"))
        assertEquals(1, store.list().size)
    }
}
