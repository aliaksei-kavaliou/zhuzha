package com.inkfirst.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkfirst.core.Block
import com.inkfirst.core.FIGURE_ASPECT
import com.inkfirst.core.badgeKindFor
import com.inkfirst.core.splitExerciseBody
import com.inkfirst.core.parseInlines
import com.inkfirst.core.Inline

/**
 * Packs a chapter's Blocks into pages of at most [contentHeightPx] tall.
 * V2.0 discipline: every block is atomic — a block that doesn't fit on the
 * current page moves whole to the next. Consequence: some pages will end
 * with visible whitespace. Paragraph splitting is a follow-up (v2.1).
 *
 * [fontScale] multiplies every text style (fontSize + lineHeight) uniformly.
 * The renderer must use the same scale for the layout to match this pack.
 *
 * [hasBadge] answers "does this course have a mascot badge for this block
 * kind?" — a badge makes an exercise/recap label row taller than the label
 * text alone, and the renderer must agree or the page overflows.
 *
 * [hasFigureImage] answers "did this figure's `src` actually get generated?".
 * An illustrated figure is measured as a fixed-ratio picture; one still
 * waiting for its picture is measured as the alt-text box the reader draws
 * instead. The renderer must answer the same question the same way or pages
 * will overflow.
 */
/**
 * One block, or part of one, placed on a page.
 *
 * [index] is the block's position in the chapter's block list, carried
 * explicitly because a paragraph may now appear on two consecutive pages —
 * the old "sum the sizes of earlier pages" arithmetic would double-count it.
 * Annotations, bookmarks and ink all anchor to this index, and splitting a
 * paragraph must not change it.
 *
 * [skippedPx] is the height of this block's lines shown on earlier pages, so
 * a continuation can report a *virtual* block top — the y the block would
 * have started at had it not been split. Every stored stroke offset is
 * relative to that, so ink keeps working across a split with no change to the
 * on-disk format: a stroke on a line that is now on the previous page simply
 * clips off the top.
 */
data class PageItem(
    val block: Block,
    val index: Int,
    val skippedPx: Int = 0,
)

