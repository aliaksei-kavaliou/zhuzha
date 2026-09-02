package com.inkfirst.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.runtime.CompositionLocalProvider
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
import com.inkfirst.app.data.ApiKeyStore
import com.inkfirst.app.data.ReaderPrefs
import com.inkfirst.app.data.TextAlignPref
import com.inkfirst.app.data.rememberCourseStore
import com.inkfirst.core.Block
import com.inkfirst.core.ChapterGenerator
import com.inkfirst.core.ChapterStatus
import com.inkfirst.core.Course
import com.inkfirst.core.Inline
import com.inkfirst.core.KtorGeminiClient
import com.inkfirst.core.MemoryUpdater
import com.inkfirst.core.PersistedChapter
import com.inkfirst.core.parseMarkdown
import kotlinx.coroutines.launch
import java.time.LocalDate

// E-ink can only render pure black + pure white directly; anything between
// gets dithered (visible dot pattern). Our warm off-white paper looked nice on
// screens but rendered as noise on the Boox. Pure black on pure white is the
// canonical e-ink palette.
private val InkColor = Color(0xFF000000)
private val PaperColor = Color(0xFFFFFFFF)
private val MutedColor = Color(0xFF555555)
private val BorderColor = Color(0xFF888888)

private sealed interface CompileState {
    data object Idle : CompileState
    data object Running : CompileState
    data class Failed(val message: String) : CompileState
}

