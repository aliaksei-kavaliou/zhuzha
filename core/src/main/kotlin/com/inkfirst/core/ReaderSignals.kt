package com.inkfirst.core

/**
 * What the READING looked like — the other half of `memory.md`, which
 * remembers only what the book taught. See docs/design.md §13.
 *
 * Every time the reader marks a passage and asks about it they have told us
 * something: they stopped, and here is the sentence that stopped them. That
 * signal was used once to answer the question and then thrown away. Feeding
 * it forward lets the next chapter open with the recap this reader actually
 * needs, which is the thing a tutor does and a chat window structurally
 * cannot.
 *
 * Two rules from the design note, and both matter more than they look:
 *
 * 1. **Observations, not scores.** No confidence numbers. A model rating
 *    "how well did they understand this?" from one exchange is badly
 *    calibrated, and a number invites false precision — `0.6` means nothing
 *    but reads as if it does, and every downstream prompt treats it as fact.
 *    "Asked twice what a gradient actually is" is more useful in a prompt AND
 *    fails honestly: a wrong observation can be read and seen to be wrong,
 *    where a wrong number just looks precise.
 *
 * 3. **No counts in the lead-in lines.** They used to read "Asked 2
 *    questions:", which was true until the reader crossed one out — and then
 *    the file stated a number the list directly beneath it contradicted. The
 *    count was never load-bearing, since the list says how many; it only
 *    became visible as a defect once the file got a screen and the reader
 *    could edit it. Anything a reader can edit must not carry a summary of
 *    itself.
 *
 * 2. **Nothing here is inferred.** This is assembled from what the reader
 *    did, not from a model's opinion about them — which is why it costs no
 *    API call and can be unit-tested. The file is theirs to read and cross
 *    out, so everything in it has to be something they would recognise.
 */
object ReaderSignals {

    /** A question the reader asked, with the passage it was asked about. */
    data class AskedQuestion(val passage: String?, val question: String)

    /**
     * Pulls questions out of a chapter's `notes/<slug>.qa.md`.
     *
     * Parses rather than stores separately because that file is already the
     * record, and a second copy would be one more thing to keep in step. The
     * format is fixed by [CourseStore.appendQa]: an optional `>` quoted
     * passage, then `**Q:**`, then `**A:**`, entries split by `---`.
     */
    fun parseQuestions(qaMarkdown: String): List<AskedQuestion> {
        if (qaMarkdown.isBlank()) return emptyList()
        val out = mutableListOf<AskedQuestion>()
        var passage: String? = null
        val quoted = StringBuilder()
        for (raw in qaMarkdown.lines()) {
            val line = raw.trim()
            when {
                line.startsWith(">") -> quoted.appendLine(line.removePrefix(">").trim())
                line.startsWith("**Q:**") -> {
                    passage = quoted.toString().trim().ifBlank { null }
                    quoted.clear()
                    val q = line.removePrefix("**Q:**").trim()
                    if (q.isNotEmpty()) out += AskedQuestion(passage, q)
                }
                // A new entry starts: anything buffered belonged to the one
                // just closed and must not leak into the next.
                line == "---" || line.startsWith("## ") -> quoted.clear()
            }
        }
        return out
    }

    /**
     * The "how the reading went" body for one chapter, or null when the
     * reader left no trace at all — in which case saying nothing is right,
     * and better than recording an absence as if it meant something.
     *
     * [annotations] should be the whole course's list; it is filtered here.
     */
    fun notesForChapter(
        chapterSlug: String,
        annotations: List<Annotation>,
        qaMarkdown: String,
    ): String? {
        val mine = annotations.filter { it.chapterSlug == chapterSlug }
        val questions = parseQuestions(qaMarkdown)
        val asked = mine.filter { it.kind == "ask" }
        val written = mine.filter { it.kind != "ask" && it.note.isNotBlank() }

        // A passage marked with Ask whose question never got typed. Still a
        // signal, and a different one: they stopped there but did not manage
        // to put the difficulty into words.
        val questionPassages = questions.mapNotNull { it.passage?.trim() }.toSet()
        val markedOnly = asked
            .map { it.quote.trim() }
            .filter { it.isNotEmpty() && it !in questionPassages }
            .distinct()

        if (questions.isEmpty() && markedOnly.isEmpty() && written.isEmpty()) return null

        return buildString {
            if (questions.isNotEmpty()) {
                appendLine("Asked about:")
                questions.forEach { q ->
                    if (q.passage != null) {
                        appendLine("- On \"${oneLine(q.passage)}\" — asked: ${oneLine(q.question)}")
                    } else {
                        appendLine("- Asked: ${oneLine(q.question)}")
                    }
                }
                appendLine()
            }
            if (markedOnly.isNotEmpty()) {
                appendLine("Stopped at these passages without asking anything:")
                markedOnly.forEach { appendLine("- \"${oneLine(it)}\"") }
                appendLine()
            }
            if (written.isNotEmpty()) {
                appendLine("Wrote these notes:")
                written.forEach {
                    appendLine("- On \"${oneLine(it.quote)}\" — ${oneLine(it.note)}")
                }
            }
        }.trim()
    }

    /**
     * Quotes get long, and a prompt full of half-paragraphs buries the point.
     * Trimmed to the first clause-ish span, on a whitespace boundary so a cut
     * never lands mid-word.
     */
    private fun oneLine(text: String, limit: Int = 90): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        if (flat.length <= limit) return flat
        val cut = flat.lastIndexOf(' ', limit)
        return flat.substring(0, if (cut > limit / 2) cut else limit).trimEnd() + "…"
    }
}
