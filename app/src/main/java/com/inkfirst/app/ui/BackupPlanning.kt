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

private fun countOf(n: Int, one: String, many: String): String =
    if (n == 1) "1 $one" else "$n $many"

private const val MAX_NAMED_FAILURES = 3
