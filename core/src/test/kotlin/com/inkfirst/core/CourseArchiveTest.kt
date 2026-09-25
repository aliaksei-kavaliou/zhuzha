package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

/**
 * The backup packed one file per book.
 *
 * Most of what matters here is not "does zip work" but the two rules that
 * make an archive safe to trust: a book is judged by what is INSIDE its
 * archive rather than by the archive's timestamp, and every archive written
 * is read back before the run says it succeeded.
 */
class CourseArchiveTest {

    private fun File.put(path: String, text: String, modified: Long? = null): File {
        val f = File(this, path)
        f.parentFile?.mkdirs()
        f.writeText(text)
        if (modified != null) f.setLastModified(modified)
        return f
    }

    /** A device with one small book on it. */
    private fun shelf(tmp: File): File = File(tmp, "device").apply {
        mkdirs()
        put("kotlin/course.json", """{"title":"Kotlin"}""")
        put("kotlin/chapters/01-intro.md", "# Chapter one\n".repeat(50))
        put("kotlin/figures/01-intro-cover.png", "PNGBYTES-not-really")
        put("last_read.json", """{"course_slug":"kotlin"}""")
    }

    @Test
    fun `a book packs and comes back byte for byte`(@TempDir tmp: File) {
        val device = shelf(tmp)
        val from = LocalFileTree(device)
        val book = CourseBackup.booksIn(from.list()).first { it.name == "kotlin" }

        val bytes = ByteArrayOutputStream()
        val packed = CourseArchive.pack(from, book.entries, bytes)
        assertEquals(3, packed.files)
        assertTrue(packed.failed.isEmpty())

        val restored = File(tmp, "restored").apply { mkdirs() }
        val out = CourseArchive.unpack(ByteArrayInputStream(bytes.toByteArray()), LocalFileTree(restored))

        assertEquals(3, out.files)
        assertTrue(out.failed.isEmpty())
        listOf(
            "kotlin/course.json",
            "kotlin/chapters/01-intro.md",
            "kotlin/figures/01-intro-cover.png",
        ).forEach { path ->
            assertEquals(File(device, path).readText(), File(restored, path).readText(), path)
        }
    }

    @Test
    fun `paths inside the archive stay relative to the courses root`(@TempDir tmp: File) {
        // So unzipping by hand reproduces the course folder, and a restore is
        // a plain write with no path arithmetic to get wrong.
        val from = LocalFileTree(shelf(tmp))
        val book = CourseBackup.booksIn(from.list()).first { it.name == "kotlin" }

        val bytes = ByteArrayOutputStream()
        CourseArchive.pack(from, book.entries, bytes)

        val names = CourseArchive.listing(ByteArrayInputStream(bytes.toByteArray())).map { it.path }
        assertTrue(names.all { it.startsWith("kotlin/") }, names.toString())
    }

    @Test
    fun `what is not a book travels in the shelf archive`(@TempDir tmp: File) {
        val device = shelf(tmp)
        device.put("labels/russian.json", "{}")
        val books = CourseBackup.booksIn(LocalFileTree(device).list())

        assertEquals(listOf(CourseArchive.SHELF, "kotlin"), books.map { it.name })
        assertEquals(
            listOf("labels/russian.json", "last_read.json"),
            books.first { it.isShelf }.entries.map { it.path },
        )
    }

    @Test
    fun `the shelf is never offered as a book to restore`(@TempDir tmp: File) {
        val backup = listOf(
            FileTree.Entry("kotlin.zip", 100, 1_000),
            FileTree.Entry("_shelf.zip", 10, 1_000),
            FileTree.Entry("notes.txt", 10, 1_000),
        )
        assertEquals(listOf("kotlin"), CourseBackup.archivedIn(backup))
    }

    @Test
    fun `a book already packed and unchanged is left alone`(@TempDir tmp: File) {
        val from = LocalFileTree(shelf(tmp))
        val to = LocalFileTree(File(tmp, "backup").apply { mkdirs() })

        val first = CourseBackup.planArchives(from.list(), CourseBackup.archiveReader(to))
        assertEquals(2, first.write.size)
        assertTrue(CourseBackup.writeArchives(from, to, first).ok)

        val again = CourseBackup.planArchives(from.list(), CourseBackup.archiveReader(to))
        assertTrue(again.write.isEmpty(), again.write.map { it.name }.toString())
        assertEquals(2, again.keep.size)
    }

