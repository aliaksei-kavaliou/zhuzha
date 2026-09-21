package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `nothing at the destination is ever deleted`(@TempDir tmp: File) {
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
}
