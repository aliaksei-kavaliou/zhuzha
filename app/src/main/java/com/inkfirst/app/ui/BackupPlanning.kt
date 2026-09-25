package com.inkfirst.app.ui

import com.inkfirst.core.CourseBackup
import com.inkfirst.core.FileTree
import java.util.Locale

/**
 * The arithmetic and the wording behind the backup screen, kept out of the
 * screen so it can be tested without a device — same reason as [TapRouting].
 * Nothing here touches Android or a filesystem: it takes listings and returns
 * sentences.
 */

/**
 * The files belonging to one course, by the same rule the app stores them:
 * everything under a directory named for the slug.
 *
 * Restoring one book out of a backup of five needs no new machinery — the
 * paths on both sides are relative to the courses root, so narrowing the
 * SOURCE listing is the whole of it, and `CourseBackup.plan` never has to
 * learn what a course is. The prefix carries its "/" so a course called
 * "kotlin" does not drag in "kotlin-advanced".
 */
internal fun entriesForCourse(entries: List<FileTree.Entry>, slug: String): List<FileTree.Entry> =
    entries.filter { it.path.startsWith("$slug/") }

/** "912 B", "48 KB", "14.8 MB" — sizes for a sentence, not for accounting. */
internal fun humanBytes(bytes: Long): String = when {
    bytes < 1_000 -> "$bytes B"
    bytes < 1_000_000 -> "${(bytes + 512) / 1_024} KB"
    bytes < 1_000_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    else -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
}

/** "3 books · 412 files · 14.8 MB" — what is on this device, or in a backup. */
internal fun describeShelf(entries: List<FileTree.Entry>): String {
    if (entries.isEmpty()) return "Nothing here yet."
    val books = CourseBackup.coursesIn(entries).size
    val bytes = entries.sumOf { it.size }
    return listOf(
        countOf(books, "book", "books"),
        countOf(entries.size, "file", "files"),
        humanBytes(bytes),
    ).joinToString(" · ")
}

/** What a run is about to do, before any bytes move. */
internal fun describePlan(plan: CourseBackup.Plan): String {
    if (plan.copy.isEmpty()) {
        return if (plan.skip.isEmpty()) "Nothing to copy."
        else "Already up to date — ${countOf(plan.skip.size, "file", "files")} unchanged."
    }
    val head = "${countOf(plan.copy.size, "file", "files")} to copy, ${humanBytes(plan.bytes)}"
    return if (plan.skip.isEmpty()) "$head."
    else "$head · ${countOf(plan.skip.size, "file", "files")} already there."
}

/**
 * How a run went. Failures are named rather than counted — "3 files failed"
 * tells the reader they have an incomplete backup and nothing about whether
 * it matters, while "chapters/03-loops.md" tells them exactly what is missing
 * from it.
 */
internal fun describeReport(report: CourseBackup.Report): String {
    val copied = if (report.copied == 0) "Nothing needed copying"
    else "Copied ${countOf(report.copied, "file", "files")}, ${humanBytes(report.bytes)}"
    val skipped = if (report.skipped == 0) "" else " · ${report.skipped} already there"
    if (report.ok) return "$copied$skipped."

    val named = report.failed.take(MAX_NAMED_FAILURES).joinToString(", ") { it.path }
    val rest = report.failed.size - MAX_NAMED_FAILURES
    val tail = if (rest > 0) ", and $rest more" else ""
    return "$copied$skipped. ${countOf(report.failed.size, "file", "files")} could not be " +
        "copied: $named$tail."
}

/**
 * What is in the backup folder — books and bytes, no file count.
 *
 * A book is one zip there now, so "3 books · 3 files · 21 MB" would be
 * counting the wrong thing at the reader. The device side still counts files,
 * because there the number means something.
 */