fun paginate(
    blocks: List<Block>,
    contentWidthPx: Int,
    contentHeightPx: Int,
    density: Density,
    measurer: TextMeasurer,
    fontScale: Float = 1.0f,
    lineSpacing: Float = 1.0f,
    hasFigureImage: (String) -> Boolean = { false },
    hasBadge: (String) -> Boolean = { false },
    inkTopMarginPx: Int = 0,
    /** The book's own word for "Answer:", measured as the renderer draws it. */
    answerPrefix: String = "Answer:",
): List<List<PageItem>> {
    if (blocks.isEmpty() || contentWidthPx <= 0 || contentHeightPx <= 0) {
        return listOf(blocks.mapIndexed { i, b -> PageItem(b, i) })
    }

    // Safety margin: TextMeasurer under-counts by roughly a half-line per
    // block (font metric slop + sub-pixel rounding). 5% wasn't enough — still
    // saw single-line clipping. 10% covers observed cases.
    // The reader reserves INK_TOP_MARGIN above the first block so the first
    // line can be stroked from above; the pack has to fit in what is left.
    val budget = ((contentHeightPx - inkTopMarginPx) * 0.90f).toInt()

    val lineHeightPx = with(density) { (30.sp * fontScale * lineSpacing).toPx().toInt() }

    val pages = mutableListOf<MutableList<PageItem>>()
    pages.add(mutableListOf())
    var height = 0

    fun newPage() {
        pages.add(mutableListOf())
        height = 0
    }

    var i = 0
    // Set when a paragraph was split: the text still to place, and how much of
    // the original block's height has already been shown.
    // What a split left over, waiting for the next page. Widened from
    // Block.Paragraph when fences learned to split: a bordered block can be
    // carried too, and the loop treats a carried block exactly like any other.
    var carry: Block? = null
    var carriedPx = 0
    // Set when an opener was placed on an empty page and its content would
    // not fit beside it: the next block joins it regardless. See below.
    var forceNext = false

    while (i < blocks.size) {
        val block = carry ?: blocks[i]
        val atTop = pages.last().isEmpty()
        val h = estimateHeight(
            block, contentWidthPx, density, measurer, fontScale, lineSpacing,
            hasFigureImage, hasBadge, atPageTop = atTop, answerPrefix = answerPrefix,
        )

        // Keep-with-next: don't orphan a heading at the bottom of a page. If a
        // heading is about to land and the block after it wouldn't fit on the
        // same page, push the heading too — provided the pair fits together on
        // an empty page, or we would loop.
        val opensSomething = block is Block.Heading ||
            (block is Block.CustomFence && block.kind == "meta")
        val requiredHeight = if (opensSomething && i + 1 < blocks.size) {
            val next = blocks[i + 1]
            // A heading must not be orphaned at the foot of a page — but it
            // only needs ENOUGH of what follows to look attached, not all of
            // it. Requiring the whole next block was what left half-empty
            // pages once paragraphs could split: a heading plus a nine-line
            // paragraph wouldn't fit, so both moved and the rest of the page
            // was lost even though six lines would have gone under it.
            val nextH = if (next is Block.Paragraph) {
                minOf(
                    estimateHeight(
                        next, contentWidthPx, density, measurer, fontScale, lineSpacing,
                        hasFigureImage, hasBadge, answerPrefix = answerPrefix,
                    ),
                    lineHeightPx * MIN_SPLIT_LINES,
                )
            } else {
                estimateHeight(
                    next, contentWidthPx, density, measurer, fontScale, lineSpacing,
                    hasFigureImage, hasBadge, answerPrefix = answerPrefix,
                )
            }
            if (h + nextH <= budget) h + nextH else h
        } else {
            h
        }

        // Forced, and it does not fit: take the lines that do and carry the
        // rest. The force exists so an opener is not left alone on a page,
        // and a few lines under the title satisfy that exactly as well as the
        // whole paragraph — which is the rule `requiredHeight` above already
        // applies when it caps the next block at MIN_SPLIT_LINES. Only the
        // PLACEMENT had not been taught it, so the whole paragraph went down
        // on a page with room for two lines of it and the third came back
        // sliced in half by the footer. Reported from chapter 2 of a real
        // book, 2026-09-29: meta byline + title + a 4:3 cover leaves very
        // little of the first page, which is exactly when this bites.
        if (forceNext && height + h > budget && block is Block.Paragraph) {
            val forcedSplit = splitParagraph(
                block, budget - height, contentWidthPx, measurer, fontScale, lineSpacing, density,
            )
            if (forcedSplit != null) {
                pages.last().add(PageItem(forcedSplit.head, i, carriedPx))
                carry = forcedSplit.tail
                carriedPx += forcedSplit.headPx
                forceNext = false
                newPage()
                continue
            }
            // Nothing splittable — too little room even for MIN_SPLIT_LINES.
            // Fall through and place it whole: an opener alone on a page is
            // the worse of the two, and this is the case the 10% budget
            // margin exists to absorb.
        }

        if (forceNext || height + requiredHeight <= budget) {
            pages.last().add(PageItem(block, i, carriedPx))
            height += h
            carry = null
            carriedPx = 0
            // Placed on a PROMISE, so keep it. The keep-with-next check above
            // asked whether the next block fits beside this opener and placed
            // it because the answer was yes — but the next block runs its own
            // check when its turn comes, and a heading demands room for the
            // paragraph after IT. So the byline went down alone, the title
            // started a new page, and mergeLonelyOpeners glued the two back
            // together afterwards, where there is no budget left to check
            // against. Worse, the title then costs MORE than was measured:
            // 306px at the top of a page, 346px once something sits above it,
            // because it regains its 40dp top spacer. That 40px plus the
            // paragraph is what ran off the bottom of the page and came back
            // as a line sliced in half by the footer (2026-09-29).
            //
            // So: while this page holds nothing but openers, whatever comes
            // next joins it. The force is safe because the branch above now
            // splits a forced paragraph rather than placing it whole.
            forceNext = pages.last().all { it.opensSomething }
            i++
            continue
        }

        // An opener alone on a page is not a page. The chapter's byline and
        // its title were landing on a sheet of their own whenever the title
        // and its cover picture would not fit beside them — a long title in
        // Russian plus a 4:3 cover does it — so the reader opened the chapter
        // on one grey line and blank paper, with the title overleaf.
        //
        // Done HERE rather than by gluing the two pages together afterwards,
        // which was the first attempt: a page laid out to fill the budget
        // does not have room for a byline added on top, and the last line of
        // its paragraph came back clipped. Forcing the pair during layout
        // lets everything after it re-flow against what is actually left.
        if (atTop && opensSomething) {
            pages.last().add(PageItem(block, i, carriedPx))
            height += h
            carry = null
            carriedPx = 0
            forceNext = true
            i++
            continue
        }

        // A BLOCK WOULD RATHER MOVE THAN BE CUT — and since 2026-10-06 this
        // matters far less than it did. Exercise and recap have no frame to
        // leave open and no name to reintroduce, so a cut costs only what a
        // cut paragraph costs. What still cannot be cut well is the working
        // room, which has to stay with the task, and a figure, which is a
        // picture. So the preference stands, weaker: if the thing fits on a
        // page of its own, give it one.
        //
        // This is the other half of removing the `!atTop` guard below. That
        // guard was wrong as written — it refused to split at the TOP of a
        // page, which sent a box taller than a whole page to the oversize
        // branch that clips it — but it was also, incidentally, what kept
        // boxes off the split path in the ordinary case, and removing it took
        // that with it. So the distinction is restored where it actually
        // belongs: on whether the box fits a fresh page, not on where the
        // layout happens to be standing when it meets one.
        //
        // It cannot loop. A fence's estimate does not depend on `atPageTop`
        // (only a heading's does, for its top spacer), so a box measuring
        // within budget here measures the same on the empty page and is placed
        // there by the fit branch above. `!atTop` is what makes that true:
        // newPage() appends unconditionally, so moving a box off a page that
        // is already empty would spin.

        // SHRINK THE FURNITURE BEFORE REACHING FOR THE SCISSORS.
        //
        // This rung existed from 2026-10-01 and sat BELOW the split, where
        // the split's own `continue` made it unreachable: by the time it ran,
        // the box was already in pieces and it could only tidy the last one.
        // So a box that overflowed by one row of dots was cut across two
        // pages rather than giving up the row — and at a larger font, cut
        // again and again.
        //
        // The room to write in is a courtesy and the words are content, so
        // the courtesy yields first. It yields only down to MIN_WORKING_ROWS
        // here: a box that can fit a page ONLY by losing every row of room is
        // better served by the part split below, which keeps the room whole
        // on a page of its own rather than deleting it to win a tidy page.
        var relaxedFence: Block.CustomFence? = null
        var relaxedFenceH = 0
        if (block is Block.CustomFence && h > budget) {
            var best: Block.CustomFence = block
            var bestH = h
            withShrunkWritingSpace(best, bestH - budget, density, fontScale)?.let { room ->
                best = room
                bestH = estimateHeight(
                    room, contentWidthPx, density, measurer, fontScale, lineSpacing,
                    hasFigureImage, hasBadge, atPageTop = true, answerPrefix = answerPrefix,
                )
            }
            if (bestH > budget && best.params[FENCE_PLAIN_ANSWER] == null) {
                val plain = best.copy(params = best.params + (FENCE_PLAIN_ANSWER to "1"))
                val plainH = estimateHeight(
                    plain, contentWidthPx, density, measurer, fontScale, lineSpacing,
                    hasFigureImage, hasBadge, atPageTop = true, answerPrefix = answerPrefix,
                )
                if (plainH < bestH) {
                    best = plain
                    bestH = plainH
                }
            }
            if (best !== block) {
                relaxedFence = best
                relaxedFenceH = bestH
            }
            // It fits a page now. Place it whole if this page is empty;
            // otherwise give it the fresh page it has earned — a box that
            // fits somewhere whole must never be cut.
            if (bestH <= budget) {
                if (!atTop) {
                    newPage()
                    continue
                }
                pages.last().add(PageItem(best, i, carriedPx))
                height += bestH
                carry = null
                carriedPx = 0
                i++
                continue
            }
        }

        if (block is Block.CustomFence && !atTop && h <= budget) {
            newPage()
            continue
        }

        // Doesn't fit. A paragraph can give up the lines that do rather than
        // surrendering the rest of the page — which is what made a page with
        // six lines free lose all six.
        val remaining = budget - height
        // head, tail, and what the head costs. A Triple rather than a shared
        // supertype because the two splitters return different block types and
        // the loop only needs these three things from either.
        val split: Triple<Block, Block, Int>? = when (block) {
            is Block.Paragraph ->
                splitParagraph(block, remaining, contentWidthPx, measurer, fontScale, lineSpacing, density)
                    ?.let { Triple(it.head, it.tail, it.headPx) }
            // A bordered block can be cut too, since 2026-09-29. Before that a
            // fence taller than a whole page fell through to the oversize
            // branch below, which places it and lets the page clip it — and on
            // a small screen that lost the end of an exercise and its "Show
            // answer" button entirely, with no way to reach either.
            is Block.CustomFence ->
                // The RELAXED form if the rung above made one: having already
                // decided those rows are not affordable, splitting the
                // original would hand them to a piece and then drop them.
                splitFence(
                    relaxedFence ?: block, remaining, contentWidthPx, measurer,
                    fontScale, lineSpacing, density, hasBadge,
                )
                    ?.let { Triple(it.head, it.tail, it.headPx) }
            else -> null
        }

        // No longer refuses to split at the top of a page. That guard sent a
        // block too tall for a whole page to the oversize branch, which is the
        // one that clips; splitting is strictly better, and it terminates
        // because every tail is shorter than what it came from.
        if (split != null) {
            if (block is Block.CustomFence) {
            }
            pages.last().add(PageItem(split.first, i, carriedPx))
            carry = split.second
            carriedPx += split.third
            newPage()
            continue
        }

        // NOTHING LEFT TO CUT AND IT STILL OVERFLOWS A WHOLE PAGE. The page
        // is about to clip it, so now the room goes all the way — a box with
        // no room to write still asks its question and still shows its
        // answer, where a clipped one does neither. This is the rung the
        // polite one above deliberately refuses to take.
        var relaxed: Block.CustomFence? = relaxedFence
        var relaxedH = relaxedFenceH
        if (atTop && block is Block.CustomFence && h > budget) {
            val from = relaxed ?: block
            val fromH = if (relaxed != null) relaxedH else h
            withShrunkWritingSpace(from, fromH - budget, density, fontScale, floor = 0)?.let { bare ->
                relaxed = bare
                relaxedH = estimateHeight(
                    bare, contentWidthPx, density, measurer, fontScale, lineSpacing,
                    hasFigureImage, hasBadge, atPageTop = true, answerPrefix = answerPrefix,
                )
            }
            if (relaxed != null && relaxedH <= budget) {
                pages.last().add(PageItem(relaxed!!, i, carriedPx))
                height += relaxedH
                carry = null
                carriedPx = 0
                i++
                continue
            }
        }

        if (atTop) {
            // Taller than a whole page and nothing to split: place it anyway,
            // or we would loop forever. The relaxed form if the ladder made
            // one — it is smaller, so less of it is lost.
            pages.last().add(PageItem(relaxed ?: block, i, carriedPx))
            height += if (relaxed != null) relaxedH else h
            carry = null
            carriedPx = 0
            i++
            continue
        }
        newPage()
    }
    return mergeLonelyOpeners(pages.map { it.toList() })
}

