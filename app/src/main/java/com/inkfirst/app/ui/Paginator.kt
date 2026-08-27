package com.inkfirst.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkfirst.core.Block
import com.inkfirst.core.Inline

/**
 * Packs a chapter's Blocks into pages of at most [contentHeightPx] tall.
 * V2.0 discipline: every block is atomic — a block that doesn't fit on the
 * current page moves whole to the next. Consequence: some pages will end
 * with visible whitespace. Paragraph splitting is a follow-up (v2.1).
 *
 * [fontScale] multiplies every text style (fontSize + lineHeight) uniformly.
 * The renderer must use the same scale for the layout to match this pack.
 */
fun paginate(
    blocks: List<Block>,
    contentWidthPx: Int,
    contentHeightPx: Int,
    density: Density,
    measurer: TextMeasurer,
    fontScale: Float = 1.0f,
): List<List<Block>> {
    if (blocks.isEmpty() || contentWidthPx <= 0 || contentHeightPx <= 0) return listOf(blocks)

    val pages = mutableListOf<MutableList<Block>>()
    pages.add(mutableListOf())
    var height = 0

    for (block in blocks) {
        val h = estimateHeight(block, contentWidthPx, density, measurer, fontScale)
        val pageIsEmpty = pages.last().isEmpty()
        if (height + h > contentHeightPx && !pageIsEmpty) {
            pages.add(mutableListOf())
            height = 0
        }
        pages.last().add(block)
        height += h
    }
    return pages.map { it.toList() }
}

/**
 * Returns the page index that contains the block at [blockIndex] within [blocks].
 * Used to jump a Reader to a bookmark that's anchored to a block, not a page.
 * Returns 0 if the index is out of range.
 */
fun findPageContainingBlock(pages: List<List<Block>>, blocks: List<Block>, blockIndex: Int): Int {
    if (blockIndex < 0 || blockIndex >= blocks.size) return 0
    val target = blocks[blockIndex]
    pages.forEachIndexed { pageIdx, page ->
        if (page.any { it === target }) return pageIdx
    }
    return 0
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
): Int = with(density) {
    when (block) {
        is Block.Heading -> {
            val (baseSize, topPad, botPad) = when (block.level) {
                1 -> Triple(30.sp, 24.dp, 12.dp)
                2 -> Triple(24.sp, 20.dp, 10.dp)
                3 -> Triple(20.sp, 16.dp, 8.dp)
                else -> Triple(18.sp, 14.dp, 6.dp)
            }
            val result = measurer.measure(
                text = AnnotatedString(block.inlines.plainText()),
                style = TextStyle(
                    fontSize = baseSize * fontScale,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                ),
                constraints = Constraints(maxWidth = widthPx),
            )
            topPad.toPx().toInt() + result.size.height + botPad.toPx().toInt()
        }
        is Block.Paragraph -> {
            val result = measurer.measure(
                text = AnnotatedString(block.inlines.plainText()),
                style = TextStyle(
                    fontSize = 18.sp * fontScale,
                    lineHeight = 30.sp * fontScale,
                    fontFamily = FontFamily.Serif,
                ),
                constraints = Constraints(maxWidth = widthPx),
            )
            result.size.height + 12.dp.toPx().toInt()
        }
        is Block.BulletItem -> measureListItem(block.inlines, "• ", widthPx, density, measurer, fontScale)
        is Block.OrderedItem -> measureListItem(block.inlines, "${block.n}. ", widthPx, density, measurer, fontScale)
        is Block.CustomFence -> {
            val innerWidth = widthPx - 24.dp.toPx().toInt()
            val bodyText = when (block.kind) {
                "figure" -> block.params["alt"] ?: block.body.trim().ifBlank { "(figure)" }
                else -> block.body.trim()
            }
            val bodyStyle = when (block.kind) {
                "figure" -> TextStyle(
                    fontSize = 16.sp * fontScale,
                    lineHeight = 26.sp * fontScale,
                    fontFamily = FontFamily.Serif,
                )
                else -> TextStyle(
                    fontSize = 17.sp * fontScale,
                    lineHeight = 28.sp * fontScale,
                    fontFamily = FontFamily.Serif,
                )
            }
            val body = measurer.measure(
                text = AnnotatedString(bodyText),
                style = bodyStyle,
                constraints = Constraints(maxWidth = innerWidth),
            )
            // Label is a small sans-serif tag; scales with font too so it stays proportional.
            val label = (11.sp.toPx() * fontScale).toInt() + 6.dp.toPx().toInt()
            val exerciseExtra = if (block.kind == "exercise") 48.dp.toPx().toInt() else 0
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
): Int = with(density) {
    val result = measurer.measure(
        text = AnnotatedString(prefix + inlines.plainText()),
        style = TextStyle(
            fontSize = 18.sp * fontScale,
            lineHeight = 30.sp * fontScale,
            fontFamily = FontFamily.Serif,
        ),
        constraints = Constraints(maxWidth = widthPx - 8.dp.toPx().toInt()),
    )
    result.size.height + 6.dp.toPx().toInt()
}

private fun List<Inline>.plainText(): String = joinToString("") { i ->
    when (i) {
        is Inline.Text -> i.text
        is Inline.Bold -> i.text
        is Inline.Italic -> i.text
        is Inline.Code -> i.text
    }
}
