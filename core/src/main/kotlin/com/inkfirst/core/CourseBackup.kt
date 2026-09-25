package com.inkfirst.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Copying courses somewhere they survive this device — and copying them back.
 *
 * This is BACKUP, not sync: one direction at a time, chosen by the reader.
 * A course deleted on the device stays in the backup, which is the whole
 * point of having one. Nothing here merges, so there are no conflicts to
 * resolve and no change to the way a course is stored.
 *
 * It deletes at the far end in exactly one case, [removeLooseCopies], and
 * only for files an archive in the same folder verifiably already holds. That
 * is a tidy-up of this app's own leftovers, not a sync rule: nothing the
 * reader put there is touched, and nothing is removed because the device no
 * longer has it.
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
     *
     * [slackMillis] is how much older the destination may be and still count
     * as current. Zero for ordinary files; two seconds when the destination
     * listing came out of a zip, whose timestamps are kept in an MS-DOS field
     * that only counts even seconds.
     */
    fun plan(
        source: List<FileTree.Entry>,
        destination: List<FileTree.Entry>,
        mode: Mode = Mode.INCREMENTAL,
        slackMillis: Long = 0,
    ): Plan {
        if (mode == Mode.OVERWRITE) return Plan(copy = source, skip = emptyList())

        val existing = destination.associateBy { it.path }
        val copy = mutableListOf<FileTree.Entry>()
        val skip = mutableListOf<FileTree.Entry>()

        source.forEach { entry ->
            val there = existing[entry.path]
            if (there != null &&
                there.size == entry.size &&
                there.modified + slackMillis >= entry.modified
            ) {
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

    // ---------------------------------------------------------------------
    // Archives: the same backup, packed one file per book.
    //
    // Loose files were the honest shape and the wrong one for the device —
    // see CourseArchive for why. Nothing about WHAT to copy changes; what
    // changes is the grain. The unit of a decision is now a book rather than
    // a file, because a zip cannot be written half-way: touch one chapter and
    // the whole book is repacked.
    // ---------------------------------------------------------------------

    /**
     * A group of files that travels as one archive — one course, or [the
     * shelf][CourseArchive.SHELF] for what sits at the courses root and
     * belongs to no course.
     */
    data class Book(val name: String, val entries: List<FileTree.Entry>) {
        val file: String get() = CourseArchive.nameFor(name)
        val bytes: Long get() = entries.sumOf { it.size }
        val isShelf: Boolean get() = name == CourseArchive.SHELF
    }

    /**
     * What an archiving run is about to do. [keep] is the books already
     * backed up and unchanged — the reason the second run of an evening is
     * quick even though a book is all-or-nothing.
     */
    data class ArchivePlan(
        val write: List<Book>,
        val keep: List<Book>,
    ) {
        val bytes: Long get() = write.sumOf { it.bytes }
        val files: Int get() = write.sumOf { it.entries.size }
        val kept: Int get() = keep.sumOf { it.entries.size }
    }

    /**
     * Groups a courses-root listing into the archives it would become.
     *
     * A course is a directory holding a `course.json`, by the same rule as
     * [coursesIn]; everything else at the root — `last_read.json`, the
     * `labels/` translations, anything a future version drops there — goes in
     * the shelf archive, so "back up everything" keeps meaning everything.
     */
    fun booksIn(source: List<FileTree.Entry>): List<Book> {
        val slugs = coursesIn(source).toSet()
        return source
            .groupBy { entry ->
                val head = entry.path.substringBefore('/', "")
                if (head in slugs) head else CourseArchive.SHELF
            }
            .map { (name, entries) -> Book(name, entries.sortedBy { it.path }) }
            .sortedBy { it.name }
    }

    /**
     * Decides which books need packing again.
     *
     * [archived] is asked what is inside the archive for a book, and answers
     * null when there is none — or when the one there cannot be read, which
     * is the case that matters. A run interrupted half-way through a 15 MB
     * book leaves a truncated zip whose timestamp is NEWER than everything in
     * the book; judging it by its timestamp would skip it forever. Judging it
     * by its contents cannot: a damaged archive fails to list, and a short
     * one is missing a file, and both come back here as "write it again".
     *
     * That makes the check and the repair the same act, and it is also the
     * question a reader actually asks of a backup — not "is it recent?" but
     * "is everything in it?".
     */
    fun planArchives(
        source: List<FileTree.Entry>,
        archived: (Book) -> List<FileTree.Entry>?,
    ): ArchivePlan {
        val write = mutableListOf<Book>()
        val keep = mutableListOf<Book>()
        booksIn(source).forEach { book ->
            val inside = archived(book)
            val current = inside != null &&
                plan(book.entries, inside, slackMillis = CourseArchive.TIME_SLACK_MILLIS)
                    .copy.isEmpty()
            if (current) keep += book else write += book
        }
        return ArchivePlan(write = write, keep = keep)
    }

    /**
     * Reads what is inside the archive for a book, or null if it is not there
     * or will not open. The shape [planArchives] wants, over a real tree.
     */
    fun archiveReader(to: FileTree, deep: Boolean = false): (Book) -> List<FileTree.Entry>? {
        val present = runCatching { to.list().map { it.path }.toSet() }.getOrDefault(emptySet())
        return { book ->
            if (book.file !in present) null
            else runCatching { to.readStream(book.file).use { CourseArchive.listing(it, deep) } }
                .getOrNull()
        }
    }

    /**
     * Packs each book in [plan] into its own archive at [to].
     *
     * Every archive written is read back before the run reports success. It
     * doubles the reading, and it is worth it twice over: a zip read back
     * checks a CRC per file, so this is the difference between "15 MB went
     * out" and "15 MB arrived, and it is the right 15 MB" — on what may be
     * the only other copy of a book. The check reuses [plan] against the
     * book's own listing, so "up to date" and "arrived intact" are one rule
     * asked twice rather than two rules that can disagree.
     */
    fun writeArchives(
        from: FileTree,
        to: FileTree,
        plan: ArchivePlan,
        verify: Boolean = true,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Report {
        var copied = 0
        var bytes = 0L
        var done = 0
        val total = plan.files
        val failed = mutableListOf<Failure>()

        plan.write.forEach { book ->
            var packed: CourseArchive.Packed? = null
            try {
                to.writeStream(book.file) { out ->
                    packed = CourseArchive.pack(from, book.entries, out) {
                        done++
                        onProgress(done, total)
                    }
                }
            } catch (e: IOException) {
                failed += Failure(book.file, e.message ?: e::class.java.simpleName)
                // Whatever did not get counted file by file still happened to
                // the run; the counter must not stall on the broken book.
                done = minOf(total, done + book.entries.size)
                onProgress(done, total)
                return@forEach
            }

            val result = packed ?: return@forEach
            failed += result.failed
            copied += result.files
            bytes += result.bytes

            if (verify) {
                val problem = checkArchive(to, book)
                if (problem != null) failed += Failure(book.file, problem)
            }
        }
        return Report(copied = copied, skipped = plan.kept, bytes = bytes, failed = failed)
    }

    /** Null when the archive holds the book; a sentence when it does not. */
    private fun checkArchive(to: FileTree, book: Book): String? {
        val inside = try {
            to.readStream(book.file).use { CourseArchive.listing(it, deep = true) }
        } catch (e: IOException) {
            return "written, but it could not be read back (${e.message ?: "damaged"})"
        }
        val short = plan(book.entries, inside, slackMillis = CourseArchive.TIME_SLACK_MILLIS).copy
        if (short.isEmpty()) return null
        return "written, but ${short.size} of ${book.entries.size} files are not in it " +
            "(${short.first().path})"
    }

    /**
     * Copies one book out of its archive and back onto the device.
     *
     * Overwrite, always: the reader asked for the backup's copy, and an
     * incremental restore would skip exactly the files that changed — which
     * are the ones being restored.
     */
    fun restoreArchive(
        from: FileTree,
        book: String,
        to: FileTree,
        onProgress: (done: Int) -> Unit = { },
    ): Report {
        val file = CourseArchive.nameFor(book)
        return try {
            var done = 0
            val out = from.readStream(file).use { stream ->
                CourseArchive.unpack(stream, to) {
                    done++
                    onProgress(done)
                }
            }
            Report(copied = out.files, skipped = 0, bytes = out.bytes, failed = out.failed)
        } catch (e: IOException) {
            Report(
                copied = 0,
                skipped = 0,
                bytes = 0,
                failed = listOf(Failure(file, e.message ?: e::class.java.simpleName)),
            )
        }
    }

    /**
     * The books a folder holds as archives. Sorted and distinct like
     * [coursesIn], and the shelf is not one of them — it is not a book and
     * nothing restores it on its own.
     */
    fun archivedIn(entries: List<FileTree.Entry>): List<String> =
        entries
            .mapNotNull { CourseArchive.bookIn(it.path) }
            .filter { it != CourseArchive.SHELF }
            .distinct()
            .sorted()

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

    // ---------------------------------------------------------------------
    // Sweeping up: the loose files the FIRST version of the backup wrote.
    //
    // The app changed how it writes a backup, so a folder that was backed up
    // by both versions holds each book twice — once packed, once as the
    // directory of separate files it used to write. The loose ones are what a
    // Boox indexes into its Images and Documents libraries, which is the
    // whole reason for the change, so leaving them is leaving the problem.
    //
    // Deleting them is the only thing in this file that destroys anything at
    // the far end, and it is fenced accordingly: see [removeLooseCopies].
    // ---------------------------------------------------------------------

    /**
     * A group of loose files at the far end that an archive beside them
     * already holds — one course, or [the shelf][CourseArchive.SHELF] for the
     * odds and ends at the root that belong to no course.
     */
    data class LooseCopy(val name: String, val entries: List<FileTree.Entry>) {
        val isShelf: Boolean get() = name == CourseArchive.SHELF
        val bytes: Long get() = entries.sumOf { it.size }

        /**
         * What to actually remove. A course is one directory; the shelf is
         * whatever is lying at the root — `last_read.json` and the `labels/`
         * folder today, and whatever a later version drops there.
         */
        val roots: List<String>
            get() = entries.map { it.path.substringBefore('/') }.distinct().sorted()
    }

    /**
     * The loose copies in a backup folder: files that are there the old way
     * AND packed into an archive sitting beside them.
     *
     * The pairing is by name only, which is enough to OFFER the tidy-up and
     * nowhere near enough to carry it out — [removeLooseCopies] opens the
     * archive and checks. A loose folder with no archive beside it is left
     * out entirely: that is a book which is no longer on the device, and the
     * loose copy is the only one left of it.
     */
    fun looseCopiesIn(entries: List<FileTree.Entry>): List<LooseCopy> {
        val slugs = coursesIn(entries).toSet()
        val packed = entries.mapNotNull { CourseArchive.bookIn(it.path) }.toSet()
        return entries
            // The archives themselves sit at the root and are not loose.
            .filter { CourseArchive.bookIn(it.path) == null }
            .groupBy { entry ->
                val head = entry.path.substringBefore('/', "")
                if (head in slugs) head else CourseArchive.SHELF
            }
            .filterKeys { it in packed }
            .map { (name, group) -> LooseCopy(name, group.sortedBy { it.path }) }
            .sortedBy { it.name }
    }

    /** What a sweep managed. [failed] names each group that was left alone, and why. */
    data class Swept(
        val removed: List<String>,
        val files: Int,
        val bytes: Long,
        val failed: List<Failure>,
    ) {
        val ok: Boolean get() = failed.isEmpty()
    }

    /**
     * Deletes the loose copies in [to], each one only after the archive
     * beside it has been opened and found to hold every file being deleted.
     *
     * Three rules make this safe enough to be a button.
     *
     * It re-reads the folder rather than taking a listing from the caller.
     * The screen's idea of what is superseded was formed when it loaded; this
     * runs when a finger lands, and the decision has to be made against the
     * folder as it is at that moment.
     *
     * It reads the archive with `deep = true`, which pulls every byte past a
     * CRC. Listing the names would be enough to know the files are in there,
     * and it is the wrong question: this is the moment the other copy stops
     * existing, so the survivor being INTACT is exactly what wants checking.
     * It costs a pass over the archive — about eight seconds for a real shelf
     * on the Boox — which is nothing against a deliberate, destructive tap.
     *
     * It compares BY PATH, and deliberately not with [plan]. Every other
     * comparison in this file asks "is the destination as new as the source";
     * asking that here would refuse every real case, because the archive is
     * the NEWER copy and the loose files at the far end are stamped with the
     * moment they were copied rather than the moment they were written. What
     * is being asked is narrower and is the thing that actually matters: is
     * anything lost by deleting this. Nothing is, if every loose path is in
     * the archive — a differing size just means the archive has the newer
     * chapter, which is the copy being kept. A path the archive does NOT hold
     * stops that group dead: it is a file the backup would forget.
     */
    fun removeLooseCopies(
        to: FileTree,
        only: Set<String>? = null,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Swept {
        val groups = looseCopiesIn(to.list())
            .filter { only == null || it.name in only }
        val removed = mutableListOf<String>()
        val failed = mutableListOf<Failure>()
        var files = 0
        var bytes = 0L
        var done = 0
        val total = groups.sumOf { it.entries.size }

        groups.forEach { group ->
            val archive = CourseArchive.nameFor(group.name)
            val inside = try {
                to.readStream(archive).use { CourseArchive.listing(it, deep = true) }
            } catch (e: IOException) {
                failed += Failure(archive, "could not be read, so nothing was deleted " +
                    "(${e.message ?: "damaged"})")
                done += group.entries.size
                onProgress(done, total)
                return@forEach
            }

            val held = inside.map { it.path }.toSet()
            var wholeGroup = true

            // Root by root, not group by group. A course has exactly one root
            // so the two are the same thing there — but the shelf's roots are
            // unrelated items that happen to share the top of the folder, and
            // one stray file the reader dropped in should not keep
            // `last_read.json` lying about for ever.
            group.roots.forEach { root ->
                val under = group.entries.filter {
                    it.path == root || it.path.startsWith("$root/")
                }
                val missing = under.filter { it.path !in held }
                if (missing.isNotEmpty()) {
                    wholeGroup = false
                    failed += Failure(
                        archive,
                        "does not hold ${missing.size} of the ${under.size} older files in " +
                            "$root (${missing.first().path}), so they were kept",
                    )
                } else {
                    try {
                        to.delete(root)
                        files += under.size
                        bytes += under.sumOf { it.size }
                    } catch (e: IOException) {
                        wholeGroup = false
                        failed += Failure(root, e.message ?: e::class.java.simpleName)
                    }
                }
                done += under.size
                onProgress(done, total)
            }
            if (wholeGroup) removed += group.name
        }
        return Swept(removed = removed, files = files, bytes = bytes, failed = failed)
    }
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
     * Reads one file as a stream, for content too big to want twice in
     * memory — an archive of a whole book. The caller closes it.
     *
     * The default reads the bytes and hands back a stream over them, so an
     * implementation only overrides this if it can do better. [LocalFileTree]
     * and the document-tree one both can.
     */
    fun readStream(path: String): InputStream = ByteArrayInputStream(read(path))

    /**
     * Writes one file by streaming into it. Same bargain as [readStream]: the
     * default buffers and falls back to [write], so nothing has to implement
     * it, but the implementations that matter do — packing a 15 MB book
     * through a document provider should never hold 15 MB.
     */
    fun writeStream(path: String, body: (OutputStream) -> Unit) {
        val buffer = ByteArrayOutputStream()
        body(buffer)
        write(path, buffer.toByteArray())
    }

    /**
     * Sets a file's modified time, where that is possible at all.
     *
     * A no-op by default, and a no-op forever on the document-tree side —
     * Android's document API offers no way to stamp a file, which is the
     * whole reason [CourseBackup.plan] compares "at least as new as" instead
     * of "the same as". Restoring from an archive DOES use it: the archive
     * remembers when each file was really written, and putting that back is
     * what stops a restored book from looking freshly changed and being
     * repacked in full by the next backup.
     */
    fun touch(path: String, modified: Long) = Unit

    /**
     * Removes a file, or a directory and everything under it.
     *
     * The default refuses, because most of what a [FileTree] is asked to do
     * is copying and a tree that cannot delete should say so rather than
     * quietly doing nothing — a silent no-op here would report a tidy-up that
     * did not happen. Only [CourseBackup.removeLooseCopies] calls it.
     */
    fun delete(path: String): Unit = throw IOException("this folder cannot delete $path")

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

    override fun readStream(path: String): InputStream = File(root, path).inputStream().buffered()

    override fun writeStream(path: String, body: (OutputStream) -> Unit) {
        val target = File(root, path)
        target.parentFile?.mkdirs()
        FileOutputStream(target).buffered().use(body)
    }

    override fun touch(path: String, modified: Long) {
        if (modified > 0) File(root, path).setLastModified(modified)
    }

    override fun delete(path: String) {
        val target = File(root, path)
        if (!target.exists()) return
        // Kotlin's deleteRecursively returns false rather than throwing, and
        // it returns false having deleted SOME of the tree. The caller has to
        // hear about that: a half-removed folder is the one outcome a tidy-up
        // must not report as done.
        if (!target.deleteRecursively()) throw IOException("could not delete $path")
    }
}