    @Test
    fun `one changed file repacks its book and only its book`(@TempDir tmp: File) {
        val device = shelf(tmp)
        device.put("neural/course.json", "{}")
        val from = LocalFileTree(device)
        val to = LocalFileTree(File(tmp, "backup").apply { mkdirs() })
        CourseBackup.writeArchives(
            from,
            to,
            CourseBackup.planArchives(from.list(), CourseBackup.archiveReader(to)),
        )

        // A chapter read and annotated this evening.
        device.put("kotlin/course.json", """{"title":"Kotlin","progress":3}""")

        val plan = CourseBackup.planArchives(from.list(), CourseBackup.archiveReader(to))

        assertEquals(listOf("kotlin"), plan.write.map { it.name })
        assertTrue(plan.keep.any { it.name == "neural" })
    }

    @Test
    fun `a half-written archive is packed again, not skipped`(@TempDir tmp: File) {
        // The case a timestamp cannot see. A run interrupted in the middle of
        // a 15 MB book leaves an archive NEWER than every file in the book
        // and holding only part of it; judged by its stamp it would be
        // skipped for ever, and the reader would find out when they needed it.
        val from = LocalFileTree(shelf(tmp))
        val backup = File(tmp, "backup").apply { mkdirs() }
        val to = LocalFileTree(backup)
        CourseBackup.writeArchives(
            from,
            to,
            CourseBackup.planArchives(from.list(), CourseBackup.archiveReader(to)),
        )

        val archive = File(backup, "kotlin.zip")
        val whole = archive.readBytes()
        archive.writeBytes(whole.copyOfRange(0, whole.size / 2))
        archive.setLastModified(System.currentTimeMillis() + 60_000)

        val plan = CourseBackup.planArchives(from.list(), CourseBackup.archiveReader(to))

        assertTrue(plan.write.any { it.name == "kotlin" })
    }

    @Test
    fun `an archive missing a file is packed again`(@TempDir tmp: File) {
        val device = shelf(tmp)
        val from = LocalFileTree(device)
        val backup = File(tmp, "backup").apply { mkdirs() }
        val to = LocalFileTree(backup)

        // An archive of the book as it was before tonight's illustration.
        val short = CourseBackup.Book(
            "kotlin",
            from.list().filter { it.path.startsWith("kotlin/") && !it.path.contains("figures/") },
        )
        to.writeStream("kotlin.zip") { CourseArchive.pack(from, short.entries, it) }

        val plan = CourseBackup.planArchives(from.list(), CourseBackup.archiveReader(to))

        assertTrue(plan.write.any { it.name == "kotlin" })
    }

    @Test
    fun `an archive that did not arrive whole is reported, not called a backup`(@TempDir tmp: File) {
        val from = LocalFileTree(shelf(tmp))
        val backup = File(tmp, "backup").apply { mkdirs() }
        // A far end that stops half way through everything written to it — a
        // card pulled mid-write, a provider that lies about flushing.
        val to = object : FileTree by LocalFileTree(backup) {
            override fun writeStream(path: String, body: (OutputStream) -> Unit) {
                val buffer = ByteArrayOutputStream()
                body(buffer)
                val bytes = buffer.toByteArray()
                File(backup, path).writeBytes(bytes.copyOfRange(0, bytes.size / 2))
            }
        }

        val plan = CourseBackup.planArchives(from.list(), CourseBackup.archiveReader(to))
        val report = CourseBackup.writeArchives(from, to, plan)

        assertFalse(report.ok)
        assertTrue(report.failed.any { it.path == "kotlin.zip" }, report.failed.toString())
        assertTrue(
            report.failed.first { it.path == "kotlin.zip" }.reason.contains("read back"),
            report.failed.toString(),
        )
    }

