package com.inkfirst.core

/**
 * Surgery on `memory.md` — the running record of what the book has TAUGHT,
 * which every later chapter is written against (docs/design.md §3).
 *
 * Appending to it is the normal case and lives in [CourseStore.appendMemory].
 * This exists for the abnormal one: a chapter rewritten after the fact, whose
 * old summary now describes prose that no longer exists. Left in place it
 * would keep feeding forward — later chapters would build on metaphors the
 * reader never saw.
 *
 * Pure string work, so it is unit-tested rather than reasoned about. The file
 * is meant to be hand-editable, so everything here tolerates ragged input and
 * never throws: the worst outcome of a format it does not recognise is that
 * the new summary is appended instead of replaced.
 */
object CourseMemory {

    /** Matches the block header [CourseStore.appendMemory] writes. */
    private val HEADER = Regex("""^--- Chapter (\d+):.*---\s*$""")

    /**
     * Splits [memory] into chapter blocks, each running from its header to
     * the next one. Anything before the first header is kept as a preamble
     * under chapter number null.
     */
    private fun blocks(memory: String): List<Pair<Int?, String>> {
        val out = mutableListOf<Pair<Int?, String>>()
        var current: Int? = null
        val buffer = StringBuilder()
        memory.lineSequence().forEach { line ->
            val header = HEADER.find(line.trim())
            if (header != null) {
                if (buffer.isNotEmpty()) out += current to buffer.toString()
                buffer.clear()
                current = header.groupValues[1].toIntOrNull()
            }
            buffer.appendLine(line)
        }
        if (buffer.isNotEmpty()) out += current to buffer.toString()
        return out
    }

    /** [memory] with chapter [chapterN]'s block removed, if it had one. */
    fun withoutChapter(memory: String, chapterN: Int): String =
        blocks(memory).filterNot { it.first == chapterN }
            .joinToString("") { it.second }
            .trimEnd() + if (memory.isBlank()) "" else "\n"

    /**
     * [memory] with chapter [chapterN]'s summary replaced in place, keeping
     * its position among the others. Appended at the end if the chapter has
     * no block yet — which is what happens when a chapter that failed its
     * memory pass is later rewritten.
     */
    fun replaceChapter(memory: String, chapterN: Int, chapterTitle: String, summary: String): String {
        val replacement = buildString {
            appendLine("--- Chapter $chapterN: $chapterTitle ---")
            appendLine(summary.trim())
            appendLine()
        }
        val existing = blocks(memory)
        if (existing.none { it.first == chapterN }) {
            val head = memory.trimEnd()
            return if (head.isEmpty()) replacement else head + "\n\n" + replacement
        }
        return existing.joinToString("") { (n, body) ->
            if (n == chapterN) replacement else body
        }
    }
}
