package com.inkfirst.app.ui

import com.inkfirst.core.CourseBackup
import com.inkfirst.core.FileTree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupPlanningTest {

    private fun entry(path: String, size: Long = 100, modified: Long = 1_000) =
        FileTree.Entry(path, size, modified)

    @Test
    fun `one course is picked out of a shelf by its directory`() {
        val shelf = listOf(
            entry("kotlin/course.json"),
            entry("kotlin/chapters/01-intro.md"),
            entry("gardening/course.json"),
        )

        val picked = entriesForCourse(shelf, "kotlin")

        assertEquals(listOf("kotlin/course.json", "kotlin/chapters/01-intro.md"), picked.map { it.path })
    }

    @Test
    fun `a slug is not a prefix of another course`() {
        // "kotlin" must not drag in "kotlin-advanced", or restoring one book
        // quietly overwrites a different one.
        val shelf = listOf(entry("kotlin/course.json"), entry("kotlin-advanced/course.json"))

        assertEquals(listOf("kotlin/course.json"), entriesForCourse(shelf, "kotlin").map { it.path })
    }

    @Test
    fun `a shelf describes itself in books, files and bytes`() {
        val shelf = listOf(
            entry("kotlin/course.json", size = 6_000),
            entry("kotlin/figures/01-cover.jpg", size = 2_000_000),
            entry("gardening/course.json", size = 4_000),
        )

        assertEquals("2 books · 3 files · 1.9 MB", describeShelf(shelf))
    }

    @Test
    fun `an empty shelf says so rather than counting zeroes`() {
        assertEquals("Nothing here yet.", describeShelf(emptyList()))
    }

    @Test
    fun `a plan says what moves and what does not`() {
        val plan = CourseBackup.plan(
            source = listOf(entry("kotlin/course.json", size = 6_000), entry("kotlin/memory.md", size = 2_000)),
            destination = listOf(entry("kotlin/memory.md", size = 2_000, modified = 5_000)),
        )

        assertEquals("1 file to copy, 6 KB · 1 file already there.", describePlan(plan))
    }

    @Test
    fun `an up-to-date backup does not read as an error`() {
        val same = listOf(entry("kotlin/course.json", modified = 5_000))
        val plan = CourseBackup.plan(source = same, destination = same)

        assertEquals("Already up to date — 1 file unchanged.", describePlan(plan))
    }

    @Test
    fun `a report names the files that failed, not just how many`() {
        val report = CourseBackup.Report(
            copied = 40,
            skipped = 2,
            bytes = 1_500_000,
            failed = listOf(
                CourseBackup.Failure("kotlin/chapters/03-loops.md", "no space"),
                CourseBackup.Failure("kotlin/figures/03-a.jpg", "no space"),
            ),
        )

        val text = describeReport(report)

        assertTrue(text.startsWith("Copied 40 files, 1.4 MB · 2 already there."), text)
        assertTrue(text.contains("kotlin/chapters/03-loops.md"), text)
        assertTrue(text.contains("kotlin/figures/03-a.jpg"), text)
    }

    @Test
    fun `a long list of failures is cut short rather than filling the screen`() {
        val failed = (1..9).map { CourseBackup.Failure("kotlin/figures/0$it.jpg", "no space") }
        val report = CourseBackup.Report(copied = 0, skipped = 0, bytes = 0, failed = failed)

        val text = describeReport(report)

        assertTrue(text.contains("and 6 more"), text)
    }

    @Test
    fun `sizes read as sizes`() {
        assertEquals("912 B", humanBytes(912))
        assertEquals("48 KB", humanBytes(49_000))
        assertEquals("14.8 MB", humanBytes(15_518_924))
    }
}