    @Test
    fun `an unreadable file costs one file, not the book`(@TempDir tmp: File) {
        val device = shelf(tmp)
        val real = LocalFileTree(device)
        val broken = object : FileTree by real {
            override fun read(path: String): ByteArray {
                if (path.endsWith(".png")) throw java.io.IOException("permission denied")
                return real.read(path)
            }
        }
        val bytes = ByteArrayOutputStream()

        val packed = CourseArchive.pack(broken, CourseBackup.booksIn(real.list())
            .first { it.name == "kotlin" }.entries, bytes)

        assertEquals(2, packed.files)
        assertEquals(1, packed.failed.size)
        assertEquals("kotlin/figures/01-intro-cover.png", packed.failed[0].path)
        // And what did get in is a readable archive, not a broken one.
        assertEquals(2, CourseArchive.listing(ByteArrayInputStream(bytes.toByteArray())).size)
    }

    @Test
    fun `a restored book is not packed all over again`(@TempDir tmp: File) {
        // Restoring writes every file at once; without the archive's own
        // timestamps going back on, the whole book would look changed and the
        // next backup would repack all of it.
        val from = LocalFileTree(shelf(tmp))
        val to = LocalFileTree(File(tmp, "backup").apply { mkdirs() })
        CourseBackup.writeArchives(
            from,
            to,
            CourseBackup.planArchives(from.list(), CourseBackup.archiveReader(to)),
        )

        val restoredDir = File(tmp, "restored").apply { mkdirs() }
        val restored = LocalFileTree(restoredDir)
        val report = CourseBackup.restoreArchive(to, "kotlin", restored)
        assertTrue(report.ok)
        assertEquals(3, report.copied)

        val plan = CourseBackup.planArchives(restored.list(), CourseBackup.archiveReader(to))
        assertTrue(plan.write.none { it.name == "kotlin" }, plan.write.map { it.name }.toString())
    }

    @Test
    fun `restoring an archive that is not there fails with a sentence`(@TempDir tmp: File) {
        val to = LocalFileTree(File(tmp, "backup").apply { mkdirs() })
        val report = CourseBackup.restoreArchive(to, "missing", LocalFileTree(File(tmp, "here")))

        assertFalse(report.ok)
        assertEquals("missing.zip", report.failed[0].path)
        assertNotNull(report.failed[0].reason)
    }

    @Test
    fun `an entry that points outside the courses root is refused`(@TempDir tmp: File) {
        // These archives are written by this app, but they live in a folder
        // the reader can edit and a restore is the one path that writes into
        // the course tree.
        val bytes = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("../../escaped.md"))
            zip.write("nope".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("kotlin/course.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }
        val here = File(tmp, "here").apply { mkdirs() }

        val out = CourseArchive.unpack(ByteArrayInputStream(bytes.toByteArray()), LocalFileTree(here))

        assertEquals(1, out.files)
        assertEquals(1, out.failed.size)
        assertEquals("../../escaped.md", out.failed[0].path)
        assertFalse(File(tmp, "escaped.md").exists())
        assertFalse(File(here.parentFile.parentFile, "escaped.md").exists())
    }

    @Test
    fun `an illustration is stored rather than compressed`(@TempDir tmp: File) {
        // A course is mostly PNG, which deflates to about its own size. On a
        // reader's CPU that is the whole cost of the backup, paid for nothing.
        val device = File(tmp, "device").apply { mkdirs() }
        val incompressible = ByteArray(200_000) { (it * 31 % 251).toByte() }
        File(device, "kotlin").mkdirs()
        File(device, "kotlin/course.json").writeText("{}")
        File(device, "kotlin/figures").mkdirs()
        File(device, "kotlin/figures/cover.png").writeBytes(incompressible)
        val from = LocalFileTree(device)

        val bytes = ByteArrayOutputStream()
        CourseArchive.pack(from, from.list(), bytes)

        java.util.zip.ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { zip ->
            var seen = false
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name.endsWith(".png")) {
                    seen = true
                    assertEquals(java.util.zip.ZipEntry.STORED, entry.method)
                    assertEquals(200_000L, entry.size)
                }
                zip.closeEntry()
            }
            assertTrue(seen)
        }
    }
}