/**
 * A page that is only a byline is not a page — the backstop.
 *
 * The rule that actually prevents this lives in [paginate]'s loop, where an
 * opener left alone on an empty page pulls the next block onto it and
 * everything after re-flows. This pass catches whatever that cannot: it glues
 * pages that are already laid out, so the page it produces can run slightly
 * over the height budget, which is why it is the second line of defence and
 * not the first.
 *
 * The reader always prepends one synthetic `meta` block — it cannot be
 * dropped, because every annotation, bookmark and ink stroke is indexed
 * against a block list that includes it. When the model wrote the chapter's
 * own title, that meta carries nothing but the byline ("Chapter 1 · ~60 min ·
 * Day 1–2"), and the title and cover picture ride on the heading after it.
 *
 * The keep-with-next rule tries to hold the two together and gives up when
 * the pair will not fit, which is exactly what a long title plus a 4:3 cover
 * does. The reader then opens on a page holding one grey line and an
 * otherwise blank sheet — observed on the Boox with a Russian chapter whose
 * title runs to three lines, 2026-09-23.
 *
 * So: any page that carries nothing but blocks which OPEN something, and
 * which is not the last page, gives them to the page that follows. The cost
 * is that the following page runs slightly over the height budget — but that
 * budget is a 10% safety margin for measurement slop, and spending a line of
 * it to avoid a blank page is the better trade. Blocks keep their order and
 * their indices, so anchors, bookmarks and ink are untouched.
 */
internal fun mergeLonelyOpeners(pages: List<List<PageItem>>): List<List<PageItem>> {
    if (pages.size < 2) return pages
    val out = mutableListOf<List<PageItem>>()
    var carried = emptyList<PageItem>()
    pages.forEachIndexed { index, page ->
        val merged = carried + page
        val isLast = index == pages.lastIndex
        if (!isLast && merged.isNotEmpty() && merged.all { it.opensSomething }) {
            // Nothing here but a byline and headings: hold them for the page
            // that has the content they introduce.
            carried = merged
        } else {
            carried = emptyList()
            if (merged.isNotEmpty()) out += merged
        }
    }
    // A trailing run of openers with nothing after it stays where it is —
    // there is no page to give it to, and dropping it would lose the block.
    if (carried.isNotEmpty()) out += carried
    return out
}

/**
 * Blocks that introduce what follows and read as broken without it.
 *
 * A heading carrying the chapter's COVER is not one of them. It is a title
 * page — byline, title, picture — which design.md §10 calls the right shape
 * for a chapter opening, and the picture is content by any measure. Treating
 * it as lonely made the merge pull the first paragraph onto a page with no
 * room for it, and since this pass runs on pages that are already laid out it
 * has no budget to check against: the page ran over and the footer sliced the
 * last line in half. Reported from chapter 2 of a real book, 2026-09-29.
 *
 * A bare title with no picture is still lonely and still travels, which is
 * the case this pass was built for.
 */
private val PageItem.opensSomething: Boolean
    get() = when (val b = block) {
        is Block.Heading -> b.coverSrc == null
        is Block.CustomFence -> b.kind == "meta"
        else -> false
    }

/** [head] fits the space offered; [tail] is what is left to place. */
private data class SplitParagraph(
    val head: Block.Paragraph,
    val tail: Block.Paragraph,
    val headPx: Int,
)

/**
 * Splits a paragraph so its first lines fill [availablePx].
 *
 * Returns null when fewer than [MIN_SPLIT_LINES] lines would fit, or when the
 * remainder would be a single orphaned line — a two-word continuation at the
 * top of the next page looks like a mistake, and moving the whole paragraph
 * costs less than that looks.
 */
private fun splitParagraph(
    block: Block.Paragraph,
    availablePx: Int,
    widthPx: Int,
    measurer: TextMeasurer,
    fontScale: Float,
    lineSpacing: Float,
    density: Density,
): SplitParagraph? = with(density) {
    val text = block.inlines.plainText()
    if (text.isBlank()) return null
    val style = TextStyle(
        fontSize = 18.sp * fontScale,
        lineHeight = 30.sp * fontScale * lineSpacing,
        fontFamily = FontFamily.Serif,
        hyphens = Hyphens.Auto,
    )
    val layout = measurer.measure(
        text = AnnotatedString(text),
        style = style,
        constraints = Constraints(maxWidth = widthPx),
    )
    if (layout.lineCount < MIN_SPLIT_LINES * 2) return null

    var fitting = 0
    while (fitting < layout.lineCount && layout.getLineBottom(fitting) <= availablePx) fitting++
    if (fitting < MIN_SPLIT_LINES) return null
    // If the tail would be a lone orphan line, give a line back rather than
    // abandoning the split. Refusing outright is what left a 589px gap for an
    // eight-line paragraph of which seven fitted: the orphan guard cost more
    // than the orphan it was avoiding.
    if (layout.lineCount - fitting < MIN_SPLIT_LINES) {
        fitting = layout.lineCount - MIN_SPLIT_LINES
    }
    if (fitting < MIN_SPLIT_LINES) return null

    val cut = layout.getLineEnd(fitting - 1, visibleEnd = true)
    if (cut <= 0 || cut >= text.length) return null

    return SplitParagraph(
        head = Block.Paragraph(sliceInlines(block.inlines, 0, cut)),
        tail = Block.Paragraph(sliceInlines(block.inlines, cut, text.length)),
        headPx = layout.getLineBottom(fitting - 1).toInt(),
    )
}

/**
 * The end of the last complete sentence at or before [limit], or [limit]
 * itself when backing up that far would cost more than [budgetChars].
 *
 * Deliberately crude: a full stop, question mark or exclamation followed by a
 * space. It does not need to be right about abbreviations — the worst case is
 * a cut one sentence earlier than ideal, which still reads as a whole thought.
 */
