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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inkfirst.app.data.ApiKeyStore
import com.inkfirst.app.data.ReaderPrefs
import com.inkfirst.app.data.TextAlignPref
import com.inkfirst.app.data.geminiErrorLabel
import com.inkfirst.app.data.rememberCourseStore
import com.inkfirst.core.Block
import com.inkfirst.core.ChapterGenerator
import com.inkfirst.core.ChapterStatus
import com.inkfirst.core.Annotation
import com.inkfirst.core.Course
import com.inkfirst.core.Inline
import com.inkfirst.core.KtorGeminiClient
import com.inkfirst.core.MemoryUpdater
import com.inkfirst.core.PersistedChapter
import com.inkfirst.core.Stroke as InkStroke
import com.inkfirst.core.courseDaySchedule
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
    // Chapter-scoped freehand strokes. Load in the same effect that pulls
    // the chapter markdown so both are ready by the time PagedContent runs.
    var strokes by remember(chapterSlug) { mutableStateOf<List<InkStroke>>(emptyList()) }
    // Dev toggle read once per session; the Settings screen writes it and it
    // takes effect on next chapter open (no live rebinding — a chapter's
    // gestures are re-keyed on chapter change so this is fine).
    val fingerDraws = remember { readerPrefs.getFingerDraws() }
    // Hoisted here so both the header (bookmark ribbon indicator) and the
    // format menu (Bookmark this page row) can read/toggle without prop-drilling.
    var headerFirstBlock by remember(chapterSlug) { mutableStateOf(-1) }
    val headerBookmarked = course?.bookmarks?.any {
        it.chapterSlug == chapterSlug && it.blockIndex == headerFirstBlock
    } == true
    var fontScale by remember { mutableStateOf(readerPrefs.getFontScale()) }
    var lineSpacing by remember { mutableStateOf(readerPrefs.getLineSpacing()) }
    var textAlign by remember { mutableStateOf(readerPrefs.getTextAlign()) }
    var horizontalPadding by remember { mutableStateOf(readerPrefs.getHorizontalPadding()) }
    var showFormatMenu by remember { mutableStateOf(false) }
    // null = closed. "" = open general chat. non-empty = open with quoted passage.
    var chatSelection by remember(chapterSlug) { mutableStateOf<String?>(null) }
    // When non-null, an AddNote overlay is up with this quoted passage.
    var pendingNoteQuote by remember(chapterSlug) { mutableStateOf<String?>(null) }
    // When non-null, the NoteView overlay shows this annotation for read + delete.
    var viewingAnnotation by remember(chapterSlug) { mutableStateOf<Annotation?>(null) }
    // When non-null, a chip is showing for a tapped drawing — mirrors the
    // text-selection chip's actions (Ask / Add note / Delete). Anchor block
    // (and thus context text) is resolved lazily from stroke.blockIndex.
    var viewingStroke by remember(chapterSlug) { mutableStateOf<InkStroke?>(null) }
    // Fresh enclosure gesture waiting for the user's action. Distinct from
    // viewingStroke because the semantics differ: this stroke will be
    // removed on Ask (via chatOriginStroke) or Cancel, kept on Add note.
    var pendingGestureStroke by remember(chapterSlug) { mutableStateOf<InkStroke?>(null) }
    // Set when a chat was opened as the result of an enclosure gesture, so
    // the ChatPanel's onClose knows to remove that stroke afterwards (the
    // stroke served as a selection gesture, not a persistent mark).
    var chatOriginStroke by remember(chapterSlug) { mutableStateOf<InkStroke?>(null) }
    // The Ask toolbar for the paged content — intercepts text-selection UI.
    // Hoisted to ReaderScreen scope so the floating chip can live in the outer
    // Box (which is what supports Alignment.BottomCenter).
    val clipboard = LocalClipboardManager.current
    val askToolbar = remember(clipboard) {
        AskTextToolbar(
            clipboard = clipboard,
            onAsk = { selectedText -> chatSelection = selectedText },
            onAddNote = { selectedText -> pendingNoteQuote = selectedText },
        )
    }
    // startBlockIndex = -1 is the "open at last block" sentinel — used when
    // flipping left off the first page of a chapter into the previous
    // chapter's tail. Resolved once blocks are parsed (LaunchedEffect below).
    var anchorBlockIndex by remember(chapterSlug) { mutableStateOf(startBlockIndex.coerceAtLeast(0)) }
    var compileState by remember(chapterSlug) { mutableStateOf<CompileState>(CompileState.Idle) }

    LaunchedEffect(courseSlug, chapterSlug) {
        course = store.load(courseSlug)
        markdown = store.readChapter(courseSlug, chapterSlug)
        strokes = store.readStrokes(courseSlug, chapterSlug)
        // Record where we are so a cold start of the app resumes here.
        store.writeLastRead(courseSlug, chapterSlug)
    }

    val chapter = course?.chapters?.firstOrNull { it.slug == chapterSlug }
    val chapterAnnotations = course?.annotations?.filter { it.chapterSlug == chapterSlug }.orEmpty()
    val blocks = remember(markdown, chapter, course) {
        val parsed = markdown?.let { parseMarkdown(it) } ?: emptyList()
        if (chapter == null || parsed.isEmpty()) parsed
        else {
            // Small italic intro line — shows est-minutes + day range + status
            // once at the start of the chapter so the TOC can stay clean.
            val dayLabel = course?.let { c ->
                courseDaySchedule(c).firstOrNull { it.chapterN == chapter.n }?.label
            }
            val metaBody = buildString {
                append("~${chapter.estMinutes} min")
                if (!dayLabel.isNullOrEmpty()) append(" · $dayLabel")
                append(" · ${statusLabel(chapter.status)}")
            }
            val meta = Block.CustomFence(
                kind = "meta",
                params = emptyMap(),
                body = metaBody,
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
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = horizontalPadding.dp)) {
            if (isPending) {
                val pendingChapterIndex = course?.chapters?.indexOfFirst { it.slug == chapterSlug } ?: -1
                val pendingPrev = course?.chapters?.getOrNull(pendingChapterIndex - 1)
                val pendingNext = course?.chapters?.getOrNull(pendingChapterIndex + 1)
                val pendingDayLabel = course?.let { c ->
                    courseDaySchedule(c).firstOrNull { it.chapterN == chapter!!.n }?.label
                }
                CompilePrompt(
                    chapter = chapter!!,
                    chapterCount = course?.chapters?.size ?: 0,
                    dayLabel = pendingDayLabel,
                    compileState = compileState,
                    onBack = onBack,
                    onOpenPrevChapter = pendingPrev?.let { p -> { onOpenChapter(p.slug, -1) } },
                    onOpenNextChapter = pendingNext?.let { n -> { onOpenChapter(n.slug, 0) } },
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
            // Boox side buttons fire PageUp / PageDown. Request focus so the
            // Reader receives key events without the user having to tap first.
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(chapterSlug) { focusRequester.requestFocus() }

            // Header at ReaderScreen scope so it can read/toggle bookmarks
            // via the hoisted headerFirstBlock / headerBookmarked state.
            ReaderHeader(
                chapterN = chapter?.n,
                chapterCount = course?.chapters?.size,
                isBookmarked = headerBookmarked,
                canBookmark = headerFirstBlock >= 0 && course != null,
                onOpenContents = onOpenContents,
                onBookmarkToggle = {
                    val c = course ?: return@ReaderHeader
                    if (headerFirstBlock < 0) return@ReaderHeader
                    val updated = if (headerBookmarked) {
                        store.removeBookmark(c.slug, chapterSlug, headerFirstBlock)
                    } else {
                        store.addBookmark(c.slug, chapterSlug, headerFirstBlock, LocalDate.now().toString())
                    }
                    course = updated ?: course
                },
            )

            CompositionLocalProvider(LocalTextToolbar provides askToolbar) {
                PagedContent(
                    blocks = blocks,
                    annotations = chapterAnnotations,
                    course = course,
                    chapterSlug = chapterSlug,
                    fontScale = fontScale,
                    lineSpacing = lineSpacing,
                    textAlign = textAlign,
                    anchorBlockIndex = anchorBlockIndex,
                    focusRequester = focusRequester,
                    strokes = strokes,
                    fingerDraws = fingerDraws,
                    onStrokeAdded = { added ->
                        val updated = strokes + added
                        strokes = updated
                        store.saveStrokes(courseSlug, chapterSlug, updated)
                    },
                    onStrokeErased = { gone ->
                        val updated = strokes - gone
                        strokes = updated
                        store.saveStrokes(courseSlug, chapterSlug, updated)
                    },
                    onStrokeTapped = { s -> viewingStroke = s },
                    onStrokeGesture = { g -> pendingGestureStroke = g },
                    onFirstBlockChange = { headerFirstBlock = it },
                    onAskAboutBlock = { text -> chatSelection = text },
                    onOpenAnnotation = { a -> viewingAnnotation = a },
                    onOpenPrevChapter = prevChapter?.let { p -> { onOpenChapter(p.slug, -1) } },
                    onOpenNextChapter = nextChapter?.let { n -> { onOpenChapter(n.slug, 0) } },
                    menuOpen = showFormatMenu,
                    onAnchorChange = { anchorBlockIndex = it },
                    onToggleMenu = { showFormatMenu = !showFormatMenu },
                )
            }
        }

        // Floating selection chip — appears when there's an active text
        // selection in the reader. Two book-native actions on the same
        // passage: ask the AI about it, or annotate it with a note. Lives in
        // the outer Box (which supports Alignment.BottomCenter) rather than
        // inside the Column-based content.
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
                TextButton(onClick = { askToolbar.addNote() }) {
                    Text("Add note", color = PaperColor)
                }
                TextButton(onClick = { askToolbar.hide() }) {
                    Text("Cancel", color = PaperColor)
                }
            }
        }

        // Tap-on-drawing chip — mirrors the text-selection chip but with
        // "Delete" for the drawing itself. Context text = plain text of the
        // stroke's anchor block, so Ask/Add-note behave the same way they
        // would if the user had highlighted that block by hand.
        // Fresh-enclosure gesture chip — auto-opens on a closed stroke that
        // covers text. Semantics differ from viewingStroke: Ask removes the
        // stroke on chat close, Add note keeps it, Cancel removes it.
        pendingGestureStroke?.let { stroke ->
            val text = stroke.annotatedText.orEmpty()
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .background(InkColor, RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                TextButton(onClick = {
                    chatOriginStroke = stroke
                    chatSelection = text
                    pendingGestureStroke = null
                }) {
                    Text("Ask about this", color = PaperColor)
                }
                TextButton(onClick = {
                    pendingNoteQuote = text
                    pendingGestureStroke = null
                }) {
                    Text("Add note", color = PaperColor)
                }
                TextButton(onClick = {
                    val updated = strokes - stroke
                    strokes = updated
                    store.saveStrokes(courseSlug, chapterSlug, updated)
                    pendingGestureStroke = null
                }) {
                    Text("Cancel", color = PaperColor)
                }
            }
        }

        viewingStroke?.let { stroke ->
            val anchorBlock = blocks.getOrNull(stroke.blockIndex)
            val fallback = anchorBlock?.let { extractAskableText(it) }?.takeIf { it.isNotBlank() }
            // Prefer the text the stroke actually sits over (captured at
            // draw time) so Ask/Add-note reference the exact phrase the user
            // pointed at, not the whole paragraph.
            val blockText = stroke.annotatedText?.takeIf { it.isNotBlank() } ?: fallback
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .background(InkColor, RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                if (blockText != null) {
                    TextButton(onClick = {
                        chatSelection = blockText
                        viewingStroke = null
                    }) {
                        Text("Ask about this", color = PaperColor)
                    }
                    TextButton(onClick = {
                        pendingNoteQuote = blockText
                        viewingStroke = null
                    }) {
                        Text("Add note", color = PaperColor)
                    }
                }
                TextButton(onClick = {
                    val updated = strokes - stroke
                    strokes = updated
                    store.saveStrokes(courseSlug, chapterSlug, updated)
                    viewingStroke = null
                }) {
                    Text("Delete", color = PaperColor)
                }
                TextButton(onClick = { viewingStroke = null }) {
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
                onClose = {
                    chatSelection = null
                    // If this chat was launched from an enclosure gesture,
                    // the stroke served as a selection cue — remove it now
                    // so the page doesn't accumulate pen marks after Q&A.
                    chatOriginStroke?.let { origin ->
                        val updated = strokes - origin
                        strokes = updated
                        store.saveStrokes(courseSlug, chapterSlug, updated)
                        chatOriginStroke = null
                    }
                },
            )
        }

        // Add-note overlay — small modal with the quoted passage + a note
        // field. Anchors the annotation to the block whose plain text
        // actually contains the quote (found via substring match), falling
        // back to the current page's first block if no match — that way
        // navigation from the TOC lands on the right block, and the inline
        // underline renderer finds the same block for the highlight.
        pendingNoteQuote?.let { quote ->
            AddNoteOverlay(
                quote = quote,
                onCancel = { pendingNoteQuote = null },
                onSave = { noteText ->
                    val c = course ?: return@AddNoteOverlay
                    val trimmedQuote = quote.trim()
                    val matchedBlock = findBlockContainingQuote(blocks, trimmedQuote)
                    val anchor = matchedBlock ?: headerFirstBlock.coerceAtLeast(0)
                    val updated = store.addAnnotation(
                        courseSlug = c.slug,
                        chapterSlug = chapterSlug,
                        blockIndex = anchor,
                        quote = trimmedQuote,
                        note = noteText.trim(),
                        created = LocalDate.now().toString(),
                    )
                    if (updated != null) course = updated
                    pendingNoteQuote = null
                },
            )
        }

        // Note-view overlay — tap an inline ✎ on the page to open. Shows the
        // note text with a Delete button.
        viewingAnnotation?.let { annotation ->
            NoteViewOverlay(
                annotation = annotation,
                onClose = { viewingAnnotation = null },
                onDelete = {
                    val c = course ?: return@NoteViewOverlay
                    val updated = store.removeAnnotation(
                        courseSlug = c.slug,
                        chapterSlug = annotation.chapterSlug,
                        quote = annotation.quote,
                        created = annotation.created,
                    )
                    if (updated != null) course = updated
                    viewingAnnotation = null
                },
            )
        }

        if (showFormatMenu) {
            FormatMenu(
                fontScale = fontScale,
                lineSpacing = lineSpacing,
                textAlign = textAlign,
                horizontalPadding = horizontalPadding,
                isBookmarked = headerBookmarked,
                canBookmark = headerFirstBlock >= 0 && course != null,
                onOpenContents = {
                    showFormatMenu = false
                    onOpenContents()
                },
                onAskAboutChapter = {
                    showFormatMenu = false
                    chatSelection = ""  // open with no selection = general Q&A
                },
                onToggleBookmark = {
                    showFormatMenu = false
                    val c = course ?: return@FormatMenu
                    if (headerFirstBlock < 0) return@FormatMenu
                    val updated = if (headerBookmarked) {
                        store.removeBookmark(c.slug, chapterSlug, headerFirstBlock)
                    } else {
                        store.addBookmark(c.slug, chapterSlug, headerFirstBlock, LocalDate.now().toString())
                    }
                    course = updated ?: course
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
                onHorizontalPaddingChange = { newPad ->
                    horizontalPadding = newPad
                    readerPrefs.setHorizontalPadding(newPad)
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun PagedContent(
    blocks: List<Block>,
    annotations: List<Annotation>,
    course: Course?,
    chapterSlug: String,
    fontScale: Float,
    lineSpacing: Float,
    textAlign: TextAlignPref,
    anchorBlockIndex: Int,
    menuOpen: Boolean,
    focusRequester: FocusRequester,
    strokes: List<InkStroke>,
    fingerDraws: Boolean,
    onStrokeAdded: (InkStroke) -> Unit,
    onStrokeErased: (InkStroke) -> Unit,
    onStrokeTapped: (InkStroke) -> Unit,
    onStrokeGesture: (InkStroke) -> Unit,
    onAnchorChange: (Int) -> Unit,
    onToggleMenu: () -> Unit,
    onFirstBlockChange: (Int) -> Unit,
    onOpenPrevChapter: (() -> Unit)?,
    onOpenNextChapter: (() -> Unit)?,
    onAskAboutBlock: (String) -> Unit,
    onOpenAnnotation: (Annotation) -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val store = rememberCourseStore()

    val chapterN = course?.chapters?.firstOrNull { it.slug == chapterSlug }?.n

    Column(modifier = Modifier.fillMaxSize()) {
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

            // Block bounds tracked for the ink overlay: each rendered block's
            // top edge in root-layout coords, minus the overlay's own top,
            // gives us the block's top in overlay-local coords (which is what
            // InkOverlay needs to anchor + render strokes per-block). Keyed
            // on chapterSlug so the map is fresh on chapter change.
            val blockTopsInRoot = remember(chapterSlug) { mutableStateMapOf<Int, Float>() }
            var overlayTopInRoot by remember(chapterSlug) { mutableStateOf(0f) }

            // The block index of a page's first block is just the sum of block counts
            // in all prior pages — paginate() preserves order and never splits blocks.
            val firstBlockIndex = pages.take(pageIndex).sumOf { it.size }
            LaunchedEffect(firstBlockIndex) { onFirstBlockChange(firstBlockIndex) }

            // What the ink overlay is allowed to touch on this page: the
            // global block-index range and the subset of persisted strokes
            // whose anchor block falls in it.
            val pageBlockCount = pages.getOrNull(pageIndex)?.size ?: 0
            val pageBlockRange = firstBlockIndex until (firstBlockIndex + pageBlockCount)
            val pageStrokes = strokes.filter { it.blockIndex in pageBlockRange }

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
                        .focusRequester(focusRequester)
                        .focusable()
                        .onKeyEvent { event ->
                            // Boox side buttons fire VolumeUp/VolumeDown by default
                            // (Onyx remaps them from the standard Android Volume codes
                            // for use as page-turn keys). Also accept PageUp/PageDown
                            // and DirectionUp/DirectionDown for external keyboards.
                            // Returning true CONSUMES the event so Android doesn't
                            // also show the system volume popup.
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                Key.VolumeUp, Key.PageUp, Key.DirectionUp -> {
                                    goPrev(pageIndex, pages, onAnchorChange, onOpenPrevChapter)
                                    true
                                }
                                Key.VolumeDown, Key.PageDown, Key.DirectionDown -> {
                                    goNext(pageIndex, pages, onAnchorChange, onOpenNextChapter)
                                    true
                                }
                                else -> false
                            }
                        }
                        .pointerInput(pageIndex, pages.size, menuOpen) {
                            val dragThresholdPx = 60.dp.toPx()
                            val vDragThresholdPx = 80.dp.toPx()  // slightly higher — vertical drags happen more accidentally
                            val dragStartPx = 10.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downPos = down.position
                                var totalDx = 0f
                                var totalDy = 0f
                                var isDrag = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) {
                                        // If a child composable (TextButton, SelectionContainer,
                                        // etc.) already consumed this tap, don't page-turn on top
                                        // of it. That was the "Show answer button also flipped a
                                        // page" bug.
                                        if (change.isConsumed) break
                                        if (menuOpen) {
                                            onToggleMenu()
                                        } else if (isDrag) {
                                            // Which axis dominates decides the meaning.
                                            if (kotlin.math.abs(totalDx) >= kotlin.math.abs(totalDy)) {
                                                if (totalDx <= -dragThresholdPx) goNext(pageIndex, pages, onAnchorChange, onOpenNextChapter)
                                                else if (totalDx >= dragThresholdPx) goPrev(pageIndex, pages, onAnchorChange, onOpenPrevChapter)
                                            } else {
                                                // Vertical: swipe UP → next chapter, swipe DOWN → previous chapter.
                                                if (totalDy <= -vDragThresholdPx) onOpenNextChapter?.invoke()
                                                else if (totalDy >= vDragThresholdPx) onOpenPrevChapter?.invoke()
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
                                    val pc = change.positionChange()
                                    totalDx += pc.x
                                    totalDy += pc.y
                                    if (!isDrag && (kotlin.math.abs(totalDx) > dragStartPx || kotlin.math.abs(totalDy) > dragStartPx)) {
                                        isDrag = true
                                    }
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
                                val blockAnnotations = annotationsForBlock(annotations, block, globalIdx)
                                // Wrapping Box reports each block's top-Y in
                                // root coords so InkOverlay can convert to its
                                // own local space (via the diff with its own
                                // positionInRoot). Adds no visual layout — Box
                                // sizes to child. See docs comment on
                                // blockTopsInRoot above.
                                Box(
                                    modifier = Modifier.onGloballyPositioned { coords ->
                                        blockTopsInRoot[globalIdx] = coords.positionInRoot().y
                                    },
                                ) {
                                    RenderBlock(
                                        block = block,
                                        fontScale = fontScale,
                                        lineSpacing = lineSpacing,
                                        textAlign = textAlign,
                                        annotations = blockAnnotations,
                                        revealed = revealed[globalIdx] == true,
                                        onReveal = { revealed[globalIdx] = true },
                                        onOpenAnnotation = onOpenAnnotation,
                                    )
                                }
                            }
                        }
                    }

                    // Freehand-ink layer sits on top of the text, transparent
                    // until a stroke exists. Consumes stylus input (and finger
                    // if the dev toggle is on) so the parent's page-turn
                    // gesture skips those events via change.isConsumed.
                    // Reverted from OnyxInkOverlay pending a proper Onyx slice.
                    // Onyx gave zero-latency draw but broke tap-select, erase,
                    // and periodically fired page-turn on stroke end + left
                    // phantom EPD-drawn strokes. Swap back to OnyxInkOverlay
                    // when we can iterate on those with logcat + a test rig.
                    InkOverlay(
                        pageStrokes = pageStrokes,
                        blockTopY = { idx -> blockTopsInRoot[idx]?.minus(overlayTopInRoot) },
                        pageBlockRange = pageBlockRange,
                        allowFingerDraw = fingerDraws,
                        onStrokeAdded = { raw ->
                            // Enrich with the text the stroke actually sits
                            // over, so Ask/Add-note from a stroke-tap gets
                            // the specific text the user marked instead of
                            // the whole paragraph. Locked at draw time so a
                            // later font-size change doesn't drift it.
                            val anchor = blocks.getOrNull(raw.blockIndex)
                            val enriched = if (anchor != null) {
                                val text = extractStrokeText(
                                    raw, anchor, widthPx, measurer, density, fontScale, lineSpacing,
                                )
                                if (!text.isNullOrBlank()) raw.copy(annotatedText = text) else raw
                            } else raw
                            // Save first so the stroke renders while the
                            // gesture chip is up. If the user later cancels
                            // or asks the AI, the caller removes it then.
                            onStrokeAdded(enriched)
                            if (isEnclosingStroke(enriched) &&
                                !enriched.annotatedText.isNullOrBlank()) {
                                onStrokeGesture(enriched)
                            }
                        },
                        onStrokeErased = onStrokeErased,
                        onStrokeTapped = onStrokeTapped,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            overlayTopInRoot = coords.positionInRoot().y
                        },
                    )

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
    onOpenContents: () -> Unit,
    onBookmarkToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
    ) {
        // Explicit-destination nav rather than popBackStack — the auto-resume
        // cold-start pushes Reader directly on top of Library with no
        // Contents in between, so a back-pop would land on Library. The
        // button label says Contents; it should always go to Contents.
        TextButton(onClick = onOpenContents) { Text("≡ Contents", color = InkColor) }
        Spacer(Modifier.weight(1f))
        if (chapterN != null && chapterCount != null) {
            Text(
                text = "Ch $chapterN/$chapterCount",
                style = MaterialTheme.typography.bodySmall.copy(color = MutedColor),
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        // Bookmark ribbon: appears in the header only when this page is
        // bookmarked. Tap to remove. Setting a bookmark happens via the
        // reader menu (center tap → "Bookmark this page"). This keeps the
        // header quiet when no bookmark is set — the "appear-only-when-on"
        // pattern we agreed on earlier.
        if (isBookmarked) {
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
                    drawPath(path, color = InkColor)
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
    horizontalPadding: Float,
    isBookmarked: Boolean,
    canBookmark: Boolean,
    onOpenContents: () -> Unit,
    onAskAboutChapter: () -> Unit,
    onToggleBookmark: () -> Unit,
    onFontSmaller: () -> Unit,
    onFontLarger: () -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onTextAlignChange: (TextAlignPref) -> Unit,
    onHorizontalPaddingChange: (Float) -> Unit,
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

        // Bookmark toggle for the current page.
        MenuRow(label = "Mark") {
            Box(
                modifier = Modifier
                    .clickable(enabled = canBookmark) { onToggleBookmark() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (isBookmarked) "Remove bookmark" else "Bookmark this page",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canBookmark) InkColor else MutedColor,
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

        // Margin — symmetric side padding. "Wide" doubles as marginalia
        // space for pen notes; "Narrow" maximizes text density. Reflows the
        // whole chapter on change (paginator reads content width).
        MenuRow(label = "Margin") {
            ReaderPrefs.HORIZONTAL_PADDINGS.forEachIndexed { i, v ->
                SelectableChip(
                    label = ReaderPrefs.HORIZONTAL_PADDING_LABELS[i],
                    selected = kotlin.math.abs(horizontalPadding - v) < 0.5f,
                    onClick = { onHorizontalPaddingChange(v) },
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
    annotations: List<Annotation>,
    revealed: Boolean,
    onReveal: () -> Unit,
    onOpenAnnotation: (Annotation) -> Unit,
) {
    when (block) {
        is Block.Heading -> HeadingBlock(block, fontScale)
        is Block.Paragraph -> ParagraphBlock(
            block.inlines, fontScale, lineSpacing, textAlign,
            annotations, onOpenAnnotation,
        )
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
    annotations: List<Annotation>,
    onOpenAnnotation: (Annotation) -> Unit,
) {
    val textAnnotations = annotations.filter { it.kind == "text" }
    val annotated = if (textAnnotations.isEmpty()) {
        renderInlines(inlines)
    } else {
        renderInlinesWithUnderlines(inlines, textAnnotations.map { it.quote })
    }
    // Precompute character ranges of each underlined quote so a tap inside
    // one opens the note directly, matching how physical marginalia work
    // (the mark IS the affordance). Falls through to the parent page-turn
    // handler when the tap misses every range — we only .consume() on a hit.
    val plainText = annotated.text
    val annotationRanges = remember(plainText, textAnnotations) {
        textAnnotations.mapNotNull { a ->
            val needle = firstLineOf(a.quote.trim())
            if (needle.isBlank()) return@mapNotNull null
            val start = plainText.indexOf(needle)
            if (start < 0) null else (start until start + needle.length) to a
        }
    }
    var textLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val hitSlop = with(LocalDensity.current) { 8.dp.toPx() }
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            text = annotated,
            onTextLayout = { textLayout = it },
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp * fontScale,
                lineHeight = 30.sp * fontScale * lineSpacing,
                color = InkColor,
                textAlign = if (textAlign == TextAlignPref.JUSTIFY) TextAlign.Justify else TextAlign.Start,
                hyphens = Hyphens.Auto,
            ),
            modifier = Modifier
                .weight(1f)
                .pointerInput(annotationRanges) {
                    if (annotationRanges.isEmpty()) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var moved = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: break
                            val pc = ch.positionChange()
                            if (kotlin.math.abs(pc.x) > hitSlop || kotlin.math.abs(pc.y) > hitSlop) moved = true
                            if (!ch.pressed) {
                                if (!moved) {
                                    val layout = textLayout
                                    if (layout != null) {
                                        val pos = layout.getOffsetForPosition(down.position)
                                        val hit = annotationRanges.firstOrNull { pos in it.first }
                                        if (hit != null) {
                                            ch.consume()
                                            onOpenAnnotation(hit.second)
                                        }
                                    }
                                }
                                break
                            }
                        }
                    }
                },
        )
        // Note markers to the right of the paragraph — one small ✎ per
        // annotation, tappable to open the note. Sits outside the Text so
        // clicks aren't fighting SelectionContainer.
        if (annotations.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 6.dp, top = 2.dp)) {
                annotations.forEach { a ->
                    Text(
                        text = "✎",
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp * fontScale,
                        color = InkColor,
                        modifier = Modifier
                            .clickable { onOpenAnnotation(a) }
                            .padding(vertical = 2.dp, horizontal = 4.dp),
                    )
                }
            }
        }
    }
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

/**
 * Same as [renderInlines] but also adds an underline SpanStyle over any
 * substring matching a quote. Whitespace is normalized before the match,
 * so line wraps or double spaces at annotation-time don't defeat the lookup.
 * Multi-line quotes: we match the first-line prefix as the underline range
 * so at least the head of the annotated passage gets marked; the intent is
 * a visual cue, not a legal citation.
 */
private fun renderInlinesWithUnderlines(
    inlines: List<Inline>,
    quotes: List<String>,
): AnnotatedString {
    // Track plain text alongside the styled builder so we can look up quote
    // offsets — AnnotatedString.Builder has no text accessor mid-build, and
    // its length is character-count which equals the plain-text length
    // because SpanStyle transitions don't insert characters.
    val plain = StringBuilder()
    return buildAnnotatedString {
        inlines.forEach { i ->
            when (i) {
                is Inline.Text -> {
                    append(i.text); plain.append(i.text)
                }
                is Inline.Bold -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(i.text) }
                    plain.append(i.text)
                }
                is Inline.Italic -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(i.text) }
                    plain.append(i.text)
                }
                is Inline.Code -> {
                    withStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x14000000)),
                    ) { append(i.text) }
                    plain.append(i.text)
                }
            }
        }
        val flat = plain.toString()
        quotes.forEach { q ->
            val needle = firstLineOf(q.trim())
            if (needle.isBlank()) return@forEach
            val idx = flat.indexOf(needle)
            if (idx >= 0) {
                addStyle(
                    SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                    idx,
                    idx + needle.length,
                )
            }
        }
    }
}

private fun firstLineOf(s: String): String = s.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()

/**
 * Which of [annotations] belong to the block at [globalIdx]. An annotation
 * "belongs" only if its stored blockIndex matches this block AND the quote
 * substring is still findable in the block's plain text (defensive against
 * post-hoc chapter edits). This keeps single-word notes like "kitchen"
 * highlighted on the passage they were made on, not on every block that
 * happens to mention the word.
 */
private fun annotationsForBlock(
    annotations: List<Annotation>,
    block: Block,
    globalIdx: Int,
): List<Annotation> {
    if (annotations.isEmpty()) return emptyList()
    val plain = extractAskableText(block) ?: return emptyList()
    return annotations.filter { a ->
        if (a.blockIndex != globalIdx) return@filter false
        val needle = firstLineOf(a.quote.trim())
        needle.isNotBlank() && plain.contains(needle)
    }
}

/**
 * Heuristic: was this stroke drawn as an enclosure (roughly closed curve
 * around some text)? Used to route fresh strokes to the auto-popup path
 * instead of leaving them as silent marginalia. Three checks:
 *   1) enough sampled points to constitute a curve (not a jitter),
 *   2) a substantial bbox (rules out tiny loops from scribbling),
 *   3) start and end points close together relative to bbox size.
 * Non-enclosing strokes (underlines, checkmarks, free scribbles) stay as
 * marginalia — caller only auto-opens the chip when this returns true.
 */
private fun isEnclosingStroke(stroke: InkStroke): Boolean {
    val points = stroke.points
    if (points.size < 8) return false
    var minX = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for (p in points) {
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
    }
    val w = maxX - minX
    val h = maxY - minY
    val area = w * h
    if (area < ENCLOSURE_MIN_AREA_PX) return false
    val diag = kotlin.math.sqrt((w * w + h * h).toDouble()).toFloat()
    val first = points.first()
    val last = points.last()
    val gap = kotlin.math.sqrt(
        (((first.x - last.x) * (first.x - last.x)) +
         ((first.y - last.y) * (first.y - last.y))).toDouble(),
    ).toFloat()
    return gap < diag * ENCLOSURE_MAX_GAP_RATIO
}

private const val ENCLOSURE_MIN_AREA_PX = 400f
private const val ENCLOSURE_MAX_GAP_RATIO = 0.25f

/**
 * Given a freshly-drawn [stroke] anchored to [block], measure the block's
 * text layout with the current reader styles and return the substring of
 * the block's plain text that the stroke sits over.
 *
 * How: build a paragraph-style TextLayout with the same width/font/spacing
 * the renderer uses, then for each character offset check whether that
 * character's bbox overlaps the stroke's bbox (with a half-line vertical
 * slop so an underline drawn just below the baseline still catches the
 * chars above it). Concatenate the hit range → underlying text.
 *
 * Returns null for non-text blocks (figures/exercises/fences) or when the
 * stroke lives outside the text (margin scribble). Non-null result is
 * stored in Stroke.annotatedText so a later font-scale change doesn't
 * shift what "the marked text" refers to.
 */
private fun extractStrokeText(
    stroke: InkStroke,
    block: Block,
    widthPx: Int,
    measurer: TextMeasurer,
    density: Density,
    fontScale: Float,
    lineSpacing: Float,
): String? {
    val plain = when (block) {
        is Block.Paragraph -> block.inlines.plainText()
        is Block.BulletItem -> block.inlines.plainText()
        is Block.OrderedItem -> block.inlines.plainText()
        is Block.Heading -> block.inlines.plainText()
        is Block.CustomFence -> when (block.kind) {
            "recap", "exercise" -> block.body.trim()
            else -> return null
        }
    }
    if (plain.isBlank() || stroke.points.isEmpty() || widthPx <= 0) return null

    // Style approximates what ParagraphBlock uses. Slight metric drift is
    // OK — we're locating chars, not pixel-aligning glyphs.
    val style = with(density) {
        TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 18.sp * fontScale,
            lineHeight = 30.sp * fontScale * lineSpacing,
            hyphens = Hyphens.Auto,
        )
    }
    val layout = measurer.measure(
        text = AnnotatedString(plain),
        style = style,
        constraints = Constraints(maxWidth = widthPx),
    )

    var minX = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    stroke.points.forEach { p ->
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
    }

    val lineHeightPx = if (layout.lineCount > 0) {
        layout.getLineBottom(0) - layout.getLineTop(0)
    } else 0f
    // Half-line vertical slop lets an underline drawn just below the
    // baseline still associate with the chars above it (common pattern).
    val vSlop = lineHeightPx * 0.5f

    var firstHit = -1
    var lastHit = -1
    for (offset in plain.indices) {
        val box = layout.getBoundingBox(offset)
        val yOverlap = box.bottom >= minY - vSlop && box.top <= maxY + vSlop
        val xOverlap = box.right >= minX && box.left <= maxX
        if (yOverlap && xOverlap) {
            if (firstHit < 0) firstHit = offset
            lastHit = offset
        }
    }
    if (firstHit < 0) return null
    val end = (lastHit + 1).coerceAtMost(plain.length)
    return plain.substring(firstHit, end).trim().ifBlank { null }
}

