package com.inkfirst.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkfirst.app.data.ReaderPrefs
import com.inkfirst.app.data.TextAlignPref
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
    onOpenContents: () -> Unit,
) {
    val context = LocalContext.current
    val store = rememberCourseStore()
    val readerPrefs = remember { ReaderPrefs(context) }

    var course by remember { mutableStateOf<Course?>(null) }
    var markdown by remember { mutableStateOf<String?>(null) }
    var fontScale by remember { mutableStateOf(readerPrefs.getFontScale()) }
    var lineSpacing by remember { mutableStateOf(readerPrefs.getLineSpacing()) }
    var textAlign by remember { mutableStateOf(readerPrefs.getTextAlign()) }
    var showFormatMenu by remember { mutableStateOf(false) }
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
                lineSpacing = lineSpacing,
                textAlign = textAlign,
                anchorBlockIndex = anchorBlockIndex,
                menuOpen = showFormatMenu,
                onAnchorChange = { anchorBlockIndex = it },
                onToggleMenu = { showFormatMenu = !showFormatMenu },
                onBack = onBack,
                onBookmarkToggle = { newCourse -> course = newCourse ?: course },
            )
        }

        if (showFormatMenu) {
            FormatMenu(
                fontScale = fontScale,
                lineSpacing = lineSpacing,
                textAlign = textAlign,
                onOpenContents = {
                    showFormatMenu = false
                    onOpenContents()
                },
                onFontSmaller = {
                    val idx = ReaderPrefs.FONT_SCALES.indexOfNearest(fontScale)
                    if (idx > 0) {
                        fontScale = ReaderPrefs.FONT_SCALES[idx - 1]
                        readerPrefs.setFontScale(fontScale)
                    }
                },
                onFontLarger = {
                    val idx = ReaderPrefs.FONT_SCALES.indexOfNearest(fontScale)
                    if (idx < ReaderPrefs.FONT_SCALES.lastIndex) {
                        fontScale = ReaderPrefs.FONT_SCALES[idx + 1]
                        readerPrefs.setFontScale(fontScale)
                    }
                },
                onLineSpacingChange = { newSpacing ->
                    lineSpacing = newSpacing
                    readerPrefs.setLineSpacing(newSpacing)
                },
                onTextAlignChange = { newAlign ->
                    textAlign = newAlign
                    readerPrefs.setTextAlign(newAlign)
                },
                modifier = Modifier.align(Alignment.BottomCenter),
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
    lineSpacing: Float,
    textAlign: TextAlignPref,
    anchorBlockIndex: Int,
    menuOpen: Boolean,
    onAnchorChange: (Int) -> Unit,
    onToggleMenu: () -> Unit,
    onBack: () -> Unit,
    onBookmarkToggle: (Course?) -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val store = rememberCourseStore()

    val chapterN = course?.chapters?.firstOrNull { it.slug == chapterSlug }?.n

    Column(modifier = Modifier.fillMaxSize()) {
        ReaderHeader(chapterN = chapterN, chapterCount = course?.chapters?.size, onBack = onBack)

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val widthPx = with(density) { maxWidth.toPx().toInt() }
            val availableHeightPx = with(density) { (maxHeight - 40.dp).toPx().toInt() }

            val pages = remember(blocks, widthPx, availableHeightPx, fontScale, lineSpacing) {
                paginate(
                    blocks = blocks,
                    contentWidthPx = widthPx,
                    contentHeightPx = availableHeightPx,
                    density = density,
                    measurer = measurer,
                    fontScale = fontScale,
                    lineSpacing = lineSpacing,
                )
            }
            val pageIndex = remember(pages, anchorBlockIndex) {
                findPageContainingBlock(pages, blocks, anchorBlockIndex).coerceAtLeast(0)
            }
            val revealed = remember(blocks) { mutableStateMapOf<Int, Boolean>() }

            // The block index of a page's first block is just the sum of block counts
            // in all prior pages — paginate() preserves order and never splits blocks.
            val firstBlockIndex = pages.take(pageIndex).sumOf { it.size }
            val isBookmarked = course?.bookmarks?.any {
                it.chapterSlug == chapterSlug && it.blockIndex == firstBlockIndex
            } == true

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // pageIndex MUST be in the key list — without it, the tap closure
                        // captures a stale pageIndex and every tap advances from 0.
                        .pointerInput(pageIndex, pages.size, menuOpen) {
                            detectTapGestures { pos ->
                                if (menuOpen) {
                                    onToggleMenu()
                                    return@detectTapGestures
                                }
                                val third = size.width / 3f
                                when {
                                    pos.x < third -> {
                                        if (pageIndex > 0) {
                                            val prevAnchor = pages.take(pageIndex - 1).sumOf { it.size }
                                            onAnchorChange(prevAnchor)
                                        }
                                    }
                                    pos.x > 2 * third -> {
                                        if (pageIndex < pages.size - 1) {
                                            val nextAnchor = pages.take(pageIndex + 1).sumOf { it.size }
                                            onAnchorChange(nextAnchor)
                                        }
                                    }
                                    else -> onToggleMenu()
                                }
                            }
                        },
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val pageStart = firstBlockIndex  // computed above, arithmetic
                        pages.getOrNull(pageIndex)?.forEachIndexed { i, block ->
                            val globalIdx = pageStart + i
                            RenderBlock(
                                block = block,
                                fontScale = fontScale,
                                lineSpacing = lineSpacing,
                                textAlign = textAlign,
                                revealed = revealed[globalIdx] == true,
                                onReveal = { revealed[globalIdx] = true },
                            )
                        }
                    }

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
                        text = "tap ← / center / → ",
                        style = MaterialTheme.typography.bodySmall.copy(color = MutedColor),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderHeader(chapterN: Int?, chapterCount: Int?, onBack: () -> Unit) {
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
    }
}

/**
 * Bottom-panel formatting menu. Opens on center-tap; dismissed by tapping
 * anywhere outside (handled by the parent tap detector) or via one of its
 * own controls without an explicit close button (feels lighter that way).
 */
@Composable
private fun FormatMenu(
    fontScale: Float,
    lineSpacing: Float,
    textAlign: TextAlignPref,
    onOpenContents: () -> Unit,
    onFontSmaller: () -> Unit,
    onFontLarger: () -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onTextAlignChange: (TextAlignPref) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canShrink = ReaderPrefs.FONT_SCALES.indexOfNearest(fontScale) > 0
    val canGrow = ReaderPrefs.FONT_SCALES.indexOfNearest(fontScale) < ReaderPrefs.FONT_SCALES.lastIndex

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PaperColor)
            .border(width = 1.dp, color = BorderColor)
            // Consume taps so they don't bubble to the parent's dismiss handler.
            .pointerInput(Unit) { detectTapGestures { /* swallow */ } }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Contents jump
        MenuRow(label = "Go to") {
            Box(
                modifier = Modifier
                    .clickable { onOpenContents() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Contents",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkColor,
                )
            }
        }

        // Font size
        MenuRow(label = "Font") {
            TextButton(onClick = onFontSmaller, enabled = canShrink) {
                Text("A−", fontSize = 16.sp, color = if (canShrink) InkColor else MutedColor)
            }
            Text(
                text = "%.2fx".format(fontScale),
                style = MaterialTheme.typography.bodySmall.copy(color = MutedColor),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            TextButton(onClick = onFontLarger, enabled = canGrow) {
                Text("A+", fontSize = 18.sp, color = if (canGrow) InkColor else MutedColor)
            }
        }

        // Line spacing
        MenuRow(label = "Spacing") {
            ReaderPrefs.LINE_SPACINGS.forEachIndexed { i, v ->
                SelectableChip(
                    label = ReaderPrefs.LINE_SPACING_LABELS[i],
                    selected = kotlin.math.abs(lineSpacing - v) < 0.01f,
                    onClick = { onLineSpacingChange(v) },
                )
            }
        }

        // Alignment
        MenuRow(label = "Align") {
            TextAlignPref.entries.forEach { pref ->
                SelectableChip(
                    label = pref.label,
                    selected = textAlign == pref,
                    onClick = { onTextAlignChange(pref) },
                )
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(color = InkColor),
            modifier = Modifier.width(70.dp),
        )
        content()
    }
}

@Composable
private fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) 1.dp else 0.dp
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .then(
                if (selected) Modifier.border(border, InkColor, RoundedCornerShape(4.dp))
                else Modifier,
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = InkColor,
        )
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
private fun RenderBlock(
    block: Block,
    fontScale: Float,
    lineSpacing: Float,
    textAlign: TextAlignPref,
    revealed: Boolean,
    onReveal: () -> Unit,
) {
    when (block) {
        is Block.Heading -> HeadingBlock(block, fontScale)
        is Block.Paragraph -> ParagraphBlock(block.inlines, fontScale, lineSpacing, textAlign)
        is Block.BulletItem -> ListItemBlock("• ", block.inlines, fontScale, lineSpacing)
        is Block.OrderedItem -> ListItemBlock("${block.n}. ", block.inlines, fontScale, lineSpacing)
        is Block.CustomFence -> when (block.kind) {
            "exercise" -> ExerciseBlock(block, fontScale, lineSpacing, revealed, onReveal)
            "recap" -> RecapBlock(block, fontScale, lineSpacing)
            "figure" -> FigureBlock(block, fontScale, lineSpacing)
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
    val fontSize = baseSize * fontScale
    Spacer(Modifier.height(top))
    Text(
        text = renderInlines(h.inlines),
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        lineHeight = fontSize * 1.2f,
        color = InkColor,
    )
    Spacer(Modifier.height(bottom))
}

@Composable
private fun ParagraphBlock(
    inlines: List<Inline>,
    fontScale: Float,
    lineSpacing: Float,
    textAlign: TextAlignPref,
) {
    Text(
        text = renderInlines(inlines),
        style = androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp * fontScale,
            lineHeight = 30.sp * fontScale * lineSpacing,
            color = InkColor,
            textAlign = if (textAlign == TextAlignPref.JUSTIFY) TextAlign.Justify else TextAlign.Start,
            hyphens = Hyphens.Auto,
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
}

@Composable
private fun ListItemBlock(prefix: String, inlines: List<Inline>, fontScale: Float, lineSpacing: Float) {
    Row(modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)) {
        Text(
            text = prefix,
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp * fontScale,
            lineHeight = 30.sp * fontScale * lineSpacing,
            color = InkColor,
        )
        Text(
            text = renderInlines(inlines),
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp * fontScale,
            lineHeight = 30.sp * fontScale * lineSpacing,
            color = InkColor,
        )
    }
}

@Composable
private fun ExerciseBlock(
    f: Block.CustomFence,
    fontScale: Float,
    lineSpacing: Float,
    revealed: Boolean,
    onReveal: () -> Unit,
) {
    val (question, answer) = splitExerciseBody(f.body)
    BorderedBlock(label = "TRY THIS", fontScale = fontScale) {
        Text(
            text = question,
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp * fontScale,
            lineHeight = 28.sp * fontScale * lineSpacing,
            color = InkColor,
        )
        Spacer(Modifier.height(8.dp))
        if (revealed || answer.isEmpty()) {
            if (answer.isNotEmpty()) {
                Text(
                    text = "Answer: $answer",
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp * fontScale,
                    lineHeight = 26.sp * fontScale * lineSpacing,
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
private fun RecapBlock(f: Block.CustomFence, fontScale: Float, lineSpacing: Float) {
    BorderedBlock(label = "RECAP", fontScale = fontScale) {
        Text(
            text = f.body.trim(),
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp * fontScale,
            lineHeight = 28.sp * fontScale * lineSpacing,
            color = InkColor,
        )
    }
}

@Composable
private fun FigureBlock(f: Block.CustomFence, fontScale: Float, lineSpacing: Float) {
    val alt = f.params["alt"] ?: f.body.trim().ifBlank { "(figure)" }
    BorderedBlock(label = "FIGURE", fontScale = fontScale) {
        Text(
            text = alt,
            fontFamily = FontFamily.Serif,
            fontSize = 16.sp * fontScale,
            lineHeight = 26.sp * fontScale * lineSpacing,
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