private fun sentenceCutAtOrBefore(text: String, limit: Int, budgetChars: Int): Int {
    val floor = (limit - budgetChars).coerceAtLeast(0)
    var i = limit - 1
    while (i > floor) {
        if (text.endsSentenceAt(i)) return sentenceEndAfter(text, i)
        i--
    }
    return limit
}

/** Closers that may stand between a full stop and the space after it. */
private const val SENTENCE_CLOSERS = "\"'\u201d\u2019)]\u00bb"

/**
 * Whether [i] is the terminator of a sentence — allowing for what may follow
 * it before the space.
 *
 * It used to require whitespace IMMEDIATELY after the stop, which misses
 * every sentence that ends inside quotation marks. Found on the Boox in a
 * chapter about drawing a mug, 2026-10-06: `connects to the cylinder." Now,
 * trace…` offered a clean break and the splitter could not see it, so the
 * page was cut four words into the next sentence instead.
 */
private fun String.endsSentenceAt(i: Int): Boolean {
    if (this[i] != '.' && this[i] != '!' && this[i] != '?') return false
    var j = i + 1
    while (j < length && this[j] in SENTENCE_CLOSERS) j++
    return j < length && this[j].isWhitespace()
}

private fun sentenceEndAfter(text: String, i: Int): Int {
    var j = i + 1
    while (j < text.length && text[j] in SENTENCE_CLOSERS) j++
    return j
}

/** A bordered block cut across a page boundary. */
private data class SplitFence(
    val head: Block.CustomFence,
    val tail: Block.CustomFence,
    val headPx: Int,
)

/**
 * Marks the two halves of a fence that was cut across a page boundary, so the
 * renderer can open the box at the break and name the continuation.
 *
 * A param rather than a field on [Block.CustomFence]: params already carry
 * everything else the renderer needs to know about a fence, and adding a field
 * would touch the parser, the serialiser and every construction site for a
 * property only pagination can produce.
 */
/** This piece began on an earlier page: open its top, name it a continuation. */
internal const val FENCE_CONT_FROM = "contFrom"

/** This piece carries on over the leaf: open its foot, withhold the answer. */
internal const val FENCE_CONT_TO = "contTo"

/**
 * How many rows of working room this box gets, decided by PAGINATION.
 *
 * Written only when a box would not otherwise fit a page, and read by the
 * renderer in place of the natural count. Like [FENCE_CONT_FROM] it is a
 * param the model never writes and the file on disk never holds — it exists
 * for the length of one layout.
 */
internal const val FENCE_SPACE_ROWS = "spaceRows"

/**
 * Rows of working room worth drawing at all. Below this it reads as a stray
 * band of dots rather than as somewhere to work, so the room goes to nothing
 * instead.
 */
private const val MIN_WORKING_ROWS = 2

/**
 * Print the answer without its mascot, because the page needs the width.
 *
 * The second rung of the same ladder as [FENCE_SPACE_ROWS]. The answer badge
 * sits BESIDE the answer text, so on a narrow page a 72dp mascot takes close
 * to a quarter of the line away from it: on a 320dp screen it turned a
 * seven-line answer into a twelve-line one, which was most of what pushed the
 * box off the page. Decided by pagination, never written by the model.
 */
internal const val FENCE_PLAIN_ANSWER = "plainAnswer"

/**
 * Give back working room until the box fits, or give it up entirely.
 *
 * THE WRITING SPACE IS THE SLACK. A box holds two things the reader cannot do
 * without — the task and, once revealed, the answer — plus one courtesy, the
 * room to work in. When the three together exceed a page, the courtesy is what
 * yields, because clipping either of the other two loses content with no way
 * to reach it. Seen on a 320x640 screen: the last line of an answer sliced in
 * half by the page edge and ", you did it right." unreachable.
 *
 * It yields all the way to nothing rather than stopping at [MIN_WORKING_ROWS].
 * A phone has no stylus anyway, and a box with no room to write still asks its
 * question and still shows its answer, where a clipped one does neither.
 *
 * Returns null when there is nothing to give — no room to start with, a
 * `space="none"` box, or a count pagination already wrote.
 */
private fun withShrunkWritingSpace(
    block: Block.CustomFence,
    overflowPx: Int,
    density: Density,
    fontScale: Float,
    floor: Int = MIN_WORKING_ROWS,
): Block.CustomFence? = with(density) {
    if (block.kind != "exercise") return null
    val spaceKind = block.params["space"]
    if (spaceKind == "none") return null
    // An explicit count is the starting point rather than a refusal: a piece
    // of a split box already carries one, and may still need to give more.
    // Termination is safe because the result must be strictly smaller.
    val natural = block.params[FENCE_SPACE_ROWS]?.toIntOrNull()
        ?: answerSpaceLineCount(splitExerciseBody(block.body).second, spaceKind == "lines")
    if (natural <= 0) return null
    val rowPx = (ANSWER_RULE_SPACING * fontScale).toPx()
    if (rowPx <= 0f) return null
    val drop = kotlin.math.ceil(overflowPx / rowPx).toInt().coerceAtLeast(1)
    val kept = (natural - drop).let { if (it < floor) (if (floor == 0) 0 else 0) else it }
    if (kept >= natural) return null
    // With a floor, taking the room to nothing is NOT this rung's business —
    // a box that can only fit by losing all of it is better served by the
    // part split, which keeps the room whole on a page of its own.
    if (floor > 0 && kept == 0) return null
    return block.copy(params = block.params + (FENCE_SPACE_ROWS to kept.toString()))
}

/**
 * Splits a bordered block so its first lines fill [availablePx].
 *
 * Until this existed a fence could not be split at all, so one taller than a
 * whole page hit the oversize branch in [paginate] — placed anyway and
 * clipped, with everything past the fold unreachable. On a small screen that
 * lost the end of an exercise AND its "Show answer" button, which is the most
 * load-bearing block in a study book (2026-09-29).
 *
 * It is now the LAST resort rather than the first. [paginate] moves a box that
 * would fit on a fresh page to one, so what reaches here is a box that does
 * not fit on any page — which is the only case where cutting beats moving.
 *
 * Only exercise and recap. A figure is a picture and cannot be cut; a fence of
 * an unknown kind might be anything, and guessing wrong here corrupts a page
 * rather than merely filling it badly.
 *
 * THE ANSWER TRAVELS WITH THE TAIL, whole. It is reserved space even while
 * hidden — the estimate measures it so that revealing it cannot reflow the
 * page under the reader's thumb — so splitting the question while leaving the
 * answer behind would strand the reveal button on a page with no room to
 * expand into.
 */