/**
 * Walk [blocks] and return the index of the first whose plain text contains
 * [quote]. Used at annotation-creation time so the stored blockIndex points
 * at the passage's actual block rather than the page's top block. Null if
 * no block matches (e.g., a multi-block selection).
 */
private fun findBlockContainingQuote(blocks: List<Block>, quote: String): Int? {
    val needle = firstLineOf(quote.trim())
    if (needle.isBlank()) return null
    return blocks.indexOfFirst { extractAskableText(it)?.contains(needle) == true }
        .takeIf { it >= 0 }
}

@Composable
private fun CompilePrompt(
    chapter: PersistedChapter,
    chapterCount: Int,
    dayLabel: String?,
    compileState: CompileState,
    onBack: () -> Unit,
    onCompile: () -> Unit,
    onOpenPrevChapter: (() -> Unit)? = null,
    onOpenNextChapter: (() -> Unit)? = null,
) {
    // Swipe support on the compile prompt: horizontal or vertical swipe still
    // lets you nav to adjacent chapters. Without this, hitting a pending
    // chapter via vertical-swipe-forward is a dead end until the user compiles.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onOpenPrevChapter, onOpenNextChapter) {
                val hDrag = 60.dp.toPx()
                val vDrag = 80.dp.toPx()
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var dx = 0f
                    var dy = 0f
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) {
                            if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) {
                                if (dx <= -hDrag) onOpenNextChapter?.invoke()
                                else if (dx >= hDrag) onOpenPrevChapter?.invoke()
                            } else {
                                if (dy <= -vDrag) onOpenNextChapter?.invoke()
                                else if (dy >= vDrag) onOpenPrevChapter?.invoke()
                            }
                            break
                        }
                        val p = ch.positionChange()
                        dx += p.x
                        dy += p.y
                    }
                }
            },
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
        text = buildString {
            append("~${chapter.estMinutes} min")
            if (!dayLabel.isNullOrEmpty()) append(" · $dayLabel")
            append(" · not compiled")
        },
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
    }  // close Column
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            // User navigated away or the screen recomposed; don't paint this
            // as an error — just let the coroutine unwind.
            throw e
        } catch (e: Exception) {
            System.err.println("chapter compile failed: ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            onState(CompileState.Failed(geminiErrorLabel(e)))
        }
    }
}
