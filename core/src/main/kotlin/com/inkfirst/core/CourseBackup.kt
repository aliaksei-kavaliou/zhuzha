package com.inkfirst.core

import java.io.File
import java.io.IOException

/**
 * Copying courses somewhere they survive this device — and copying them back.
 *
 * This is BACKUP, not sync: one direction at a time, chosen by the reader,
 * and it never deletes anything at the far end. A course deleted on the
 * device stays in the backup, which is the whole point of having one. Nothing
 * here merges, so there are no conflicts to resolve and no change to the way
 * a course is stored.
 *
 * It matters more than "in case the Boox dies". The courses live in the app's
 * own external-files directory, whose permissions (`drwxrwx--x`) give other
 * apps no read access at all — so the promise in design.md §1 that a reader's
 * study material is plain, inspectable, greppable files is currently only
 * true over adb from a laptop. Copying the tree to a folder the reader picked
 * is what makes it true on the device.
 */
object CourseBackup {

    /**
     * What to do about a file that is already at the destination.
     *
     * [INCREMENTAL] is for backing up: skip anything already copied and
     * unchanged since, so an evening's reading moves a few kilobytes rather
     * than recopying every chapter illustration.
     *
     * [OVERWRITE] is for restoring: you asked for the backup's version, so
     * take it whatever is sitting there now.
     */
    enum class Mode { INCREMENTAL, OVERWRITE }

    /**
     * What a run is about to do, worked out before any bytes move so the
     * screen can say "42 files, 1.2 MB" and so the decision can be tested
     * without touching a disk.
     */
    data class Plan(
        val copy: List<FileTree.Entry>,
        val skip: List<FileTree.Entry>,
    ) {
        val bytes: Long get() = copy.sumOf { it.size }
    }

    /** One file that could not be copied, and why. */
    data class Failure(val path: String, val reason: String)

    /**
     * How a run went. [failed] is a list rather than an exception because one
     * unreadable file should not abandon the other thirty-nine — a backup
     * that stops at the first problem and says nothing is worse than one that
     * copies what it can and reports the rest.
     */
    data class Report(
        val copied: Int,
        val skipped: Int,
        val bytes: Long,
        val failed: List<Failure>,
    ) {
        val ok: Boolean get() = failed.isEmpty()
    }

    /**
     * Decides what needs copying. Pure — it takes two listings and returns a
     * decision, so every rule below is testable without a filesystem.
     */
    fun plan(
        source: List<FileTree.Entry>,
        destination: List<FileTree.Entry>,
        mode: Mode = Mode.INCREMENTAL,
    ): Plan {
        if (mode == Mode.OVERWRITE) return Plan(copy = source, skip = emptyList())

        val existing = destination.associateBy { it.path }
        val copy = mutableListOf<FileTree.Entry>()
        val skip = mutableListOf<FileTree.Entry>()

        source.forEach { entry ->
            val there = existing[entry.path]
            if (there != null && there.size == entry.size && there.modified >= entry.modified) {
                skip += entry
            } else {
                copy += entry
            }
        }
        return Plan(copy = copy, skip = skip)
    }

    /**
     * Carries out a [plan]. Reads each file from [from] and writes it to [to];
     * anything that fails is recorded and the run continues.
     *
     * [onProgress] is called after each file with how many of the planned
     * files are done. A course is ~15 MB over a document provider, which is
     * slow enough that a silent screen reads as a dead button — and on e-ink
     * the honest form of a progress bar is a line of text that changes a few
     * times, not an animation that repaints the panel every frame.
     */
    fun run(
        from: FileTree,
        to: FileTree,
        plan: Plan,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Report {
        var copied = 0
        var bytes = 0L
        var done = 0
        val total = plan.copy.size
        val failed = mutableListOf<Failure>()

        plan.copy.forEach { entry ->
            try {
                val content = from.read(entry.path)
                to.write(entry.path, content)
                copied++
                bytes += content.size
            } catch (e: IOException) {
                failed += Failure(entry.path, e.message ?: e::class.java.simpleName)
            }
            // Outside the catch: a file that failed is still a file dealt
            // with, and a counter that stalls on the broken one is the same
            // dead screen this exists to avoid.
            done++
            onProgress(done, total)
        }
        return Report(copied = copied, skipped = plan.skip.size, bytes = bytes, failed = failed)
    }

    /** Plan and run in one call — the ordinary case. */
    fun copy(
        from: FileTree,
        to: FileTree,
        mode: Mode = Mode.INCREMENTAL,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Report = run(from, to, plan(from.list(), to.list(), mode), onProgress)

    /**
     * The course slugs present in a tree, found the same way the app finds
     * them: a directory directly under the root holding a `course.json`.
     * Lets a restore screen list what is in a backup without parsing any of
     * it.
     */
    fun coursesIn(tree: FileTree): List<String> = coursesIn(tree.list())

    /**
     * The same question asked of a listing already in hand. Worth having
     * separately: over a document provider a listing is a query per directory,
     * and a backup screen needs both the names and the files themselves.
     */
    fun coursesIn(entries: List<FileTree.Entry>): List<String> =
        entries
            .mapNotNull { entry ->
                val parts = entry.path.split("/")
                if (parts.size == 2 && parts[1] == "course.json") parts[0] else null
            }
            .distinct()
            .sorted()
}

/**
 * A tree of files addressed by relative path, like "chapters/01-intro.md".
 *
 * It exists because the far end is not always an ordinary directory. On the
 * device the reader picks a destination through Android's document picker,
 * which hands back a document tree rather than a [File] — so `:core`
 * describes what it needs and `:app` supplies it. That keeps the interesting
 * half, deciding WHAT to copy, testable on a laptop with no Android in sight.
 *
 * Interfaces in Kotlin are close to PHP's: a contract with no implementation.
 * [LocalFileTree] below is the one real directories need.
 */
interface FileTree {

    /**
     * Every file in the tree, recursively, with paths relative to its root
     * and separated by "/" on every platform. Directories are not listed —
     * they are implied by the paths of the files inside them, and a document
     * tree has to create them on write anyway.
     */
    fun list(): List<Entry>

    /** The bytes of one file. Throws [IOException] if it cannot be read. */
    fun read(path: String): ByteArray

    /** Writes one file, creating any directories above it. */
    fun write(path: String, bytes: ByteArray)

    /**
     * One file. [modified] is "last modified" in milliseconds since the epoch.
     *
     * Note what is NOT here: a way to SET [modified] on write. Android's
     * document API does not offer one, so a backed-up file is stamped with
     * the moment it was copied rather than the moment the original was
     * written. That is why [CourseBackup.plan] asks whether the destination
     * is at least as new as the source instead of asking whether the two
     * stamps match — a rule that would otherwise recopy all 14 MB of
     * illustrations every single time.
     */
    data class Entry(val path: String, val size: Long, val modified: Long)
}

/** A [FileTree] over an ordinary directory. */
class LocalFileTree(private val root: File) : FileTree {

    override fun list(): List<FileTree.Entry> {
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile }
            .map {
                FileTree.Entry(
                    path = it.relativeTo(root).invariantSeparatorsPath,
                    size = it.length(),
                    modified = it.lastModified(),
                )
            }
            .sortedBy { it.path }
            .toList()
    }

    override fun read(path: String): ByteArray = File(root, path).readBytes()

    override fun write(path: String, bytes: ByteArray) {
        val target = File(root, path)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
    }
}
