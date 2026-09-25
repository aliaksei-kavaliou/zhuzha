package com.inkfirst.core

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.attribute.FileTime
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A book as ONE file at the far end.
 *
 * The first backup copied a course file for file, which is the honest shape
 * for something whose whole promise is "plain inspectable files" — and it was
 * wrong for the device it lands on. A Boox indexes shared storage: 96 loose
 * files walked straight into the reader's Images and Documents libraries,
 * where they sit among their photos looking like clutter and one sweep of a
 * "delete these" gesture takes a chapter with them. A backup you can destroy
 * by tidying up is not a backup.
 *
 * So a book travels as `<slug>.zip`. Five files in the folder instead of four
 * hundred, nothing that reads as a picture, and zip is not an opaque format —
 * every desktop on earth opens one by double-clicking, which is the same
 * promise §1 makes, kept with one more click.
 *
 * Paths INSIDE the archive stay relative to the courses root, so
 * `kotlin.zip` holds `kotlin/course.json` rather than `course.json`.
 * Unzipping by hand therefore reproduces the course folder exactly, and
 * [unpack] can write straight into the courses tree with no path arithmetic
 * to get wrong.
 */
object CourseArchive {

    const val EXTENSION = "zip"

    /**
     * The archive for everything at the courses root that is not a book —
     * `last_read.json`, the `labels/` translations. It is named with a
     * leading underscore so it can never collide with a course: slugs come
     * from [courseSlug], which emits `[a-z0-9-]` and nothing else.
     */
    const val SHELF = "_shelf"

    fun nameFor(book: String): String = "$book.$EXTENSION"

    /** The book an archive holds, or null if the name is not an archive. */
    fun bookIn(fileName: String): String? {
        if (!fileName.endsWith(".$EXTENSION", ignoreCase = true)) return null
        if (fileName.contains('/')) return null
        return fileName.dropLast(EXTENSION.length + 1).takeIf { it.isNotEmpty() }
    }

    /** What one packing run managed. Failures are per file, as everywhere else here. */
    data class Packed(
        val files: Int,
        val bytes: Long,
        val failed: List<CourseBackup.Failure>,
    )

    /**
     * Writes [entries] read from [from] into [out] as a zip.
     *
     * [out] is finished but NOT closed — the caller opened it and closes it,
     * which is what lets this stream a 15 MB book through a document provider
     * without ever holding the whole archive in memory.
     *
     * One unreadable file does not abandon the other ninety-five: it is
     * recorded and skipped, exactly as the file-by-file backup does. The
     * archive that results is short of one file, and that is self-correcting
     * — [CourseBackup.planArchives] compares the archive's contents against
     * the book and writes it again next run.
     */
    fun pack(
        from: FileTree,
        entries: List<FileTree.Entry>,
        out: OutputStream,
        onFile: (String) -> Unit = {},
    ): Packed {
        var files = 0
        var bytes = 0L
        val failed = mutableListOf<CourseBackup.Failure>()
        val zip = ZipOutputStream(out)

        entries.sortedBy { it.path }.forEach { entry ->
            val content = try {
                from.read(entry.path)
            } catch (e: IOException) {
                failed += CourseBackup.Failure(entry.path, e.message ?: e::class.java.simpleName)
                onFile(entry.path)
                return@forEach
            }
            zip.putNextEntry(entryFor(entry.path, content, entry.modified))
            zip.write(content)
            zip.closeEntry()
            files++
            bytes += content.size
            onFile(entry.path)
        }

        // finish(), not close(): the central directory gets written, the
        // stream underneath stays the caller's to close.
        zip.finish()
        return Packed(files = files, bytes = bytes, failed = failed)
    }

