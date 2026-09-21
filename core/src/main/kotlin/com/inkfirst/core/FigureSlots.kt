package com.inkfirst.core

/**
 * Locating and annotating ```figure fences in raw chapter Markdown.
 *
 * Deliberately a *line scanner over the raw text*, not a pass over
 * [parseMarkdown]'s output: filling in a generated picture means writing a
 * `src="…"` param back into the chapter file, and rewriting from parsed
 * blocks would mean re-serialising the whole document (lossy, and it would
 * touch prose the user may have annotated). Scanning lines lets us edit
 * exactly one line and leave every other byte alone.
 *
 * Fence recognition mirrors [parseMarkdown] — including the single-line
 * ```figure alt="…"``` shape the model routinely emits — so ordinals here
 * always line up with the figure blocks the renderer sees.
 */
/**
 * What a figure is FOR, which decides how its brief is drawn.
 *
 * [MOOD] is the original and remains the default: a silly visual metaphor
 * that stops a study page reading as a wall of text. [SHOW] is for the
 * passages where the reader has to SEE the thing to do it.
 *
 * The split was forced by a real page (2026-09-21). A chapter teaching how to
 * hold a pencil — fingers back from the tip, resting on the middle joint —
 * was illustrated with "a very relaxed capybara holding a giant wooden pencil
 * in a sunny meadow". A good picture that teaches nothing about the grip, and
 * not the model's fault: the chapter prompt told it to describe a SCENE and
 * gave rejecting a diagram as the worked example.
 *
 * This is NOT the semantic diagram DSL that design.md §7 rejected. That was
 * boxes and arrows rendered natively — accurate schematics nobody enjoys
 * looking at. A SHOW figure is still a hand-drawn picture from the image
 * model in the same house style; only its job on the page is different.
 */
enum class FigureKind {
    MOOD,
    SHOW,
    ;

    companion object {
        /** Parses the fence's `kind="…"` param. Anything unrecognised is [MOOD]. */
        fun parse(raw: String?): FigureKind =
            if (raw?.trim()?.lowercase() == "show") SHOW else MOOD
    }
}

data class FigureSlot(
    /** 1-based position among the figures of this chapter, in document order. */
    val ordinal: Int,
    /** Index into `markdown.lines()` of the fence's opening line. */
    val lineIndex: Int,
    /** The `alt="…"` param — the human-readable description of the picture. */
    val alt: String,
    /** Fence body for multi-line figure blocks; empty for the single-line shape. */
    val body: String,
    /** The `src="…"` param once a picture has been generated; null while empty. */
    val src: String?,
    /** What the picture is for — see [FigureKind]. Absent param reads as [FigureKind.MOOD]. */
    val kind: FigureKind = FigureKind.MOOD,
) {
    /** What to actually draw — alt text, falling back to the fence body. */
    val brief: String get() = alt.ifBlank { body.trim() }

    /** A slot still waiting for a picture. Blank-brief slots are unillustratable. */
    val isPending: Boolean get() = src == null && brief.isNotBlank()
}

/** Every figure fence in [markdown], in document order. */
fun findFigureSlots(markdown: String): List<FigureSlot> {
    val lines = markdown.lines()
    val slots = mutableListOf<FigureSlot>()
    var ordinal = 0
    var i = 0

    while (i < lines.size) {
        val trimmed = lines[i].trim()
        if (!trimmed.startsWith("```")) {
            i++
            continue
        }

        val openerIndex = i
        val afterOpen = trimmed.removePrefix("```")
        val closeOnSameLine = afterOpen.indexOf("```")
        val info = if (closeOnSameLine >= 0) {
            afterOpen.substring(0, closeOnSameLine).trim()
        } else {
            afterOpen.trim()
        }

        // Multi-line fence: consume the body so a ```figure appearing inside
        // some other block's body is never counted as a slot of its own.
        val body = StringBuilder()
        if (closeOnSameLine < 0) {
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                if (body.isNotEmpty()) body.append('\n')
                body.append(lines[i])
                i++
            }
        }

        // `fenceKind` is which FENCE this is (figure / exercise / recap);
        // FigureSlot.kind is what the figure is FOR. Different questions.
        val (fenceKind, params) = parseFenceInfo(info)
        if (fenceKind == "figure") {
            ordinal++
            slots += FigureSlot(
                ordinal = ordinal,
                lineIndex = openerIndex,
                alt = params["alt"].orEmpty(),
                body = body.toString(),
                src = params["src"]?.ifBlank { null },
                kind = FigureKind.parse(params["kind"]),
            )
        }
        i++
    }
    return slots
}

/**
 * Returns [markdown] with `src="…"` written into the figure fences named by
 * [srcByOrdinal]. Every other line is preserved byte-for-byte, so block
 * count and block indices are unchanged — existing annotations and ink
 * strokes stay anchored to the same passages.
 */
fun withFigureSrc(markdown: String, srcByOrdinal: Map<Int, String>): String {
    if (srcByOrdinal.isEmpty()) return markdown

    val byLine = findFigureSlots(markdown)
        .mapNotNull { slot -> srcByOrdinal[slot.ordinal]?.let { slot.lineIndex to it } }
        .toMap()
    if (byLine.isEmpty()) return markdown

    val lines = markdown.lines().toMutableList()
    for ((lineIndex, src) in byLine) {
        lines[lineIndex] = withSrcParam(lines[lineIndex], src)
    }
    return lines.joinToString("\n")
}

/**
 * Grabs a little prose around a figure slot so the image prompt knows what
 * the page is actually talking about. Fence lines and headings are skipped —
 * we want sentences, not structure.
 */
internal fun contextAround(markdown: String, slot: FigureSlot, maxChars: Int = 600): String {
    val lines = markdown.lines()
    val picked = mutableListOf<String>()
    var before = slot.lineIndex - 1
    var after = slot.lineIndex + 1

    fun usable(index: Int): String? = lines.getOrNull(index)
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.startsWith("```") && !it.startsWith("#") }

    // Alternate outwards from the fence so the picture gets the sentences
    // immediately on either side of it rather than a one-sided run.
    while (picked.sumOf { it.length } < maxChars && (before >= 0 || after < lines.size)) {
        usable(before)?.let { picked.add(0, it) }
        before--
        if (picked.sumOf { it.length } >= maxChars) break
        usable(after)?.let { picked.add(it) }
        after++
    }
    return picked.joinToString(" ").take(maxChars)
}

private val SRC_PARAM = Regex("""\s*src="[^"]*"""")

private fun withSrcParam(line: String, src: String): String {
    val indent = line.takeWhile { it.isWhitespace() }
    val afterOpen = line.trim().removePrefix("```")
    val closeAt = afterOpen.indexOf("```")
    val info = if (closeAt >= 0) afterOpen.substring(0, closeAt) else afterOpen
    val tail = if (closeAt >= 0) afterOpen.substring(closeAt) else ""
    val cleaned = info.replace(SRC_PARAM, "").trimEnd()
    return """$indent```$cleaned src="$src"$tail"""
}
