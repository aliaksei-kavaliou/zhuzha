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
    @Test
    fun `a backup describes itself in books, whether packed or loose`() {
        val folder = listOf(
            entry("kotlin.zip", size = 2_000_000),
            entry("_shelf.zip", size = 1_000),
            // A book an older version left as loose files.
            entry("gardening/course.json", size = 6_000),
            entry("gardening/chapters/01.md", size = 4_000),
        )

        assertEquals(listOf("gardening", "kotlin"), booksInBackup(folder))
        // Books and bytes, no file count: one zip per book means the file
        // count would be counting archives, which tells the reader nothing.
        assertEquals("2 books · 1.9 MB", describeBackup(folder))
    }

    @Test
    fun `only a book that is in the folder twice is called deletable`() {
        // The loose folder beside a zip is a leftover. The loose folder with
        // no zip beside it is a book that is not on the device any more —
        // saying "you can delete these" about that one destroys the only copy.
        val folder = listOf(
            entry("kotlin.zip"),
            entry("kotlin/course.json"),
            entry("gardening/course.json"),
        )

        assertEquals(listOf("kotlin"), supersededCopies(folder))
    }

    @Test
    fun `a packing run says what it packed and that it was checked`() {
        val plan = CourseBackup.ArchivePlan(
            write = listOf(CourseBackup.Book("kotlin", listOf(entry("kotlin/course.json")))),
            keep = listOf(CourseBackup.Book("gardening", listOf(entry("gardening/course.json")))),
        )
        val report = CourseBackup.Report(copied = 1, skipped = 1, bytes = 100, failed = emptyList())

        assertEquals(
            "Packed 1 book — 1 file, 100 B · 1 book unchanged, read back and checked.",
            describeArchiveReport(plan, report),
        )
    }

    @Test
    fun `an archive that came back short is named with its reason`() {
        val plan = CourseBackup.ArchivePlan(
            write = listOf(CourseBackup.Book("kotlin", listOf(entry("kotlin/course.json")))),
            keep = emptyList(),
        )
        val report = CourseBackup.Report(
            copied = 1,
            skipped = 0,
            bytes = 100,
            failed = listOf(CourseBackup.Failure("kotlin.zip", "written, but it came back short")),
        )

        val said = describeArchiveReport(plan, report)

        assertTrue(said.contains("kotlin.zip — written, but it came back short"), said)
        assertTrue(said.contains("Backing up again is the fix"), said)
    }

    @Test
    fun `nothing to pack is said as up to date, with what was checked`() {
        val plan = CourseBackup.ArchivePlan(
            write = emptyList(),
            keep = listOf(
                CourseBackup.Book("kotlin", listOf(entry("kotlin/course.json"), entry("kotlin/memory.md"))),
            ),
        )

        assertEquals("Already up to date — 1 book, 2 files checked.", describeArchivePlan(plan))
    }

    @Test
    fun `the offer names the books, the shelf and the size`() {
        val copies = listOf(
            CourseBackup.LooseCopy("_shelf", listOf(entry("last_read.json", 100))),
            CourseBackup.LooseCopy("gardening", listOf(entry("gardening/course.json", 200))),
            CourseBackup.LooseCopy("kotlin", listOf(entry("kotlin/course.json", 600))),
        )

        assertEquals(
            "2 books and where you left off are also here the old way, as loose files — " +
                "3 files, 900 B. These are the ones that show up in the reader's picture " +
                "and document libraries. Nothing needs them now.",
            describeLooseCopies(copies),
        )
    }

    @Test
    fun `nothing loose means nothing to say`() {
        assertEquals("", describeLooseCopies(emptyList()))
    }

    @Test
    fun `a tidy-up reports what went`() {
        val swept = CourseBackup.Swept(
            removed = listOf("kotlin"),
            files = 22,
            bytes = 2_000,
            failed = emptyList(),
        )

        assertEquals("Removed 22 old files, 2 KB.", describeSweep(swept))
    }

    @Test
    fun `a group kept back is named with its reason, not counted`() {
        val swept = CourseBackup.Swept(
            removed = listOf("gardening"),
            files = 3,
            bytes = 300,
            failed = listOf(
                CourseBackup.Failure("kotlin.zip", "could not be read, so nothing was deleted"),
            ),
        )

        assertEquals(
            "Removed 3 old files, 300 B. 1 thing was kept back: " +
                "kotlin.zip — could not be read, so nothing was deleted.",
            describeSweep(swept),
        )
    }
}