private fun splitFence(
    block: Block.CustomFence,
    availablePx: Int,
    widthPx: Int,
    measurer: TextMeasurer,
    fontScale: Float,
    lineSpacing: Float,
    density: Density,
    hasBadge: (String) -> Boolean,
): SplitFence? = with(density) {
    if (block.kind != "exercise" && block.kind != "recap") return null
    val (question, answer) = splitExerciseBody(block.body)
    val text = question.trim()
    if (text.isBlank()) return null

    // Stepped in from the marginal rule, exactly as the renderer does it.
    val innerWidth = (widthPx - FLOW_INDENT_DP.dp.toPx().toInt()).coerceAtLeast(1)
    val style = TextStyle(
        fontSize = 17.sp * fontScale,
        lineHeight = 28.sp * fontScale * lineSpacing,
        fontFamily = FontFamily.Serif,
    )
    val layout = measurer.measure(
        text = AnnotatedString(parseInlines(text).plainText()),
        style = style,
        constraints = Constraints(maxWidth = innerWidth),
    )
    // What the head spends before a word of body is drawn. The answer button
    // is NOT in it — the head never shows one — but the label row is, because
    // the head keeps the block's name and its mascot.
    val labelTextHeight = (FLOW_LABEL_SP.sp.toPx() * fontScale).toInt()
    val badgeKind = badgeKindFor(block.kind)
    val badgeHeight =
        if (badgeKind != null && hasBadge(badgeKind)) (BADGE_SIZE * fontScale).toPx().toInt() else 0
    // What this piece spends before a word is drawn. A piece that is itself
    // a continuation opens with nothing, so it spends nothing.
    val opensBlock = block.params[FENCE_CONT_FROM] == null
    val chrome = (if (opensBlock) maxOf(labelTextHeight, badgeHeight) + 6.dp.toPx().toInt() else 0) +
        8.dp.toPx().toInt() + 16.dp.toPx().toInt()

    val room = availablePx - chrome
    if (room <= 0) return null

    // CUT BETWEEN THE PARTS BEFORE CUTTING THE WORDS.
    //
    // A box is not a paragraph. It is a task, then room to work in, then an
    // answer — and when it will not fit, the overflow is very often none of
    // the words. Reported from the Boox at a larger font, 2026-10-06: a
    // six-line task whose eight-row grid and reserved answer came to half a
    // page again. Every one of those six lines FIT. The splitter cut them
    // anyway, because text was the only thing it knew how to cut, and the
    // rule that it must leave two lines for the tail turned a full page into
    // a four-line one. Three times over, which is what the reader saw: "3
    // ugly 5 lines each pages".
    //
    // So: if the task fits where it stands, the task is not what moves. The
    // head keeps every word of it plus as many rows of working room as the
    // page will take; the tail carries the rest of the room and the answer.
    // No sentence is broken, and both pages are full.
    val questionH = layout.size.height
    val rowPx = (ANSWER_RULE_SPACING * fontScale).toPx()
    val spaceKind = block.params["space"]
    val naturalRows = if (block.kind != "exercise" || spaceKind == "none") {
        0
    } else {
        block.params[FENCE_SPACE_ROWS]?.toIntOrNull()
            ?: answerSpaceLineCount(answer, spaceKind == "lines")
    }
    val hasAnswer = block.kind == "exercise" && answer.isNotBlank()
    if (questionH <= room && (naturalRows > 0 || hasAnswer) && rowPx > 0f) {
        val spacePad = 14.dp.toPx().toInt()
        val spare = room - questionH - spacePad
        val headRows = ((spare / rowPx).toInt())
            .coerceIn(0, naturalRows)
            .let { if (it < MIN_WORKING_ROWS) 0 else it }
        val tailRows = (naturalRows - headRows).let { if (it < MIN_WORKING_ROWS) 0 else it }
        // THE ROOM TO WRITE BELONGS WITH THE TASK, and what moves over the
        // leaf is the ANSWER. Seen on a 320px page: the task ran to two
        // pages, so this path put every word on the second and left the
        // eight-row grid alone on a third — a page that opens with nothing
        // but dots, and a reader writing their answer two pages away from
        // the question they are answering.
        //
        // So the part split earns its place only when it keeps room WITH the
        // task and has an answer to send on. Anything else falls through to
        // the text cut below, which leaves the last words of the task on the
        // same page as the room, where they belong.
        if (hasAnswer && naturalRows > 0 && headRows >= MIN_WORKING_ROWS) {
            return SplitFence(
                head = block.copy(
                    body = text,
                    params = block.params +
                        (FENCE_CONT_TO to "1") +
                        (FENCE_SPACE_ROWS to headRows.toString()),
                ),
                tail = block.copy(
                    // No words left to carry — the tail is the room and the
                    // answer. An empty body is why the renderer skips its
                    // text when there is none.
                    body = if (hasAnswer) "answer: ${answer.trim()}" else "",
                    params = block.params +
                        (FENCE_CONT_FROM to "1") +
                        (FENCE_SPACE_ROWS to tailRows.toString()),
                ),
                headPx = chrome + questionH +
                    (if (headRows > 0) headRows * rowPx.toInt() + spacePad else 0),
            )
        }
    }

    // The task itself does not fit. Now the words are what has to give.
    if (layout.lineCount < MIN_SPLIT_LINES * 2) return null

    var fitting = 0
    while (fitting < layout.lineCount && layout.getLineBottom(fitting) <= room) fitting++
    if (fitting < MIN_SPLIT_LINES) return null

    if (layout.lineCount - fitting < MIN_SPLIT_LINES) {
        fitting = layout.lineCount - MIN_SPLIT_LINES
    }
    if (fitting < MIN_SPLIT_LINES) return null

    val lineCut = layout.getLineEnd(fitting - 1, visibleEnd = true)
    if (lineCut <= 0 || lineCut >= text.length) return null

    // CUT AT A SENTENCE, not at a line. The piece that carries the writing
    // space is necessarily short — it is sharing its page with the grid, the
    // reveal button and the room reserved for the answer — so it will hold
    // only a line or two of task whatever the earlier cuts do. What made it
    // read as broken was not its length but that it began mid-sentence:
    // "about mistakes; just keep the ink flowing." The same two lines
    // starting at "Do not worry about mistakes…" read as a whole thought.
    //
    // Backing up is capped at three lines' worth. Two was too mean: the mug
    // exercise's closing sentence runs to about 110 characters, so the cut
    // fell through to a line boundary and the tail opened with "the top. Just
    // keep the pen moving." Three lines is the cost of a clean break on the
    // page that matters most — the tail is the one carrying the writing space,
    // so it is the one being read WHILE the reader works, and a whole
    // instruction there is worth more than three lines of head.
    // HOW FAR BACK, MEASURED RATHER THAN GUESSED. This was `3 * 42` — three
    // lines' worth, at an assumed 42 characters a line. On a 320px page at
    // font 1.3 a line holds about 22, so "three lines" was really six, and
    // the head gave up a third of its page to find a full stop. Ask the
    // layout instead: back up no further than the start of the second line
    // from the cut, and never past MIN_SPLIT_LINES lines, which is what
    // stranded a one-line piece under a mascot at the foot of page 9.
    val backstopLine = (fitting - 2).coerceAtLeast(MIN_SPLIT_LINES - 1)
    val backstop = layout.getLineStart(backstopLine.coerceAtMost(fitting - 1))
    val cut = sentenceCutAtOrBefore(text, lineCut, budgetChars = (lineCut - backstop).coerceAtLeast(0))

    val tailBody = buildString {
        append(text.substring(cut).trimStart())
        if (answer.isNotBlank()) {
            append("\n")
            append("answer: ")
            append(answer.trim())
        }
    }
    // Two independent bits rather than one head/tail flag. A block long enough
    // splits more than once, and the MIDDLE piece is both a continuation and
    // continues — with a single flag the second split overwrote "tail" with
    // "head" and the middle piece introduced itself as a fresh exercise,
    // mascot and all. Adding to the existing params rather than replacing them
    // is what lets the bits accumulate across repeated splits.
    return SplitFence(
        head = block.copy(
            body = text.substring(0, cut).trimEnd(),
            params = block.params + (FENCE_CONT_TO to "1"),
        ),
        tail = block.copy(
            body = tailBody,
            params = block.params + (FENCE_CONT_FROM to "1"),
        ),
        headPx = layout.getLineBottom(fitting - 1).toInt() + chrome,
    )
}