internal fun describeBackup(entries: List<FileTree.Entry>): String {
    if (entries.isEmpty()) return "Nothing here yet."
    val books = booksInBackup(entries).size
    return "${countOf(books, "book", "books")} · ${humanBytes(entries.sumOf { it.size })}"
}

/**
 * Every book the folder can restore, however it got there: packed as
 * `<slug>.zip` by this version, or left as a folder of loose files by the
 * first one. Older backups keep working — the app changed how it writes, and
 * a backup's whole job is to still be there after the app changes.
 */
internal fun booksInBackup(entries: List<FileTree.Entry>): List<String> =
    (CourseBackup.archivedIn(entries) + CourseBackup.coursesIn(entries)).distinct().sorted()

/**
 * Books sitting in the folder twice: packed AND still there as the loose
 * folder an earlier backup wrote. Only these are safe to name as deletable —
 * a loose folder with no zip beside it is a book that is not on the device
 * any more, and it is the only copy left of it.
 */
internal fun supersededCopies(entries: List<FileTree.Entry>): List<String> {
    val packed = CourseBackup.archivedIn(entries).toSet()
    return CourseBackup.coursesIn(entries).filter { it in packed }
}

/**
 * What a packing run is about to do, before any bytes move.
 *
 * The shelf archive is not counted. It is not a book — it holds where you
 * left off and the translated chrome — and "4 books" on a shelf of three is
 * the kind of small lie that makes a reader stop trusting the rest of the
 * sentence.
 */
internal fun describeArchivePlan(plan: CourseBackup.ArchivePlan): String {
    val toPack = plan.write.count { !it.isShelf }
    val unchanged = plan.keep.count { !it.isShelf }
    if (toPack == 0) {
        return if (plan.keep.isEmpty()) "Nothing to back up."
        else "Already up to date — ${countOf(unchanged, "book", "books")}, " +
            "${countOf(plan.kept, "file", "files")} checked."
    }
    val head = "${countOf(toPack, "book", "books")} to pack, ${humanBytes(plan.bytes)}"
    return if (unchanged == 0) "$head."
    else "$head · ${countOf(unchanged, "book", "books")} unchanged."
}

/**
 * How a packing run went. It says "checked" because it is true and because it
 * is the thing a reader wants to know: every archive written was read back
 * and its contents compared against the book, CRC by CRC.
 */
internal fun describeArchiveReport(
    plan: CourseBackup.ArchivePlan,
    report: CourseBackup.Report,
): String {
    val packed = plan.write.count { !it.isShelf }
    val unchanged = plan.keep.count { !it.isShelf }
    val head = when {
        packed > 0 -> "Packed ${countOf(packed, "book", "books")} — " +
            "${countOf(report.copied, "file", "files")}, ${humanBytes(report.bytes)}"
        // Only the shelf changed: nothing was read tonight but the app noted
        // where you left off. Saying "packed 1 book" of that would be a lie.
        plan.write.isNotEmpty() -> "The books were already backed up — " +
            "only where you left off had changed"
        else -> "Nothing needed packing"
    }
    val kept = if (unchanged == 0) "" else " · ${countOf(unchanged, "book", "books")} unchanged"
    if (report.ok) return "$head$kept, read back and checked."

    // The reason is named here where [describeReport] names only the path:
    // a per-file failure says what it is by naming the file, while "the
    // archive came back three files short" is the whole of what went wrong.
    val named = report.failed
        .take(MAX_NAMED_FAILURES)
        .joinToString("; ") { "${it.path} — ${it.reason}" }
    val rest = report.failed.size - MAX_NAMED_FAILURES
    val tail = if (rest > 0) ", and $rest more" else ""
    return "$head$kept. ${countOf(report.failed.size, "problem", "problems")}: $named$tail. " +
        "Backing up again is the fix — a book that did not arrive whole is packed again."
}

private fun countOf(n: Int, one: String, many: String): String =
    if (n == 1) "1 $one" else "$n $many"

private const val MAX_NAMED_FAILURES = 3
