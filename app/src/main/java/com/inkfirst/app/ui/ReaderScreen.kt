package com.inkfirst.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkfirst.app.data.ReaderPrefs
import com.inkfirst.app.data.rememberCourseStore
import com.inkfirst.core.Block
import com.inkfirst.core.Course
import com.inkfirst.core.Inline
import com.inkfirst.core.parseMarkdown
import java.time.LocalDate

private val InkColor = Color(0xFF111111)
private val PaperColor = Color(0xFFF8F5EE)
private val MutedColor = Color(0xFF555555)
private val BorderColor = Color(0xFF888888)

@Composable
fun ReaderScreen(
    courseSlug: String,
    chapterSlug: String,
    startBlockIndex: Int = 0,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = rememberCourseStore()
    val readerPrefs = remember { ReaderPrefs(context) }

    var course by remember { mutableStateOf<Course?>(null) }
    var markdown by remember { mutableStateOf<String?>(null) }
    var fontScale by remember { mutableStateOf(readerPrefs.getFontScale()) }
    // Anchor to a block index rather than a page — that way font-scale changes
    // (which repaginate) still land us on the same content.
    var anchorBlockIndex by remember { mutableStateOf(startBlockIndex.coerceAtLeast(0)) }

    LaunchedEffect(courseSlug, chapterSlug) {
        course = store.load(courseSlug)
        markdown = store.readChapter(courseSlug, chapterSlug)
    }

    val blocks = remember(markdown) { markdown?.let { parseMarkdown(it) } ?: emptyList() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperColor),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            PagedContent(
                blocks = blocks,
                course = course,
                chapterSlug = chapterSlug,
                fontScale = fontScale,
                anchorBlockIndex = anchorBlockIndex,
                onAnchorChange = { anchorBlockIndex = it },
                onBack = onBack,
                onFontScaleChange = { newScale ->
                    fontScale = newScale
                    readerPrefs.setFontScale(newScale)
                },
                onBookmarkToggle = { newCourse -> course = newCourse ?: course },
            )
        }
    }
}