/**
 * Cuts an inline run to a character range of its plain text, preserving the
 * styling of whatever survives — a split must not lose a bold word that
 * happens to straddle the boundary.
 */
private fun sliceInlines(inlines: List<Inline>, from: Int, to: Int): List<Inline> {
    val out = mutableListOf<Inline>()
    var pos = 0
    for (inline in inlines) {
        val t = when (inline) {
            is Inline.Text -> inline.text
            is Inline.Bold -> inline.text
            is Inline.Italic -> inline.text
            is Inline.Code -> inline.text
        }
        val start = pos
        val end = pos + t.length
        pos = end
        if (end <= from || start >= to) continue
        val sliced = t.substring(maxOf(from, start) - start, minOf(to, end) - start)
        if (sliced.isEmpty()) continue
        out += when (inline) {
            is Inline.Text -> Inline.Text(sliced)
            is Inline.Bold -> Inline.Bold(sliced)
            is Inline.Italic -> Inline.Italic(sliced)
            is Inline.Code -> Inline.Code(sliced)
        }
    }
    // A continuation shouldn't open with the space that ended the previous
    // line.
    val first = out.firstOrNull()
    if (first is Inline.Text && first.text.startsWith(" ")) {
        out[0] = Inline.Text(first.text.trimStart())
    }
    return out
}

/** Fewest lines worth leaving on a page, or carrying to the next one. */
private const val MIN_SPLIT_LINES = 2

/**
 * The step from a flow block's marginal rule to its text, in dp.
 *
 * Duplicated from `FLOW_RULE_INDENT` in ReaderScreen rather than shared,
 * because that one is private to the renderer — but they MUST agree: the
 * estimate measures the task at this width and the renderer draws it at that
 * one, and a disagreement shows up as a clipped last line.
 */
internal const val FLOW_INDENT_DP = 14

/**
 * Air between a washed block's edge and its text, in dp. Mirrors `WASH_PAD`.
 */
internal const val WASH_PAD_DP = 10

/**
 * A block label's type size, in sp. Mirrors `FLOW_LABEL_SP`.
 *
 * Was 11 while the label sat inside a box that did the signalling. It is half
 * the signal now, so it is bigger — Aliaksei, reading the ruled version.
 */
internal const val FLOW_LABEL_SP = 13

/**
 * Returns the page index that contains the block at [blockIndex]. Purely
 * arithmetic: sum block counts across pages until we find the range that
 * contains the index. Works whether pages was rebuilt with fresh block
 * instances or cached from an equal-but-different-reference blocks list —
 * something the earlier identity-based (===) implementation didn't handle.
 *
 * [blocks] is kept as a parameter for API symmetry with the old signature;
 * only its size matters for the range check.
 */
fun findPageContainingBlock(pages: List<List<PageItem>>, blocks: List<Block>, blockIndex: Int): Int {
    if (blockIndex < 0 || pages.isEmpty()) return 0
    // Explicit indices now, not arithmetic: a split paragraph appears on two
    // pages, so counting sizes would drift. The first page carrying the block
    // wins, which is where an anchor should land.
    pages.forEachIndexed { pageIdx, page ->
        if (page.any { it.index == blockIndex }) return pageIdx
    }
    return pages.lastIndex.coerceAtLeast(0)
}

/**
 * The block index that identifies a page — what a bookmark stores, and what a
 * resume is resolved against.
 *
 * Not simply the first block on the page. A split paragraph appears on TWO
 * consecutive pages under one index, so "the page holding block 12" has two
 * answers, and a bookmark set on the second of them resolved back to the
 * first: you saved your place and the book handed you the page before it. The
 * first block that actually BEGINS on this page is unambiguous — no earlier
 * page carries it — so [findPageContainingBlock] can only resolve it one way.
 *
 * A page made entirely of one long paragraph's continuation has no such
 * block. There the split index is all there is, and it is used as before.
 */
fun anchorForPage(page: List<PageItem>?): Int =
    page?.firstOrNull { it.skippedPx == 0 }?.index
        ?: page?.firstOrNull()?.index
        ?: 0

/**
 * How many synthetic blocks the reader puts in FRONT of a chapter's parsed
 * Markdown before paginating it.
 *
 * Exactly one: the italic byline block ("Chapter 3 · ~30 min · Day 4"),
 * which also carries the chapter title when the model forgot to write one.
 * Everything else the reader synthesises is appended at the END — the
 * closing picture — precisely so it shifts no index.
 *
 * It matters because a block index is the app's unit of position, and this
 * offset is the difference between two index spaces that otherwise look
 * identical: [com.inkfirst.core.parseMarkdown] numbers the chapter as
 * written, while bookmarks, annotations, ink and the reader's own pager all
 * number it as displayed. Anything crossing that line — a search hit, say —
 * has to be converted or it lands a block early, which near a page boundary
 * means the page before the one you asked for.
 */
const val READER_LEADING_BLOCKS: Int = 1

/**
 * Converts an index into [com.inkfirst.core.parseMarkdown]'s list into the
 * reader's own index space. See [READER_LEADING_BLOCKS].
 */
fun readerBlockIndex(parsedIndex: Int): Int = parsedIndex + READER_LEADING_BLOCKS

/**
 * Which of this chapter's bookmarks lands on [pageIndex], as the block index
 * it was stored under — or null if none does.
 *
 * Resolved rather than compared, and that is the point. Setting a bookmark
 * stores [anchorForPage]: the first block that BEGINS on the page. Going to
 * one calls [findPageContainingBlock]: the first page CONTAINING that block.
 * The two agree only while the pagination that produced them is still in
 * force — change the margin, the font size or the line spacing and a stored
 * anchor can stop being any page's first-beginning block. The bookmark then
 * still OPENS its page, because the jump uses containment, while the ribbon
 * and the menu compared against the anchor and saw nothing: a page you could
 * navigate to that offered to bookmark itself all over again.
 *
 * Asking the question the same way the jump asks it is what keeps them from
 * drifting apart. A bookmark resolves to exactly one page, so at most one
 * page ever shows its ribbon.
 */
fun bookmarkOnPage(
    pages: List<List<PageItem>>,
    blocks: List<Block>,
    pageIndex: Int,
    bookmarkedBlocks: List<Int>,
): Int? = bookmarkedBlocks.firstOrNull {
    findPageContainingBlock(pages, blocks, it) == pageIndex
}

