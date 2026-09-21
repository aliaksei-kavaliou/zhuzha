package com.inkfirst.core

/**
 * `reader.md` as something a person can look at and argue with.
 *
 * [ReaderSignals] writes the file and [buildChapterPrompt] reads it, so the
 * book has been forming opinions about its reader since September with no way
 * for that reader to see them. Design note §13 says why that is not
 * acceptable: if the book is going to decide what you found hard, you need to
 * be able to read it and cross one out. This is the parse that makes a screen
 * possible.
 *
 * Deliberately a VIEW over the raw text rather than a model of it. Every
 * observation carries the index of the line it came from, and editing removes
 * those exact lines from the original string — so the file survives hand
 * editing unchanged, which is the promise made when it was made a plain file
 * in the first place. A parse-and-re-render round trip would quietly
 * normalise away anything the reader had written themselves.
 */
object ReaderNotes {

    /** One line the reader can strike out. [lineIndex] indexes the raw text. */
    data class Observation(val lineIndex: Int, val text: String)

    /**
     * A lead-in line and the bullets under it — "Asked 2 questions:" and the
     * questions. [label] is null for bullets that stand on their own, which
     * is what a hand-edited file tends to look like.
     */
    data class Group(
        val labelLineIndex: Int?,
        val label: String?,
        val observations: List<Observation>,
    )

    /** One chapter's section. [headingLines] covers the heading and its title comment. */
    data class Section(
        val chapterN: Int?,
        val title: String?,
        val headingLines: List<Int>,
        val groups: List<Group>,
    ) {
        val observations: List<Observation> get() = groups.flatMap { it.observations }
    }

    private val HEADING = Regex("""^##\s*Chapter\s+(\d+)\s*[—–-]""", RegexOption.IGNORE_CASE)
    private val COMMENT = Regex("""^<!--(.*)-->\s*$""")
    private val BULLET = Regex("""^\s*[-*]\s+(.*)$""")

    /**
     * Splits the file into per-chapter sections.
     *
     * Ragged input is tolerated rather than rejected, for the same reason the
     * Q&A transcript tolerates it: this file is meant to be edited by hand,
     * and someone who reflows a line should not be shown an empty screen.
     * Anything before the first heading, or under no heading at all, lands in
     * a section with a null chapter number.
     */
    fun parse(markdown: String): List<Section> {
        if (markdown.isBlank()) return emptyList()
        val lines = markdown.lines()
        val sections = mutableListOf<Section>()

        var chapterN: Int? = null
        var title: String? = null
        var headingLines = mutableListOf<Int>()
        var groups = mutableListOf<Group>()
        var label: String? = null
        var labelLine: Int? = null
        var bullets = mutableListOf<Observation>()

        fun closeGroup() {
            if (label != null || bullets.isNotEmpty()) {
                groups += Group(labelLine, label, bullets.toList())
            }
            label = null
            labelLine = null
            bullets = mutableListOf()
        }

        fun closeSection() {
            closeGroup()
            if (groups.isNotEmpty() || headingLines.isNotEmpty()) {
                sections += Section(chapterN, title, headingLines.toList(), groups.toList())
            }
            chapterN = null
            title = null
            headingLines = mutableListOf()
            groups = mutableListOf()
        }

        lines.forEachIndexed { i, raw ->
            val line = raw.trim()
            val heading = HEADING.find(line)
            when {
                heading != null -> {
                    closeSection()
                    chapterN = heading.groupValues[1].toIntOrNull()
                    headingLines = mutableListOf(i)
                }
                COMMENT.matches(line) -> {
                    // The chapter title, parked in a comment so the file reads
                    // as prose. Belongs to the heading, not to the body.
                    if (title == null && groups.isEmpty() && bullets.isEmpty()) {
                        title = COMMENT.find(line)!!.groupValues[1].trim().ifBlank { null }
                        headingLines += i
                    }
                }
                line.isEmpty() -> Unit
                BULLET.matches(line) ->
                    bullets += Observation(i, BULLET.find(line)!!.groupValues[1].trim())
                else -> {
                    // A line that is not a bullet opens a new group: it is
                    // either a lead-in for the bullets below it or a sentence
                    // the reader wrote themselves.
                    closeGroup()
                    label = line
                    labelLine = i
                }
            }
        }
        closeSection()
        return sections.filter { it.groups.isNotEmpty() }
    }

    /**
     * The file with [lineIndices] struck out.
     *
     * Cascades, but only over what the removal actually empties: a lead-in
     * with no bullets left under it is noise ("Asked 2 questions:" followed by
     * nothing), and so is a chapter heading with no observations under it. A
     * lead-in that never had bullets is a sentence the reader wrote and is
     * left exactly where they put it.
     */
    fun removeObservations(markdown: String, lineIndices: Set<Int>): String {
        if (lineIndices.isEmpty()) return markdown
        val drop = lineIndices.toMutableSet()
        parse(markdown).forEach { section ->
            var sectionEmptied = section.observations.isNotEmpty()
            section.groups.forEach { group ->
                val had = group.observations.isNotEmpty()
                val allGone = had && group.observations.all { it.lineIndex in lineIndices }
                if (allGone) group.labelLineIndex?.let(drop::add)
                if (had && !allGone) sectionEmptied = false
            }
            if (sectionEmptied) drop += section.headingLines
        }
        val kept = markdown.lines().filterIndexed { i, _ -> i !in drop }
        return tidy(kept)
    }

    /**
     * Collapses the blank lines a removal leaves behind. Two in a row is a
     * paragraph break and stays; three is a hole where something used to be.
     */
    private fun tidy(lines: List<String>): String {
        val out = mutableListOf<String>()
        var blanks = 0
        lines.forEach { line ->
            if (line.isBlank()) {
                blanks++
                if (blanks <= 1) out += ""
            } else {
                blanks = 0
                out += line
            }
        }
        while (out.isNotEmpty() && out.first().isBlank()) out.removeAt(0)
        while (out.isNotEmpty() && out.last().isBlank()) out.removeAt(out.size - 1)
        return if (out.isEmpty()) "" else out.joinToString("\n") + "\n"
    }
}