@Composable
private fun PagedContent(
    blocks: List<Block>,
    course: Course?,
    chapterSlug: String,
    fontScale: Float,
    anchorBlockIndex: Int,
    onAnchorChange: (Int) -> Unit,
    onBack: () -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onBookmarkToggle: (Course?) -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val store = rememberCourseStore()

    val chapterN = course?.chapters?.firstOrNull { it.slug == chapterSlug }?.n

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx().toInt() }
        val availableHeightPx = with(density) { (maxHeight - 80.dp).toPx().toInt() }

        val pages = remember(blocks, widthPx, availableHeightPx, fontScale) {
            paginate(
                blocks = blocks,
                contentWidthPx = widthPx,
                contentHeightPx = availableHeightPx,
                density = density,
                measurer = measurer,
                fontScale = fontScale,
            )
        }
        // pageIndex is derived from the anchor + pages — so font changes preserve position.
        val pageIndex = remember(pages, anchorBlockIndex) {
            findPageContainingBlock(pages, blocks, anchorBlockIndex).coerceAtLeast(0)
        }
        val revealed = remember(blocks) { mutableStateMapOf<Int, Boolean>() }

        // Bookmark state for the current page's first block.
        val firstBlockOnPage = pages.getOrNull(pageIndex)?.firstOrNull()
        val firstBlockIndex = firstBlockOnPage?.let { blocks.indexOf(it) } ?: -1
        val isBookmarked = course?.bookmarks?.any {
            it.chapterSlug == chapterSlug && it.blockIndex == firstBlockIndex
        } == true

        Column(modifier = Modifier.fillMaxSize()) {
            ReaderHeader(
                chapterN = chapterN,
                chapterCount = course?.chapters?.size,
                fontScale = fontScale,
                onBack = onBack,
                onFontSmaller = {
                    val idx = ReaderPrefs.FONT_SCALES.indexOfNearest(fontScale)
                    if (idx > 0) onFontScaleChange(ReaderPrefs.FONT_SCALES[idx - 1])
                },
                onFontLarger = {
                    val idx = ReaderPrefs.FONT_SCALES.indexOfNearest(fontScale)
                    if (idx < ReaderPrefs.FONT_SCALES.lastIndex) onFontScaleChange(ReaderPrefs.FONT_SCALES[idx + 1])
                },
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(pages.size) {
                        detectTapGestures { pos ->
                            if (pos.x < size.width / 2) {
                                if (pageIndex > 0) {
                                    val prevPageFirstBlock = pages.getOrNull(pageIndex - 1)?.firstOrNull()
                                    val idx = prevPageFirstBlock?.let { blocks.indexOf(it) } ?: 0
                                    onAnchorChange(idx.coerceAtLeast(0))
                                }
                            } else {
                                if (pageIndex < pages.size - 1) {
                                    val nextPageFirstBlock = pages.getOrNull(pageIndex + 1)?.firstOrNull()
                                    val idx = nextPageFirstBlock?.let { blocks.indexOf(it) } ?: 0
                                    onAnchorChange(idx.coerceAtLeast(0))
                                }
                            }
                        }
                    },
            ) {
                // Content fills the box edge-to-edge. When a ribbon is present,
                // it overlaps the top-right corner of text by a few characters —
                // that's the physical-bookmark metaphor working as intended.
                Column(modifier = Modifier.fillMaxSize()) {
                    pages.getOrNull(pageIndex)?.forEach { block ->
                        val globalIdx = remember(block) { blocks.indexOf(block) }
                        RenderBlock(
                            block = block,
                            fontScale = fontScale,
                            revealed = revealed[globalIdx] == true,
                            onReveal = { revealed[globalIdx] = true },
                        )
                    }
                }

                // Invisible bookmark tap zone in the top-right corner. Renders
                // a ribbon shape only when bookmarked. Clickable wins over the
                // parent page-turn detectTapGestures (child priority).
                BookmarkCorner(
                    isBookmarked = isBookmarked,
                    enabled = firstBlockIndex >= 0 && course != null,
                    modifier = Modifier.align(Alignment.TopEnd),
                    onToggle = {
                        val c = course ?: return@BookmarkCorner
                        if (firstBlockIndex < 0) return@BookmarkCorner
                        val updated = if (isBookmarked) {
                            store.removeBookmark(c.slug, chapterSlug, firstBlockIndex)
                        } else {
                            store.addBookmark(c.slug, chapterSlug, firstBlockIndex, LocalDate.now().toString())
                        }
                        onBookmarkToggle(updated)
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (pages.isEmpty()) "" else "Page ${pageIndex + 1} of ${pages.size}",
                    style = MaterialTheme.typography.bodySmall.copy(color = MutedColor),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "tap ← / → to turn",
                    style = MaterialTheme.typography.bodySmall.copy(color = MutedColor),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun ReaderHeader(
    chapterN: Int?,
    chapterCount: Int?,
    fontScale: Float,
    onBack: () -> Unit,
    onFontSmaller: () -> Unit,
    onFontLarger: () -> Unit,
) {
    val idx = ReaderPrefs.FONT_SCALES.indexOfNearest(fontScale)
    val canShrink = idx > 0
    val canGrow = idx < ReaderPrefs.FONT_SCALES.lastIndex

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back", color = InkColor) }
        Spacer(Modifier.weight(1f))
        if (chapterN != null && chapterCount != null) {
            Text(
                text = "Ch $chapterN/$chapterCount",
                style = MaterialTheme.typography.bodySmall.copy(color = MutedColor),
            )
        }
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onFontSmaller, enabled = canShrink) {
            Text("A−", color = if (canShrink) InkColor else MutedColor)
        }
        TextButton(onClick = onFontLarger, enabled = canGrow) {
            Text("A+", color = if (canGrow) InkColor else MutedColor)
        }
    }
}

/**
 * Invisible tap zone in the top-right corner (48×48dp — the platform tap
 * target) that toggles a bookmark for the current page's first block.
 * When bookmarked, a small ribbon is drawn dangling from the top edge —
 * otherwise nothing renders and text flows underneath. Overlap with the
 * top-right of text on bookmarked pages is intentional; that's how a
 * physical bookmark works.
 */
@Composable
private fun BookmarkCorner(
    isBookmarked: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
            ) { onToggle() },
        contentAlignment = Alignment.TopEnd,
    ) {
        if (isBookmarked) {
            Canvas(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(width = 16.dp, height = 22.dp),
            ) {
                val w = size.width
                val h = size.height
                val notch = h * 0.28f
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, h)
                    lineTo(w / 2, h - notch)
                    lineTo(0f, h)
                    close()
                }
                drawPath(path, color = InkColor)
            }
        }
    }
}

private fun List<Float>.indexOfNearest(value: Float): Int {
    var best = 0
    var bestDelta = Float.MAX_VALUE
    forEachIndexed { i, v ->
        val d = kotlin.math.abs(v - value)
        if (d < bestDelta) {
            best = i; bestDelta = d
        }
    }
    return best
}

@Composable
private fun RenderBlock(block: Block, fontScale: Float, revealed: Boolean, onReveal: () -> Unit) {
    when (block) {
        is Block.Heading -> HeadingBlock(block, fontScale)
        is Block.Paragraph -> ParagraphBlock(block.inlines, fontScale)
        is Block.BulletItem -> ListItemBlock(prefix = "• ", inlines = block.inlines, fontScale = fontScale)
        is Block.OrderedItem -> ListItemBlock(prefix = "${block.n}. ", inlines = block.inlines, fontScale = fontScale)
        is Block.CustomFence -> when (block.kind) {
            "exercise" -> ExerciseBlock(block, fontScale, revealed = revealed, onReveal = onReveal)
            "recap" -> RecapBlock(block, fontScale)
            "figure" -> FigureBlock(block, fontScale)
            else -> UnknownFenceBlock(block, fontScale)
        }
    }
}