/**
 * Heights match the ReaderScreen renderer as closely as feasible without
 * doing a full off-screen SubcomposeLayout pass. Close enough for spike v2.0;
 * we can tighten if pages regularly under- or over-fill by more than ~10%.
 */
private fun estimateHeight(
    block: Block,
    widthPx: Int,
    density: Density,
    measurer: TextMeasurer,
    fontScale: Float,
    lineSpacing: Float,
    hasFigureImage: (String) -> Boolean,
    hasBadge: (String) -> Boolean = { false },
    atPageTop: Boolean = false,
    answerPrefix: String = "Answer:",
): Int = with(density) {
    when (block) {
        is Block.Heading -> {
            // Must match HeadingBlock exactly — these are the renderer's own
            // spacers, and a mismatch shows up as a clipped last line.
            val (baseSize, topPad, botPad) = when (block.level) {
                1 -> Triple(30.sp, 40.dp, 28.dp)
                2 -> Triple(24.sp, 32.dp, 26.dp)
                3 -> Triple(20.sp, 26.dp, 20.dp)
                else -> Triple(18.sp, 22.dp, 16.dp)
            }
            val fontSize = baseSize * fontScale
            val result = measurer.measure(
                // Measure what is drawn: an h1 has its "Chapter N:" prefix
                // stripped by the renderer, and on a narrow page that is the
                // difference between a two-line and a three-line title.
                text = AnnotatedString(
                    block.inlines.plainText().let {
                        if (block.level == 1) stripChapterPrefixForMeasure(it) else it
                    },
                ),
                style = TextStyle(
                    fontSize = fontSize,
                    lineHeight = fontSize * 1.2f,  // headings ignore lineSpacing — always tight
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                ),
                constraints = Constraints(maxWidth = widthPx),
            )
            // A chapter cover attached to the title heading (see
            // Block.Heading.coverSrc) is a fixed-ratio picture below it.
            val cover = block.coverSrc
                ?.takeIf { hasFigureImage(it) }
                ?.let {
                    (widthPx * PLATE_WIDTH_FRACTION / FIGURE_ASPECT).toInt() + 16.dp.toPx().toInt()
                }
                ?: 0
            (if (atPageTop) 0 else topPad.toPx().toInt()) + result.size.height +
                botPad.toPx().toInt() + cover
        }
        is Block.Paragraph -> {
            val result = measurer.measure(
                text = AnnotatedString(block.inlines.plainText()),
                style = TextStyle(
                    fontSize = 18.sp * fontScale,
                    lineHeight = 30.sp * fontScale * lineSpacing,
                    fontFamily = FontFamily.Serif,
                    hyphens = Hyphens.Auto,  // match renderer or line count drifts
                ),
                constraints = Constraints(maxWidth = widthPx),
            )
            // Matches ParagraphBlock's bottom padding.
            result.size.height + 20.dp.toPx().toInt()
        }
        // A short centred hairline plus the air around it. Fixed cost.
        is Block.Rule -> 1.dp.toPx().toInt() + 28.dp.toPx().toInt()
        is Block.BulletItem -> measureListItem(block.inlines, "• ", widthPx, density, measurer, fontScale, lineSpacing)
        is Block.OrderedItem -> measureListItem(block.inlines, "${block.n}. ", widthPx, density, measurer, fontScale, lineSpacing)
        is Block.CustomFence -> {
            // Meta byline: single line, no border, no label.
            if (block.kind == "meta") {
                val result = measurer.measure(
                    text = AnnotatedString(block.body),
                    style = TextStyle(
                        fontSize = 13.sp * fontScale,
                        lineHeight = 17.sp * fontScale,
                        fontFamily = FontFamily.Serif,
                    ),
                    constraints = Constraints(maxWidth = widthPx),
                )
                // A chapter whose Markdown had no heading carries its title
                // here instead — measured at h1 size to match MetaBlock.
                val standInTitle = block.params["title"]?.let { title ->
                    val size = 30.sp * fontScale
                    measurer.measure(
                        text = AnnotatedString(title),
                        style = TextStyle(
                            fontSize = size,
                            lineHeight = size * 1.2f,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                        ),
                        constraints = Constraints(maxWidth = widthPx),
                    ).size.height + 16.dp.toPx().toInt()
                } ?: 0
                // A cover turns the opener into a full title page; it is a
                // fixed-ratio picture like any figure.
                val cover = if (block.params["cover"] != null) {
                    (widthPx * PLATE_WIDTH_FRACTION / FIGURE_ASPECT).toInt() + 16.dp.toPx().toInt()
                } else {
                    0
                }
                return@with result.size.height + 8.dp.toPx().toInt() + standInTitle + cover
            }
            // The closing picture: a fixed-ratio image with air above it.
            if (block.kind == "tail") {
                val src = block.params["src"]
                return@with if (src != null && hasFigureImage(src)) {
                    (widthPx * PLATE_WIDTH_FRACTION / FIGURE_ASPECT).toInt() + 24.dp.toPx().toInt()
                } else {
                    0
                }
            }
            // An illustrated figure is a picture, not a bordered text box:
            // full content width at the fixed figure ratio, plus the vertical
            // breathing room FigureBlock puts around it. No text to measure.
            val figureSrc = block.params["src"]
            if (block.kind == "figure" && figureSrc != null && hasFigureImage(figureSrc)) {
                val imageHeight = (widthPx / FIGURE_ASPECT).toInt()
                return@with imageHeight + 32.dp.toPx().toInt()
            }

            // A FLOW BLOCK HAS NO FRAME, so it has no inset either: the task
            // runs the full width of the page like the prose around it. Code
            // and undrawn-figure fallbacks keep their box and their inset.
            val flows = block.kind == "exercise" || block.kind == "recap"
            // A flow block has no frame, but it IS stepped in from its
            // marginal rule, and the step narrows the measure. Must match
            // FLOW_RULE_INDENT in the renderer or the line count drifts.
            val washed = block.kind == "recap"
            val innerWidth = when {
                washed -> widthPx - 2 * WASH_PAD_DP.dp.toPx().toInt()
                flows -> widthPx - FLOW_INDENT_DP.dp.toPx().toInt()
                else -> widthPx - 24.dp.toPx().toInt()
            }
            val bodyText = when (block.kind) {
                "figure" -> block.params["alt"] ?: block.body.trim().ifBlank { "(figure)" }
                else -> block.body.trim()
            }
            val bodyStyle = when (block.kind) {
                "figure" -> TextStyle(
                    fontSize = 16.sp * fontScale,
                    lineHeight = 26.sp * fontScale * lineSpacing,
                    fontFamily = FontFamily.Serif,
                )
                else -> TextStyle(
                    fontSize = 17.sp * fontScale,
                    lineHeight = 28.sp * fontScale * lineSpacing,
                    fontFamily = FontFamily.Serif,
                )
            }
            val body = measurer.measure(
                // Measure what will actually be drawn: the renderer parses
                // these bodies now, so the backtick and asterisk markers are
                // gone by the time they reach the page.
                text = AnnotatedString(parseInlines(bodyText).plainText()),
                style = bodyStyle,
                constraints = Constraints(maxWidth = innerWidth),
            )
            // The label row is as tall as the taller of its two children —
            // the label text, or the mascot badge sitting beside it.
            val labelTextHeight = (FLOW_LABEL_SP.sp.toPx() * fontScale).toInt()
            val badgeKind = badgeKindFor(block.kind)
            val badgeHeight =
                if (badgeKind != null && hasBadge(badgeKind)) (BADGE_SIZE * fontScale).toPx().toInt() else 0
            // The mascot and the name are drawn ONCE, by the piece that opens
            // the block. A continuation of a flow block draws nothing of its
            // own — which is most of what made a cut expensive before.
            val opensBlock = !flows || block.params[FENCE_CONT_FROM] == null
            val label =
                if (opensBlock) maxOf(labelTextHeight, badgeHeight) + 6.dp.toPx().toInt() else 0
            // The reveal button, plus room for the answer badge that appears
            // beside the answer text once it is shown. Reserved whether or
            // not the reader has revealed it — revealing must not reflow the
            // page under their thumb.
            val answerBadge =
                if (block.params[FENCE_PLAIN_ANSWER] == null && hasBadge("answer")) {
                    (BADGE_SIZE * fontScale).toPx().toInt()
                } else {
                    0
                }
            // Only the piece that actually SHOWS the reveal button reserves
            // room for it. A middle piece of a split exercise has neither the
            // button nor the answer, and reserving both left half a page of
            // white under a box that had more to say.
            val showsReveal = block.params[FENCE_CONT_TO] == null
            // The reveal button is only drawn when there is something to
            // reveal. An exercise that is pure instruction shows none, and
            // was reserving 48dp plus a badge for a control that never
            // appears.
            val answerText = splitExerciseBody(block.body).second.trim()
            val hasAnswer = block.kind == "exercise" && answerText.isNotBlank()
            // THE REVEALED STATE IS THE TALL ONE, AND IT WAS NEVER MEASURED.
            // This reserved the button (48dp) plus the answer badge, on the
            // assumption that the 72dp mascot dominates whatever text sits
            // beside it. It does not: a diagnostic answer — the kind the
            // chapter prompt now asks for, saying what to CHECK rather than
            // describing the artefact — runs to several lines, in a column
            // the badge itself narrows. Reported from page 16 of the demo
            // chapter: the last line was sliced in half by the page edge and
            // ", you did it right." was unreachable.
            //
            // A block with TWO STATES has to fit the taller one, because
            // revealing must not reflow the page — ink is anchored to a block
            // index, so a reflow would move the reader's own handwriting out
            // from under the answer they wrote it to compare against. Measure
            // the answer exactly as the renderer draws it: same "Answer: "
            // prefix, same 16sp, and in the column the badge leaves behind.
            val revealedHeight = if (hasAnswer && showsReveal) {
                val gap = if (answerBadge > 0) answerBadge + 10.dp.toPx().toInt() else 0
                val measured = measurer.measure(
                    text = AnnotatedString(parseInlines("$answerPrefix $answerText").plainText()),
                    style = TextStyle(
                        fontSize = 16.sp * fontScale,
                        lineHeight = 26.sp * fontScale * lineSpacing,
                        fontFamily = FontFamily.Serif,
                    ),
                    constraints = Constraints(maxWidth = (innerWidth - gap).coerceAtLeast(1)),
                )
                // The revealed row is as tall as the taller of badge and text.
                maxOf(answerBadge, measured.size.height)
            } else {
                0
            }
            val exerciseExtra =
                if (hasAnswer && showsReveal) maxOf(48.dp.toPx().toInt(), revealedHeight) else 0
            // The ruled lines the reader writes their own answer on. Measured
            // with the SAME function the renderer draws from, deliberately:
            // an estimate that computes independently of its renderer is the
            // bug this file has produced twice in one day.
            val answerSpace = if (block.kind == "exercise") {
                val spaceKind = block.params["space"]
                // A count written by pagination outranks the natural one —
                // the same precedence the renderer uses, which is the point:
                // an estimate that computes independently of its renderer is
                // the bug this file has produced more than once.
                //
                // It outranks `showsReveal` too, since the part split: a HEAD
                // carries working room when pagination gave it some, even
                // though it never carries the answer. Only a head with no
                // count of its own has no room — the natural count belongs to
                // the piece that holds the answer.
                val explicit = block.params[FENCE_SPACE_ROWS]?.toIntOrNull()
                val lines = when {
                    spaceKind == "none" -> 0
                    explicit != null -> explicit
                    showsReveal -> answerSpaceLineCount(
                        splitExerciseBody(block.body).second,
                        spaceKind == "lines",
                    )
                    else -> 0
                }
                if (lines == 0) {
                    0
                } else {
                    (ANSWER_RULE_SPACING * fontScale).toPx().toInt() * lines +
                        14.dp.toPx().toInt()
                }
            } else {
                0
            }
            val figureExtra = if (block.kind == "figure") 24.dp.toPx().toInt() else 0
            val outerSpacers = 8.dp.toPx().toInt()
            val wrapperVPad = 16.dp.toPx().toInt()
            val borderInner = when {
                washed -> 2 * WASH_PAD_DP.dp.toPx().toInt()
                flows -> 0
                else -> 24.dp.toPx().toInt()
            }
            // The two 8dp gaps an exercise puts around its working room. They
            // used to hide inside the border's padding; with the frame gone
            // they have to be counted, and counting them errs tall, which is
            // the safe direction for a budget whose failure is clipping.
            val contentSpacers = if (block.kind == "exercise") 16.dp.toPx().toInt() else 0
            label + body.size.height + exerciseExtra + answerSpace + figureExtra +
                outerSpacers + wrapperVPad + borderInner + contentSpacers
        }
    }
}

