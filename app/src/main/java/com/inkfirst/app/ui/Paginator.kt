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
): List<List<Block>> {
    if (blocks.isEmpty() || contentWidthPx <= 0 || contentHeightPx <= 0) return listOf(blocks)

    // Safety margin: TextMeasurer under-counts by roughly a half-line per
    // block (font metric slop + sub-pixel rounding). 5% wasn't enough — still
    // saw single-line clipping. 10% covers observed cases; costs at most one
    // extra page per ~10 in the chapter.
    // The reader reserves INK_TOP_MARGIN above the first block so the first
    // line can be stroked from above; the pack has to fit in what is left.
    val budget = ((contentHeightPx - inkTopMarginPx) * 0.90f).toInt()

    val pages = mutableListOf<MutableList<Block>>()
    pages.add(mutableListOf())
    var height = 0

    for (i in blocks.indices) {
        val block = blocks[i]
        // A heading at the top of a page drops its space-above, so it has to
        // be measured that way too. Conservative by construction: if a block
        // measured mid-page then moves to the next one it only gets shorter.
        val h = estimateHeight(
            block, contentWidthPx, density, measurer, fontScale, lineSpacing, hasFigureImage, hasBadge,
            atPageTop = pages.last().isEmpty(),
        )

        // Keep-with-next: don't orphan a heading at the bottom of a page. If a
        // heading is about to land and the block after it wouldn't fit on the
        // same page, push the heading to the next page too — as long as the pair
        // actually fits together on an empty page (otherwise we'd loop).
        val opensSomething = block is Block.Heading ||
            (block is Block.CustomFence && block.kind == "meta")
        val requiredHeight = if (opensSomething && i + 1 < blocks.size) {
            val nextH = estimateHeight(
                blocks[i + 1], contentWidthPx, density, measurer, fontScale, lineSpacing,
                hasFigureImage, hasBadge,
            )
            if (h + nextH <= budget) h + nextH else h
        } else {
            h
        }

        val pageIsEmpty = pages.last().isEmpty()
        if (height + requiredHeight > budget && !pageIsEmpty) {
            pages.add(mutableListOf())
            height = 0
        }
        pages.last().add(block)
        height += h
    }
    return pages.map { it.toList() }
}

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
fun findPageContainingBlock(pages: List<List<Block>>, blocks: List<Block>, blockIndex: Int): Int {
    if (blockIndex < 0 || pages.isEmpty()) return 0
    var cumulative = 0
    pages.forEachIndexed { pageIdx, page ->
        if (blockIndex < cumulative + page.size) return pageIdx
        cumulative += page.size
    }
    return pages.lastIndex.coerceAtLeast(0)
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
                ?.let { (widthPx / FIGURE_ASPECT).toInt() + 16.dp.toPx().toInt() }
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
                    (widthPx / FIGURE_ASPECT).toInt() + 16.dp.toPx().toInt()
                } else {
                    0
                }
                return@with result.size.height + 8.dp.toPx().toInt() + standInTitle + cover
            }
            // The closing picture: a fixed-ratio image with air above it.
            if (block.kind == "tail") {
                val src = block.params["src"]
                return@with if (src != null && hasFigureImage(src)) {
                    (widthPx / FIGURE_ASPECT).toInt() + 24.dp.toPx().toInt()
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

            val innerWidth = widthPx - 24.dp.toPx().toInt()
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
            val labelTextHeight = (11.sp.toPx() * fontScale).toInt()
            val badgeKind = badgeKindFor(block.kind)
            val badgeHeight =
                if (badgeKind != null && hasBadge(badgeKind)) (BADGE_SIZE * fontScale).toPx().toInt() else 0
            val label = maxOf(labelTextHeight, badgeHeight) + 6.dp.toPx().toInt()
            // The reveal button, plus room for the answer badge that appears
            // beside the answer text once it is shown. Reserved whether or
            // not the reader has revealed it — revealing must not reflow the
            // page under their thumb.
            val answerBadge = if (hasBadge("answer")) (BADGE_SIZE * fontScale).toPx().toInt() else 0
            val exerciseExtra =
                if (block.kind == "exercise") 48.dp.toPx().toInt() + answerBadge else 0
            val figureExtra = if (block.kind == "figure") 24.dp.toPx().toInt() else 0
            val outerSpacers = 8.dp.toPx().toInt()
            val wrapperVPad = 16.dp.toPx().toInt()
            val borderInner = 24.dp.toPx().toInt()
            label + body.size.height + exerciseExtra + figureExtra +
                outerSpacers + wrapperVPad + borderInner
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