@Composable
private fun HeadingBlock(h: Block.Heading, fontScale: Float) {
    val (baseSize, top, bottom) = when (h.level) {
        1 -> Triple(30.sp, 24.dp, 12.dp)
        2 -> Triple(24.sp, 20.dp, 10.dp)
        3 -> Triple(20.sp, 16.dp, 8.dp)
        else -> Triple(18.sp, 14.dp, 6.dp)
    }
    Spacer(Modifier.height(top))
    Text(
        text = renderInlines(h.inlines),
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = baseSize * fontScale,
        color = InkColor,
    )
    Spacer(Modifier.height(bottom))
}

@Composable
private fun ParagraphBlock(inlines: List<Inline>, fontScale: Float) {
    Text(
        text = renderInlines(inlines),
        fontFamily = FontFamily.Serif,
        fontSize = 18.sp * fontScale,
        lineHeight = 30.sp * fontScale,
        color = InkColor,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun ListItemBlock(prefix: String, inlines: List<Inline>, fontScale: Float) {
    Row(modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)) {
        Text(
            text = prefix,
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp * fontScale,
            lineHeight = 30.sp * fontScale,
            color = InkColor,
        )
        Text(
            text = renderInlines(inlines),
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp * fontScale,
            lineHeight = 30.sp * fontScale,
            color = InkColor,
        )
    }
}

@Composable
private fun ExerciseBlock(f: Block.CustomFence, fontScale: Float, revealed: Boolean, onReveal: () -> Unit) {
    val (question, answer) = splitExerciseBody(f.body)
    BorderedBlock(label = "TRY THIS", fontScale = fontScale) {
        Text(
            text = question,
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp * fontScale,
            lineHeight = 28.sp * fontScale,
            color = InkColor,
        )
        Spacer(Modifier.height(8.dp))
        if (revealed || answer.isEmpty()) {
            if (answer.isNotEmpty()) {
                Text(
                    text = "Answer: $answer",
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp * fontScale,
                    lineHeight = 26.sp * fontScale,
                    fontStyle = FontStyle.Italic,
                    color = InkColor,
                )
            }
        } else {
            TextButton(onClick = onReveal) { Text("Show answer", color = InkColor) }
        }
    }
}

@Composable
private fun RecapBlock(f: Block.CustomFence, fontScale: Float) {
    BorderedBlock(label = "RECAP", fontScale = fontScale) {
        Text(
            text = f.body.trim(),
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp * fontScale,
            lineHeight = 28.sp * fontScale,
            color = InkColor,
        )
    }
}

@Composable
private fun FigureBlock(f: Block.CustomFence, fontScale: Float) {
    val alt = f.params["alt"] ?: f.body.trim().ifBlank { "(figure)" }
    BorderedBlock(label = "FIGURE", fontScale = fontScale) {
        Text(
            text = alt,
            fontFamily = FontFamily.Serif,
            fontSize = 16.sp * fontScale,
            lineHeight = 26.sp * fontScale,
            fontStyle = FontStyle.Italic,
            color = MutedColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun UnknownFenceBlock(f: Block.CustomFence, fontScale: Float) {
    BorderedBlock(label = f.kind.uppercase().ifBlank { "CODE" }, fontScale = fontScale) {
        Text(
            text = f.body,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp * fontScale,
            color = InkColor,
        )
    }
}

@Composable
private fun BorderedBlock(label: String, fontScale: Float, content: @Composable () -> Unit) {
    Spacer(Modifier.height(4.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
            .padding(12.dp),
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp * fontScale,
            color = MutedColor,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        content()
    }
    Spacer(Modifier.height(4.dp))
}

private fun splitExerciseBody(body: String): Pair<String, String> {
    val lines = body.lines()
    val answerIdx = lines.indexOfFirst { it.trim().lowercase().startsWith("answer:") }
    if (answerIdx == -1) return body.trim() to ""
    val question = lines.take(answerIdx).joinToString("\n").trim()
    val answerLine = lines[answerIdx].substringAfter(":").trim()
    val extra = lines.drop(answerIdx + 1).joinToString("\n").trim()
    val answer = if (extra.isEmpty()) answerLine else "$answerLine\n$extra"
    return question to answer
}

private fun renderInlines(inlines: List<Inline>): AnnotatedString = buildAnnotatedString {
    inlines.forEach { i ->
        when (i) {
            is Inline.Text -> append(i.text)
            is Inline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(i.text) }
            is Inline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(i.text) }
            is Inline.Code -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x14000000)),
            ) { append(i.text) }
        }
    }
}