    /**
     * What is inside an archive, as the same [FileTree.Entry] the rest of the
     * backup reasons about — so "is this archive up to date?" is answered by
     * the very same comparison as "is this file up to date?".
     *
     * [deep] reads every entry's bytes through and discards them. That is not
     * waste: the zip format carries a CRC per entry and [ZipInputStream]
     * checks it as the bytes go past, so a deep listing is a real integrity
     * check of a file that may be the only copy of a book. Shallow is enough
     * to plan with, and reads far less.
     *
     * Throws [IOException] (a [ZipException] is one) if the archive is
     * damaged or truncated — a half-written archive therefore reports itself
     * rather than passing as a backup.
     */
    fun listing(input: InputStream, deep: Boolean = false): List<FileTree.Entry> {
        val out = mutableListOf<FileTree.Entry>()
        val zip = ZipInputStream(input)
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory) {
                // A deflated entry written to a stream carries its size in a
                // descriptor AFTER the data, so the size is unknown until the
                // data has been read. Reading it is also what verifies it.
                val size = if (deep || entry.size < 0) drain(zip) else entry.size
                out += FileTree.Entry(entry.name, size, entry.time)
            }
            zip.closeEntry()
        }
        return out.sortedBy { it.path }
    }

    /** What one restore managed. */
    data class Unpacked(
        val files: Int,
        val bytes: Long,
        val failed: List<CourseBackup.Failure>,
    )

    /**
     * Writes an archive's contents into [to], overwriting what is there —
     * restoring is the direction that was asked for explicitly.
     *
     * Entry names are checked before anything is written. A zip can name
     * `../../somewhere` and nothing in the format forbids it; these archives
     * are written by this app, but they sit in a folder the reader can edit
     * and a restore is the one path that writes into the courses tree.
     */
    fun unpack(
        input: InputStream,
        to: FileTree,
        onFile: (String) -> Unit = {},
    ): Unpacked {
        var files = 0
        var bytes = 0L
        val failed = mutableListOf<CourseBackup.Failure>()
        val zip = ZipInputStream(input)

        while (true) {
            val entry = zip.nextEntry ?: break
            val path = entry.name
            if (entry.isDirectory) {
                zip.closeEntry()
                continue
            }
            if (!isSafePath(path)) {
                failed += CourseBackup.Failure(path, "not a name this backup would have written")
                zip.closeEntry()
                continue
            }
            try {
                val content = zip.readBytes()
                to.write(path, content)
                // The archive remembers when each file was really written, so
                // a restored book is not "all of it changed a minute ago" —
                // which would make the next backup repack every book.
                to.touch(path, entry.time)
                files++
                bytes += content.size
                onFile(path)
            } catch (e: IOException) {
                failed += CourseBackup.Failure(path, e.message ?: e::class.java.simpleName)
            }
            zip.closeEntry()
        }
        return Unpacked(files = files, bytes = bytes, failed = failed)
    }

    /**
     * Zip keeps timestamps in an MS-DOS field with two-second resolution.
     * Java also writes the exact millisecond in an extra field and reads it
     * back, but only when the archive was written by something that bothers —
     * so every comparison against an archive allows two seconds of slack
     * rather than trusting the stamp to the millisecond.
     */
    const val TIME_SLACK_MILLIS = 2_000L

    /** Extensions whose bytes are already compressed; deflating them again buys nothing. */
    private val STORED = setOf("jpg", "jpeg", "png", "webp", "gif", "zip", "epub")

    private fun entryFor(path: String, content: ByteArray, modified: Long): ZipEntry =
        ZipEntry(path).apply {
            // Sets the DOS field every unzip tool reads AND the exact stamp
            // in the extra field, so a round trip keeps the millisecond.
            if (modified > 0) setLastModifiedTime(FileTime.fromMillis(modified))
            if (path.substringAfterLast('.', "").lowercase() in STORED) {
                // A stored entry must declare everything up front; the whole
                // point is that the CPU never touches the bytes. On a course
                // that is 14 MB of illustration this is most of the run.
                method = ZipEntry.STORED
                size = content.size.toLong()
                compressedSize = content.size.toLong()
                crc = CRC32().apply { update(content) }.value
            } else {
                method = ZipEntry.DEFLATED
            }
        }

    private fun drain(zip: ZipInputStream): Long {
        val buffer = ByteArray(DRAIN_BUFFER)
        var total = 0L
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            total += read
        }
        return total
    }

    private fun isSafePath(path: String): Boolean {
        if (path.isEmpty() || path.startsWith("/") || path.startsWith("\\")) return false
        if (path.contains('\\')) return false
        if (path.contains(':')) return false
        return path.split("/").none { it == ".." || it == "." || it.isEmpty() }
    }

    private const val DRAIN_BUFFER = 64 * 1024
}
