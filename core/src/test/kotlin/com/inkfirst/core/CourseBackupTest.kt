package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class CourseBackupTest {

    private fun entry(path: String, size: Long = 10, modified: Long = 1_000) =
        FileTree.Entry(path, size, modified)

    private fun File.put(path: String, text: String, modified: Long? = null): File {
        val f = File(this, path)
        f.parentFile?.mkdirs()
        f.writeText(text)
        if (modified != null) f.setLastModified(modified)
        return f
    }

    @Test
    fun `an empty destination takes everything`() {
        val source = listOf(entry("course.json"), entry("chapters/01.md"))

        val plan = CourseBackup.plan(source, emptyList())

        assertEquals(2, plan.copy.size)
        assertTrue(plan.skip.isEmpty())
        assertEquals(20, plan.bytes)
    }

    @Test
    fun `a file already backed up and unchanged is skipped`() {
        val source = listOf(entry("figures/01-cover.jpg", size = 900_000, modified = 1_000))
        // Copied a moment after it was written, which is the normal case.
        val destination = listOf(entry("figures/01-cover.jpg", size = 900_000, modified = 1_200))

        val plan = CourseBackup.plan(source, destination)

        assertTrue(plan.copy.isEmpty())
        assertEquals(1, plan.skip.size)
    }

    @Test
    fun `a destination stamp is compared as at-least-as-new, not equal`() {
        // The document picker gives no way to set a timestamp on the copy, so
        // a backed-up file always carries the moment it was COPIED — later
        // than the original. Demanding equal stamps would recopy every
        // illustration on every run, which is 14 MB of the 15 MB in a course.
        val source = listOf(entry("figures/01-cover.jpg", size = 900_000, modified = 1_000))
        val destination = listOf(entry("figures/01-cover.jpg", size = 900_000, modified = 9_999_999))

        assertTrue(CourseBackup.plan(source, destination).copy.isEmpty())
    }

    @Test
    fun `a file modified since the last backup is copied again`() {
        val source = listOf(entry("memory.md", size = 8_512, modified = 5_000))
        val destination = listOf(entry("memory.md", size = 8_000, modified = 3_000))

        val plan = CourseBackup.plan(source, destination)

        assertEquals(1, plan.copy.size)
        assertEquals("memory.md", plan.copy[0].path)
    }

    @Test
    fun `a size change is enough on its own`() {
        // Same second, different content — rare, but the stamp alone would
        // wave it through and the backup would hold the wrong bytes forever.
        val source = listOf(entry("course.json", size = 5_894, modified = 1_000))
        val destination = listOf(entry("course.json", size = 5_800, modified = 1_000))

        assertEquals(1, CourseBackup.plan(source, destination).copy.size)
    }

    @Test
    fun `overwrite mode takes everything regardless`() {
        val source = listOf(entry("course.json"), entry("chapters/01.md"))
        val destination = listOf(entry("course.json"), entry("chapters/01.md"))

        val plan = CourseBackup.plan(source, destination, CourseBackup.Mode.OVERWRITE)

        assertEquals(2, plan.copy.size)
        assertTrue(plan.skip.isEmpty())
    }

    @Test
    fun `a backup run never deletes at the destination`(@TempDir tmp: File) {
        // A course deleted on the device stays in the backup. That is the
        // difference between a backup and a mirror, and it is the reason this
        // path can never lose a reader's work.
        val from = File(tmp, "device").apply { mkdirs() }
        val to = File(tmp, "backup").apply { mkdirs() }
        from.put("kotlin/course.json", "{}")
        to.put("kotlin/course.json", "{}")
        to.put("deleted-course/course.json", "{}")
        to.put("deleted-course/chapters/01.md", "still here")

        CourseBackup.copy(LocalFileTree(from), LocalFileTree(to))

        assertTrue(File(to, "deleted-course/course.json").exists())
        assertEquals("still here", File(to, "deleted-course/chapters/01.md").readText())
    }

    @Test
    fun `a round trip reproduces the course`(@TempDir tmp: File) {
        val device = File(tmp, "device").apply { mkdirs() }
        val backup = File(tmp, "backup").apply { mkdirs() }
        val restored = File(tmp, "restored").apply { mkdirs() }
        device.put("kotlin/course.json", """{"title":"Kotlin"}""")
        device.put("kotlin/chapters/01-intro.md", "# Chapter one")
        device.put("kotlin/notes/01-intro.qa.md", "Q: why?")
        device.put("kotlin/figures/01-intro-cover.jpg", "JPEGBYTES")

        val out = CourseBackup.copy(LocalFileTree(device), LocalFileTree(backup))
        assertTrue(out.ok)
        assertEquals(4, out.copied)

        val back = CourseBackup.copy(
            LocalFileTree(backup),
            LocalFileTree(restored),
            CourseBackup.Mode.OVERWRITE,
        )
        assertTrue(back.ok)
        assertEquals("# Chapter one", File(restored, "kotlin/chapters/01-intro.md").readText())
        assertEquals("JPEGBYTES", File(restored, "kotlin/figures/01-intro-cover.jpg").readText())
        assertEquals("Q: why?", File(restored, "kotlin/notes/01-intro.qa.md").readText())
    }

    @Test
    fun `a second run has nothing left to do`(@TempDir tmp: File) {
        val device = File(tmp, "device").apply { mkdirs() }
        val backup = File(tmp, "backup").apply { mkdirs() }
        device.put("kotlin/course.json", "{}")
        device.put("kotlin/figures/cover.jpg", "BYTES")

        CourseBackup.copy(LocalFileTree(device), LocalFileTree(backup))
        val again = CourseBackup.copy(LocalFileTree(device), LocalFileTree(backup))

        assertEquals(0, again.copied)
        assertEquals(2, again.skipped)
    }

    @Test
    fun `one unreadable file does not abandon the rest`(@TempDir tmp: File) {
        val backup = File(tmp, "backup").apply { mkdirs() }
        val broken = object : FileTree {
            override fun list() = listOf(
                entry("kotlin/course.json"),
                entry("kotlin/chapters/01.md"),
                entry("kotlin/chapters/02.md"),
            )

            override fun read(path: String): ByteArray {
                if (path == "kotlin/chapters/01.md") throw IOException("permission denied")
                return "ok".toByteArray()
            }

            override fun write(path: String, bytes: ByteArray) = Unit
        }

        val report = CourseBackup.copy(broken, LocalFileTree(backup))

        assertEquals(2, report.copied)
        assertFalse(report.ok)
        assertEquals(1, report.failed.size)
        assertEquals("kotlin/chapters/01.md", report.failed[0].path)
        assertEquals("permission denied", report.failed[0].reason)
        // The two good ones really landed.
        assertTrue(File(backup, "kotlin/course.json").exists())
        assertTrue(File(backup, "kotlin/chapters/02.md").exists())
    }

    @Test
    fun `coursesIn lists slugs by their course json`(@TempDir tmp: File) {
        val backup = File(tmp, "backup").apply { mkdirs() }
        backup.put("kotlin/course.json", "{}")
        backup.put("kotlin/chapters/01.md", "x")
        backup.put("neural-networks/course.json", "{}")
        // Root-level files and stray directories are not courses.
        backup.put("last_read.json", "{}")
        backup.put("scratch/notes.txt", "x")

        assertEquals(
            listOf("kotlin", "neural-networks"),
            CourseBackup.coursesIn(LocalFileTree(backup)),
        )
    }

    @Test
    fun `listing a tree that does not exist is empty, not an error`(@TempDir tmp: File) {
        assertTrue(LocalFileTree(File(tmp, "never-created")).list().isEmpty())
    }

    // -- sweeping up the loose copies an older version wrote ---------------

    /**
     * A folder backed up by BOTH versions: every book packed as a zip, and
     * every book also sitting there as the directory of separate files the
     * first version wrote.
     */
    private fun mixedBackup(tmp: File): Pair<File, File> {
        val device = File(tmp, "device").apply { mkdirs() }
        device.put("kotlin/course.json", "{}")
        device.put("kotlin/chapters/01.md", "one")
        device.put("gardening/course.json", "{}")
        device.put("last_read.json", "{}")
        device.put("labels/ru.json", "{}")

        val backup = File(tmp, "backup").apply { mkdirs() }
        val here = LocalFileTree(device)
        CourseBackup.writeArchives(
            here,
            LocalFileTree(backup),
            CourseBackup.planArchives(here.list()) { null },
        )

        device.walkTopDown().filter { it.isFile }.forEach {
            backup.put(it.relativeTo(device).invariantSeparatorsPath, it.readText())
        }
        return device to backup
    }

    private fun names(dir: File): List<String> =
        dir.listFiles().orEmpty().map { it.name }.sorted()

    @Test
    fun `looseCopiesIn finds every book that is in the folder twice`(@TempDir tmp: File) {
        val (_, backup) = mixedBackup(tmp)

        val loose = CourseBackup.looseCopiesIn(LocalFileTree(backup).list())

        assertEquals(listOf("_shelf", "gardening", "kotlin"), loose.map { it.name })
        // The shelf is the odds and ends at the root, and it is two separate
        // things to remove rather than one directory.
        val shelf = loose.first { it.isShelf }
        assertEquals(listOf("labels", "last_read.json"), shelf.roots)
    }

    @Test
    fun `a loose book with no archive beside it is never offered`(@TempDir tmp: File) {
        val (_, backup) = mixedBackup(tmp)
        // This is a book that has left the device: the loose copy is the only
        // one left of it, which is exactly when deleting would be a disaster.
        assertTrue(File(backup, "gardening.zip").delete())

        val loose = CourseBackup.looseCopiesIn(LocalFileTree(backup).list())

        assertEquals(listOf("_shelf", "kotlin"), loose.map { it.name })
    }

    @Test
    fun `sweeping leaves the archives and nothing else`(@TempDir tmp: File) {
        val (_, backup) = mixedBackup(tmp)

        val swept = CourseBackup.removeLooseCopies(LocalFileTree(backup))

        assertTrue(swept.ok, swept.failed.toString())
        assertEquals(listOf("_shelf", "gardening", "kotlin"), swept.removed)
        assertEquals(5, swept.files)
        assertEquals(
            listOf("_shelf.zip", "gardening.zip", "kotlin.zip"),
            names(backup),
        )
    }

    @Test
    fun `a loose copy older than the archive is still removed`(@TempDir tmp: File) {
        // The reason this is its own test: every other comparison in this file
        // asks "is the destination as new as the source". Asking that HERE
        // would refuse every real case — the archive is the newer copy, and
        // the loose files carry the moment they were copied rather than the
        // moment they were written. What matters is only whether the archive
        // holds the path.
        val (_, backup) = mixedBackup(tmp)
        backup.put("kotlin/chapters/01.md", "a much longer body than the archive holds", 9_999_999)

        val swept = CourseBackup.removeLooseCopies(LocalFileTree(backup), only = setOf("kotlin"))

        assertTrue(swept.ok, swept.failed.toString())
        assertFalse(File(backup, "kotlin").exists())
    }

    @Test
    fun `a file the archive does not hold stops that book`(@TempDir tmp: File) {
        val (_, backup) = mixedBackup(tmp)
        // A chapter that was backed up loose and then deleted on the device,
        // so the zip written later never saw it. Deleting the loose folder
        // would be the backup forgetting it.
        backup.put("kotlin/chapters/99-only-here.md", "the last copy")

        val swept = CourseBackup.removeLooseCopies(LocalFileTree(backup))

        assertFalse(swept.ok)
        assertEquals(listOf("_shelf", "gardening"), swept.removed)
        assertEquals("kotlin.zip", swept.failed.single().path)
        assertTrue(swept.failed.single().reason.contains("99-only-here.md"))
        assertTrue(swept.failed.single().reason.contains("in kotlin "))
        assertEquals("the last copy", File(backup, "kotlin/chapters/99-only-here.md").readText())
        // And the rest of that book's loose files stay too — the group is
        // all-or-nothing, so nothing is half-removed.
        assertTrue(File(backup, "kotlin/course.json").exists())
    }

    @Test
    fun `an archive that will not open deletes nothing`(@TempDir tmp: File) {
        val (_, backup) = mixedBackup(tmp)
        File(backup, "kotlin.zip").writeText("this is not a zip")

        val swept = CourseBackup.removeLooseCopies(LocalFileTree(backup), only = setOf("kotlin"))

        assertFalse(swept.ok)
        assertTrue(swept.removed.isEmpty())
        assertTrue(File(backup, "kotlin/course.json").exists())
        assertTrue(File(backup, "kotlin/chapters/01.md").exists())
    }

    @Test
    fun `a truncated archive deletes nothing`(@TempDir tmp: File) {
        // The case a timestamp cannot see: a run interrupted part way leaves a
        // zip that opens, lists, and is missing most of the book.
        val (_, backup) = mixedBackup(tmp)
        val zip = File(backup, "kotlin.zip")
        zip.writeBytes(zip.readBytes().copyOfRange(0, 40))

        val swept = CourseBackup.removeLooseCopies(LocalFileTree(backup), only = setOf("kotlin"))

        assertFalse(swept.ok)
        assertTrue(File(backup, "kotlin/chapters/01.md").exists())
    }

    @Test
    fun `only the named groups are swept`(@TempDir tmp: File) {
        val (_, backup) = mixedBackup(tmp)

        CourseBackup.removeLooseCopies(LocalFileTree(backup), only = setOf("gardening"))

        assertFalse(File(backup, "gardening").exists())
        assertTrue(File(backup, "kotlin/course.json").exists())
        assertTrue(File(backup, "last_read.json").exists())
    }

    @Test
    fun `a tree that cannot delete says so rather than doing nothing`() {
        // The FileTree default. A silent no-op would report a tidy-up that
        // never happened.
        val tree = object : FileTree {
            override fun list() = emptyList<FileTree.Entry>()
            override fun read(path: String) = ByteArray(0)
            override fun write(path: String, bytes: ByteArray) = Unit
        }

        assertThrows(IOException::class.java) { tree.delete("anything") }
    }

    @Test
    fun `a stray file at the root does not strand the rest of the shelf`(@TempDir tmp: File) {
        // The shelf's roots are unrelated things sharing the top of the
        // folder, not one book. Something the reader dropped in themselves is
        // kept, and keeping it must not keep last_read.json with it.
        val (_, backup) = mixedBackup(tmp)
        backup.put("holiday-photos/beach.jpg", "not ours")

        val swept = CourseBackup.removeLooseCopies(LocalFileTree(backup), only = setOf("_shelf"))

        assertFalse(swept.ok)
        assertEquals("not ours", File(backup, "holiday-photos/beach.jpg").readText())
        assertFalse(File(backup, "last_read.json").exists())
        assertFalse(File(backup, "labels").exists())
    }
}