private fun measureListItem(
    inlines: List<Inline>,
    prefix: String,
    widthPx: Int,
    density: Density,
    measurer: TextMeasurer,
    fontScale: Float,
    lineSpacing: Float,
): Int = with(density) {
    val result = measurer.measure(
        text = AnnotatedString(prefix + inlines.plainText()),
        style = TextStyle(
            fontSize = 18.sp * fontScale,
            lineHeight = 30.sp * fontScale * lineSpacing,
            fontFamily = FontFamily.Serif,
        ),
        constraints = Constraints(maxWidth = widthPx - 18.dp.toPx().toInt()),
    )
    result.size.height + 10.dp.toPx().toInt()
}

private fun List<Inline>.plainText(): String = joinToString("") { i ->
    when (i) {
        is Inline.Text -> i.text
        is Inline.Bold -> i.text
        is Inline.Italic -> i.text
        is Inline.Code -> i.text
    }
}

/** Mirror of the renderer's chapter-prefix strip; keep the two in step. */
private fun stripChapterPrefixForMeasure(title: String): String =
    MEASURE_CHAPTER_PREFIX.replace(title, "").trim().ifBlank { title }

private val MEASURE_CHAPTER_PREFIX =
    Regex("""^\s*chapter\s+\d+\s*[:.\u2014\u2013-]\s*""", RegexOption.IGNORE_CASE)