@Composable
fun ReaderScreen(
    courseSlug: String,
    chapterSlug: String,
    startBlockIndex: Int = 0,
    onBack: () -> Unit,
    onOpenContents: () -> Unit,
    onOpenChapter: (chapterSlug: String, blockIndex: Int) -> Unit,
) {
    val context = LocalContext.current
    val store = rememberCourseStore()
    val apiKeyStore = remember { ApiKeyStore(context) }
    val readerPrefs = remember { ReaderPrefs(context) }
    val scope = rememberCoroutineScope()

    var course by remember { mutableStateOf<Course?>(null) }
    var markdown by remember { mutableStateOf<String?>(null) }
    var fontScale by remember { mutableStateOf(readerPrefs.getFontScale()) }
    var lineSpacing by remember { mutableStateOf(readerPrefs.getLineSpacing()) }
    var textAlign by remember { mutableStateOf(readerPrefs.getTextAlign()) }
    var showFormatMenu by remember { mutableStateOf(false) }
    // null = closed. "" = open general chat. non-empty = open with quoted passage.
    var chatSelection by remember(chapterSlug) { mutableStateOf<String?>(null) }
    // The Ask toolbar for the paged content — intercepts text-selection UI.
    // Hoisted to ReaderScreen scope so the floating chip can live in the outer
    // Box (which is what supports Alignment.BottomCenter).
    val clipboard = LocalClipboardManager.current
    val askToolbar = remember(clipboard) {
        AskTextToolbar(clipboard) { selectedText -> chatSelection = selectedText }
    }
    // startBlockIndex = -1 is the "open at last block" sentinel — used when
    // flipping left off the first page of a chapter into the previous
    // chapter's tail. Resolved once blocks are parsed (LaunchedEffect below).
    var anchorBlockIndex by remember(chapterSlug) { mutableStateOf(startBlockIndex.coerceAtLeast(0)) }
    var compileState by remember(chapterSlug) { mutableStateOf<CompileState>(CompileState.Idle) }

    LaunchedEffect(courseSlug, chapterSlug) {
        course = store.load(courseSlug)
        markdown = store.readChapter(courseSlug, chapterSlug)
    }

    val chapter = course?.chapters?.firstOrNull { it.slug == chapterSlug }
    val blocks = remember(markdown, chapter) {
        val parsed = markdown?.let { parseMarkdown(it) } ?: emptyList()
        if (chapter == null || parsed.isEmpty()) parsed
        else {
            // Small italic intro line — shows est-minutes + status once at the
            // start of the chapter so the TOC can stay clean.
            val meta = Block.CustomFence(
                kind = "meta",
                params = emptyMap(),
                body = "~${chapter.estMinutes} min · ${statusLabel(chapter.status)}",
            )
            listOf(meta) + parsed
        }
    }

    // Resolve the "-1 = open at last" sentinel once blocks arrive.
    LaunchedEffect(blocks, startBlockIndex) {
        if (startBlockIndex < 0 && blocks.isNotEmpty()) {
            anchorBlockIndex = blocks.lastIndex
        }
    }

    // Persist the anchor whenever it changes — this is the "last block read"
    // that Contents uses to resume you where you left off.
    LaunchedEffect(anchorBlockIndex, courseSlug, chapterSlug) {
        if (course != null && chapter != null && blocks.isNotEmpty()) {
            store.updateChapterProgress(courseSlug, chapter.n, anchorBlockIndex)
        }
    }

    // Status transitions:
    //   GENERATED → READING when the reader opens a fresh chapter
    //   READING   → DONE    when the reader reaches the last block
    LaunchedEffect(chapter, anchorBlockIndex, blocks) {
        val c = course ?: return@LaunchedEffect
        val ch = chapter ?: return@LaunchedEffect
        val newStatus = when {
            ch.status == ChapterStatus.GENERATED -> ChapterStatus.READING
            ch.status == ChapterStatus.READING &&
                blocks.isNotEmpty() &&
                anchorBlockIndex >= blocks.lastIndex -> ChapterStatus.DONE
            else -> null
        }
        if (newStatus != null) {
            val updated = store.updateChapterStatus(c.slug, ch.n, newStatus)
            if (updated != null) course = updated
        }
    }

    val isPending = chapter != null && chapter.status == ChapterStatus.PENDING && markdown == null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperColor),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            if (isPending) {
                CompilePrompt(
                    chapter = chapter!!,
                    chapterCount = course?.chapters?.size ?: 0,
                    compileState = compileState,
                    onBack = onBack,
                    onCompile = {
                        val c = course ?: return@CompilePrompt
                        compileChapter(
                            course = c,
                            chapterN = chapter.n,
                            scope = scope,
                            store = store,
                            apiKeyStore = apiKeyStore,
                            onState = { compileState = it },
                            onDone = { updatedCourse, newMarkdown ->
                                if (updatedCourse != null && newMarkdown != null) {
                                    course = updatedCourse
                                    markdown = newMarkdown
                                }
                            },
                        )
                    },
                )
                return@Column
            }

            val chapterIndex = course?.chapters?.indexOfFirst { it.slug == chapterSlug } ?: -1
            val prevChapter = course?.chapters?.getOrNull(chapterIndex - 1)
            val nextChapter = course?.chapters?.getOrNull(chapterIndex + 1)

            CompositionLocalProvider(LocalTextToolbar provides askToolbar) {
                PagedContent(
                    blocks = blocks,
                    course = course,
                    chapterSlug = chapterSlug,
                    fontScale = fontScale,
                    lineSpacing = lineSpacing,
                    textAlign = textAlign,
                    anchorBlockIndex = anchorBlockIndex,
                    onAskAboutBlock = { text -> chatSelection = text },
                    onOpenPrevChapter = prevChapter?.let { p -> { onOpenChapter(p.slug, -1) } },
                    onOpenNextChapter = nextChapter?.let { n -> { onOpenChapter(n.slug, 0) } },
                    menuOpen = showFormatMenu,
                    onAnchorChange = { anchorBlockIndex = it },
                    onToggleMenu = { showFormatMenu = !showFormatMenu },
                    onBack = onBack,
                    onBookmarkToggle = { newCourse -> course = newCourse ?: course },
                )
            }
        }

        // Floating Ask chip — appears when there's an active text selection in
        // the reader. Lives here in the outer Box (which supports
        // Alignment.BottomCenter) rather than inside the Column-based content.
        askToolbar.selectionRect?.let {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .background(InkColor, RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                TextButton(onClick = { askToolbar.ask() }) {
                    Text("Ask about this", color = PaperColor)
                }
                TextButton(onClick = { askToolbar.hide() }) {
                    Text("Cancel", color = PaperColor)
                }
            }
        }

        // Chat panel sits on top of everything when open.
        if (chatSelection != null && course != null && chapter != null && markdown != null) {
            ChatPanel(
                course = course!!,
                chapter = chapter,
                chapterMarkdown = markdown!!,
                initialSelection = chatSelection?.takeIf { it.isNotBlank() },
                onClose = { chatSelection = null },
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
                onAskAboutChapter = {
                    showFormatMenu = false
                    chatSelection = ""  // open with no selection = general Q&A
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
    onOpenPrevChapter: (() -> Unit)?,
    onOpenNextChapter: (() -> Unit)?,
    onAskAboutBlock: (String) -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val store = rememberCourseStore()

    val chapterN = course?.chapters?.firstOrNull { it.slug == chapterSlug }?.n

    // Hoist the bookmark state up so the header (rendered above the paged content)
    // can show it and toggle it. The actual read of `firstBlockIndex` etc. happens
    // inside the BoxWithConstraints below.
    Column(modifier = Modifier.fillMaxSize()) {
        // The header's bookmark button needs to know the current page's first
        // block. That's computed inside PagedContent's BoxWithConstraints scope,
        // so we forward a callback + the observed state via the header inputs.
        var headerFirstBlock by remember(chapterSlug) { mutableStateOf(-1) }
        val headerBookmarked = course?.bookmarks?.any {
            it.chapterSlug == chapterSlug && it.blockIndex == headerFirstBlock
        } == true
        ReaderHeader(
            chapterN = chapterN,
            chapterCount = course?.chapters?.size,
            isBookmarked = headerBookmarked,
            canBookmark = headerFirstBlock >= 0 && course != null,
            onBack = onBack,
            onBookmarkToggle = {
                val c = course ?: return@ReaderHeader
                if (headerFirstBlock < 0) return@ReaderHeader
                val updated = if (headerBookmarked) {
                    store.removeBookmark(c.slug, chapterSlug, headerFirstBlock)
                } else {
                    store.addBookmark(c.slug, chapterSlug, headerFirstBlock, LocalDate.now().toString())
                }
                onBookmarkToggle(updated)
            },
        )

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
            LaunchedEffect(firstBlockIndex) { headerFirstBlock = firstBlockIndex }

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // Unified gesture handler — tap for zoned page-turn, horizontal
                        // drag for swipe-to-turn. Compose's gesture arbitration between
                        // two separate pointerInput modifiers got confused after certain
                        // recompositions (e.g., after a bookmark toggle) and one detector
                        // would go silent. Handling both in one awaitEachGesture avoids it.
                        .pointerInput(pageIndex, pages.size, menuOpen) {
                            val dragThresholdPx = 60.dp.toPx()
                            val dragStartPx = 10.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downPos = down.position
                                var totalDx = 0f
                                var isDrag = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) {
                                        if (menuOpen) {
                                            onToggleMenu()
                                        } else if (isDrag) {
                                            if (totalDx <= -dragThresholdPx) {
                                                goNext(pageIndex, pages, onAnchorChange, onOpenNextChapter)
                                            } else if (totalDx >= dragThresholdPx) {
                                                goPrev(pageIndex, pages, onAnchorChange, onOpenPrevChapter)
                                            }
                                        } else {
                                            // Plain tap.
                                            val third = size.width / 3f
                                            when {
                                                downPos.x < third -> goPrev(pageIndex, pages, onAnchorChange, onOpenPrevChapter)
                                                downPos.x > 2 * third -> goNext(pageIndex, pages, onAnchorChange, onOpenNextChapter)
                                                else -> onToggleMenu()
                                            }
                                        }
                                        break
                                    }
                                    val dx = change.positionChange().x
                                    totalDx += dx
                                    if (!isDrag && kotlin.math.abs(totalDx) > dragStartPx) isDrag = true
                                }
                            }
                        },
                ) {
                    // SelectionContainer lets the user long-press-drag to select
                    // specific text; our custom AskTextToolbar (installed via
                    // LocalTextToolbar above) is what surfaces the "Ask" action
                    // for whatever gets selected.
                    SelectionContainer {
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
                    }

                    // Bookmark used to live here as an overlay in the top-right corner,
                    // but at 96dp it was eating page-turn taps/swipes. Moved to the
                    // header row — small ribbon icon next to Ch N/M.
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
private fun ReaderHeader(
    chapterN: Int?,
    chapterCount: Int?,
    isBookmarked: Boolean,
    canBookmark: Boolean,
    onBack: () -> Unit,
    onBookmarkToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
    ) {
        // The header button pops back to the last screen on the nav stack,
        // which in practice is always Contents — labeling it "Contents"
        // makes the destination explicit, no functional change.
        TextButton(onClick = onBack) { Text("≡ Contents", color = InkColor) }
        Spacer(Modifier.weight(1f))
        if (chapterN != null && chapterCount != null) {
            Text(
                text = "Ch $chapterN/$chapterCount",
                style = MaterialTheme.typography.bodySmall.copy(color = MutedColor),
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        // Small ribbon-toggle button. Fills when the current page's first block
        // is bookmarked; outline when not. Fixed spot in the header so it never
        // competes with tap/swipe gestures in the reading area.
        TextButton(
            onClick = onBookmarkToggle,
            enabled = canBookmark,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
        ) {
            Canvas(modifier = Modifier.size(width = 14.dp, height = 20.dp)) {
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
                if (isBookmarked) {
                    drawPath(path, color = InkColor)
                } else {
                    drawPath(path, color = InkColor, style = Stroke(width = 2f))
                }
            }
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
    onAskAboutChapter: () -> Unit,
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

        // Ask about this chapter (general Q&A, no selection)
        MenuRow(label = "Ask") {
            Box(
                modifier = Modifier
                    .clickable { onAskAboutChapter() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "About this chapter",
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
            "meta" -> MetaBlock(block, fontScale)
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
private fun MetaBlock(f: Block.CustomFence, fontScale: Float) {
    // Small italic muted line at the top of the chapter — the transplanted
    // TOC subtitle. Not a bordered box; it should feel like a byline, not a
    // block.
    Text(
        text = f.body,
        fontFamily = FontFamily.Serif,
        fontStyle = FontStyle.Italic,
        fontSize = 13.sp * fontScale,
        color = MutedColor,
        modifier = Modifier.padding(bottom = 8.dp),
    )
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
    // 96dp tap zone — "whole top-right corner". At 300 DPI on the Boox this is
    // about a fingertip's worth. We're only losing the top-right of the
    // right-third tap-to-turn area; users have swipe as the primary
    // page-turn gesture anyway.
    Box(
        modifier = modifier
            .size(96.dp)
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
                    .padding(end = 12.dp, top = 0.dp)
                    .size(width = 24.dp, height = 32.dp),
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

/**
 * Extract long-pressable plain text from a block. Only prose blocks are
 * askable (paragraphs, list items, recap/exercise bodies). Headings and
 * figures don't offer a chat entry point — a heading isn't a "passage" to
 * ask about, and figures already have alt-text that's the passage.
 */
private fun extractAskableText(block: Block): String? = when (block) {
    is Block.Paragraph -> block.inlines.plainText()
    is Block.BulletItem -> block.inlines.plainText()
    is Block.OrderedItem -> block.inlines.plainText()
    is Block.CustomFence -> when (block.kind) {
        "recap", "exercise" -> block.body.trim()
        else -> null
    }
    else -> null
}

private fun List<Inline>.plainText(): String = joinToString("") { i ->
    when (i) {
        is Inline.Text -> i.text
        is Inline.Bold -> i.text
        is Inline.Italic -> i.text
        is Inline.Code -> i.text
    }
}

private fun goPrev(
    pageIndex: Int,
    pages: List<List<Block>>,
    onAnchorChange: (Int) -> Unit,
    onOpenPrevChapter: (() -> Unit)?,
) {
    if (pageIndex > 0) {
        val prevAnchor = pages.take(pageIndex - 1).sumOf { it.size }
        onAnchorChange(prevAnchor)
    } else {
        onOpenPrevChapter?.invoke()
    }
}

private fun goNext(
    pageIndex: Int,
    pages: List<List<Block>>,
    onAnchorChange: (Int) -> Unit,
    onOpenNextChapter: (() -> Unit)?,
) {
    if (pageIndex < pages.size - 1) {
        val nextAnchor = pages.take(pageIndex + 1).sumOf { it.size }
        onAnchorChange(nextAnchor)
    } else {
        onOpenNextChapter?.invoke()
    }
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

@Composable
private fun CompilePrompt(
    chapter: PersistedChapter,
    chapterCount: Int,
    compileState: CompileState,
    onBack: () -> Unit,
    onCompile: () -> Unit,
) {
    // Book-native "front matter" for a not-yet-compiled chapter: user sees the
    // title, meta, and objectives before spending a Gemini call. Tap Compile
    // to generate; on success the Reader auto-flips to the paginated content.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back", color = InkColor) }
        Spacer(Modifier.weight(1f))
        Text(
            text = "Ch ${chapter.n}/$chapterCount",
            style = MaterialTheme.typography.bodySmall.copy(color = MutedColor),
        )
    }

    Spacer(Modifier.height(20.dp))
    Text(
        text = "~${chapter.estMinutes} min · not compiled",
        fontFamily = FontFamily.Serif,
        fontStyle = FontStyle.Italic,
        fontSize = 13.sp,
        color = MutedColor,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Chapter ${chapter.n}: ${chapter.title}",
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        color = InkColor,
    )

    if (chapter.objectives.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = "What you'll learn",
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MutedColor,
        )
        Spacer(Modifier.height(8.dp))
        chapter.objectives.forEach { obj ->
            Row(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = "• ",
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp,
                    color = InkColor,
                )
                Text(
                    text = obj,
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = InkColor,
                )
            }
        }
    }

    Spacer(Modifier.height(28.dp))

    when (compileState) {
        CompileState.Running -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).padding(end = 8.dp),
                    color = InkColor,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Compiling chapter…",
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                    color = InkColor,
                )
            }
        }
        is CompileState.Failed -> {
            Text(
                text = compileState.message,
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Button(
                onClick = onCompile,
                colors = ButtonDefaults.buttonColors(containerColor = InkColor),
            ) {
                Text("Try again", color = PaperColor)
            }
        }
        CompileState.Idle -> {
            Button(
                onClick = onCompile,
                colors = ButtonDefaults.buttonColors(containerColor = InkColor),
            ) {
                Text("Compile chapter", color = PaperColor)
            }
        }
    }
}

private fun compileChapter(
    course: Course,
    chapterN: Int,
    scope: kotlinx.coroutines.CoroutineScope,
    store: com.inkfirst.core.CourseStore,
    apiKeyStore: ApiKeyStore,
    onState: (CompileState) -> Unit,
    onDone: (Course?, String?) -> Unit,
) {
    val apiKey = apiKeyStore.getApiKey().orEmpty()
    val model = apiKeyStore.getModelName()
    if (apiKey.isBlank()) {
        onState(CompileState.Failed("No API key set. Go to Settings first."))
        return
    }
    onState(CompileState.Running)
    scope.launch {
        try {
            KtorGeminiClient().use { client ->
                val memory = store.readMemory(course.slug)
                val markdown = ChapterGenerator(client, model).generate(course, chapterN, memory, apiKey)
                val chapter = course.chapters.first { it.n == chapterN }
                store.saveChapter(course.slug, chapter.slug, markdown)
                val updated = store.updateChapterStatus(course.slug, chapterN, ChapterStatus.GENERATED)
                try {
                    val summary = MemoryUpdater(client, model).summarize(course, chapterN, markdown, apiKey)
                    store.appendMemory(course.slug, chapterN, chapter.title, summary)
                } catch (e: Exception) {
                    System.err.println("memory update failed for chapter $chapterN: ${e.message}")
                }
                onState(CompileState.Idle)
                onDone(updated, markdown)
            }
        } catch (e: Exception) {
            onState(CompileState.Failed(e.message ?: "Unknown error"))
        }
    }
}
