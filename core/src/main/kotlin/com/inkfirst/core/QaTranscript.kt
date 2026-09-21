package com.inkfirst.core

/**
 * The chapter Q&A panel's feed, assembled from what is already on disk.
 *
 * The panel used to render `notes/<slug>.qa.md` as one undifferentiated blob
 * of raw Markdown — literal `**Q:**` and `>` on the page — with the passage
 * being asked about pinned above it as a header. That inverted time: the
 * passage just marked is the NEWEST thing that happened, and it sat above
 * answers from days ago.
 *
 * Here the transcript is parsed into turns instead, and a passage marked
 * without a question is a turn of its own. That costs no new storage: the Ask
 * gesture already writes a `kind = "ask"` annotation at mark time (it is what
 * puts the `?` in the margin), and [ReaderSignals] already treats an unasked
 * one as its own signal — "stopped here but did not manage to put the
 * difficulty into words". This just shows the reader what the book already
 * believes about them.
 */
object QaTranscript {

    /**
     * One turn of the feed, oldest first.
     *
     * [created] is what the turn is sorted by. It is an ISO date for anything
     * recorded before this file carried the time of day, and a full ISO
     * timestamp since — which is what makes the feed chronological rather
     * than "all the answered questions, then all the unasked marks". Both
     * forms sort correctly against each other, because a date is a prefix of
     * the timestamps within it.
     */
    sealed interface Entry {
        val created: String

        /** A question that was asked and answered, with the passage it was about. */
        data class Asked(
            override val created: String,
            val passage: String?,
            val question: String,
            val answer: String,
        ) : Entry

        /** A passage marked with Ask where no question was ever typed. */
        data class Marked(
            override val created: String,
            val passage: String,
        ) : Entry
    }

    /**
     * Parses the `.qa.md` transcript written by [CourseStore.appendQa].
     *
     * Entries are separated by `---`; within one, an optional `>` quote block
     * is followed by `**Q:**` and then `**A:**`, whose answer runs to the end
     * of the entry. Ragged input is tolerated rather than rejected — this file
     * is meant to be hand-editable, so a reader who reflows a paragraph or
     * deletes a stray marker should not blank their own history.
     */
    fun parse(qaMarkdown: String): List<Entry.Asked> {
        if (qaMarkdown.isBlank()) return emptyList()
        val out = mutableListOf<Entry.Asked>()

        var created = ""
        val quote = StringBuilder()
        val question = StringBuilder()
        val answer = StringBuilder()
        // Which field trailing lines belong to. The answer is the only
        // multi-line field in practice, but questions get the same treatment
        // so a pasted two-line question survives a round trip.
        var field = Field.NONE

        fun flush() {
            val q = question.toString().trim()
            if (q.isNotEmpty()) {
                out += Entry.Asked(
                    created = created,
                    passage = quote.toString().trim().ifBlank { null },
                    question = q,
                    answer = answer.toString().trim(),
                )
            }
            quote.clear(); question.clear(); answer.clear()
            field = Field.NONE
        }

        for (raw in qaMarkdown.lines()) {
            val line = raw.trim()
            when {
                line == "---" -> flush()
                line.startsWith("## ") -> {
                    // A new date heading also closes whatever came before, in
                    // case the `---` was edited away by hand.
                    flush()
                    created = line.removePrefix("## ").trim()
                }
                line.startsWith(">") -> {
                    quote.appendLine(line.removePrefix(">").trim())
                    field = Field.QUOTE
                }
                line.startsWith("**Q:**") -> {
                    question.appendLine(line.removePrefix("**Q:**").trim())
                    field = Field.QUESTION
                }
                line.startsWith("**A:**") -> {
                    answer.appendLine(line.removePrefix("**A:**").trim())
                    field = Field.ANSWER
                }
                // Continuation. A blank line inside an answer is a paragraph
                // break and has to survive; one anywhere else is separator
                // noise between fields.
                field == Field.ANSWER -> answer.appendLine(raw.trimEnd())
                field == Field.QUESTION && line.isNotEmpty() -> question.appendLine(line)
                else -> Unit
            }
        }
        flush()
        return out
    }

    private enum class Field { NONE, QUOTE, QUESTION, ANSWER }

    /**
     * The whole feed for one chapter, oldest first: every answered question,
     * plus every passage marked with Ask that never became one.
     *
     * [pendingSelection] is the passage the panel was just opened on. It is
     * normally already among [annotations] — the Ask gesture writes the
     * annotation before the panel opens — but it is passed in so the feed is
     * right even when the panel holds a slightly older Course snapshot, and
     * so a repeat Ask on an already-marked passage still shows up at the
     * bottom where the reader is looking.
     */
    fun feed(
        qaMarkdown: String,
        annotations: List<Annotation>,
        chapterSlug: String,
        pendingSelection: String? = null,
        today: String = "",
    ): List<Entry> {
        val asked = parse(qaMarkdown)
        // A passage that HAS a question is represented by its exchange; it
        // must not also appear as a bare mark, or every answered question
        // would show its passage twice.
        val answered = asked.mapNotNull { it.passage?.trim() }.filter { it.isNotEmpty() }.toSet()

        val marked = annotations
            .filter { it.chapterSlug == chapterSlug && it.kind == "ask" }
            .map { it.at.ifBlank { it.created } to it.quote.trim() }
            .filter { (_, q) -> q.isNotEmpty() && q !in answered }

        val seen = mutableSetOf<String>()
        val entries = mutableListOf<Entry>()
        entries += asked
        marked.forEach { (created, q) ->
            if (seen.add(q)) entries += Entry.Marked(created, q)
        }
        val pending = pendingSelection?.trim()
        if (!pending.isNullOrEmpty() && pending !in answered && seen.add(pending)) {
            entries += Entry.Marked(today, pending)
        }

        // ISO stamps, so lexicographic order is chronological. sortedBy is
        // stable, which is what keeps the passage just marked at the bottom
        // where the reader is looking, and what keeps entries that predate
        // the timestamp in the order their own source recorded them.
        return entries.sortedBy { it.created }
    }
}
