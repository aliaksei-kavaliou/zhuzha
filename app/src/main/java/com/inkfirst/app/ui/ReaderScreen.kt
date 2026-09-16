package com.inkfirst.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.layout.ContentScale
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
import com.inkfirst.core.BADGE_KINDS
import com.inkfirst.core.badgeKindFor
import com.inkfirst.core.badgeKindsIn
import com.inkfirst.core.BookBadger
import com.inkfirst.core.ChapterIllustrator
import com.inkfirst.core.GeneratedImage
import com.inkfirst.core.ChapterGenerator
import com.inkfirst.core.ChapterStatus
import com.inkfirst.core.Annotation
import com.inkfirst.core.Course
import com.inkfirst.core.FIGURE_ASPECT
import com.inkfirst.core.Inline
import com.inkfirst.core.GeminiClient
import com.inkfirst.core.KtorGeminiClient
import com.inkfirst.core.MemoryUpdater
import com.inkfirst.core.PersistedChapter
import com.inkfirst.core.Stroke as InkStroke
import com.inkfirst.core.courseDaySchedule
import com.inkfirst.core.findFigureSlots
import com.inkfirst.core.parseInlines
import com.inkfirst.core.parseMarkdown
import kotlinx.coroutines.launch
import java.time.LocalDate

// E-ink can only render pure black + pure white directly; anything between
// gets dithered (visible dot pattern). Our warm off-white paper looked nice on
// screens but rendered as noise on the Boox. Pure black on pure white is the
// canonical e-ink palette.
/**
 * Which pen path the reader draws with.
 *
 * true  — Onyx's TouchHelper, which paints straight to the EPD controller.
 *         Near-zero latency, and the reason to keep trying: "lag is boring".
 * false — the plain MotionEvent + Compose Canvas path. Higher latency, but
 *         every gesture around the stroke (tap-on-stroke chip, barrel erase,
 *         enclosure popup) is known to work.
 *
 * This is a device-only question — the emulator has no stylus and no EPD —
 * so it is a constant to flip and reinstall, not a user setting.
 */
private const val USE_ONYX_PEN = true

/**
 * Blank space above the first block, so the first line can be stroked from
 * above the way every other line can.
 */
private val INK_TOP_MARGIN = 28.dp

/**
 * Depth of the menu band across the top of the page.
 *
 * The menu used to own the whole centre third — the rarest action in a
 * reading session (a few taps) holding a third of the screen, exactly where a
 * thumb rests on a 7" device held one-handed, and on e-ink an accidental menu
 * costs a full refresh and your place in the sentence. Zones are sized by
 * frequency now: next page hundreds of times, previous tens, menu a handful.
 *
 * A top band is what Kindle and Kobo both use, and for the same reasons: it
 * is not where a hand rests while reading, it adjoins the header chrome so
 * "the top is controls" is already half-learned, and reaching it is a
 * deliberate movement rather than an accident.
 */
private val MENU_BAND_HEIGHT = 120.dp

/**
 * Width of a chapter cover or closing picture, as a fraction of the text
 * column.
 *
 * Full width was too tall: at 4:3 it took ~890px of a ~1300px page, so a
 * three-line chapter title plus its cover could not fit together on one page
 * at all — the opener degenerated into a byline alone with the title and the
 * picture pushed to page 2. A plate that stops short of the text edges also
 * simply looks better on a book page.
 */
internal const val PLATE_WIDTH_FRACTION = 0.72f

/**
 * Where a tap lands. One rule, used by both tap detectors — the reader's own
 * and the Onyx overlay's, which had to take over page taps when the SDK
 * started owning the host view's touch stream.
 */
internal enum class TapZone { MENU, PREV, NEXT }

internal fun tapZoneFor(x: Float, y: Float, width: Float, menuBandPx: Float): TapZone = when {
    y < menuBandPx -> TapZone.MENU
    // Left third goes back; everything else goes forward. The old centre
    // third is given to NEXT because that is what a centre tap usually meant
    // — someone moving on, not aiming carefully.
    x < width / 3f -> TapZone.PREV
    else -> TapZone.NEXT
}

private val InkColor = Color(0xFF000000)
private val PaperColor = Color(0xFFFFFFFF)
private val MutedColor = Color(0xFF555555)
private val BorderColor = Color(0xFF888888)

// Inline `code` is marked by the monospace face alone — no background. A tint
// behind the words was tried in both a dithered and a flat form and read as
// ugly either way on the panel; against a serif body, the switch to mono is
// already an unmistakable signal, and it keeps the page pure black on white.

private sealed interface CompileState {
    data object Idle : CompileState
    data object Running : CompileState
    /** Words are written and saved; pictures are still being drawn. */
    data class Illustrating(val done: Int, val total: Int) : CompileState
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
    // Figure slots still waiting for a picture. Drives the menu's "Draw
    // figures" row — the retrofit path for chapters written before figures
    // existed, and the retry path when one came back empty.
    val pendingFigures = remember(markdown) {
        markdown?.let { md -> findFigureSlots(md).count { it.isPending } } ?: 0
    }
    // Progress (or the failure) of a manual illustrate pass, shown in the
    // menu row itself. With the chapter already on screen there is no
    // compile prompt to report into, and drawing takes long enough that
    // silence reads as "nothing happened".
    // Badges are course-level, so unlike figures this counts once for the
    // whole book. badgeVersion exists to invalidate the decoded-badge cache
    // and the page pack after a draw — a badge makes a block label taller,
    // so pagination has to be recomputed, not just repainted.
    var badgeVersion by remember { mutableStateOf(0) }
    // What this chapter actually needs — a chapter with code blocks earns a
    // code badge the first time it is opened, so the set grows with the book
    // instead of being fixed up front.
    val neededBadges = remember(markdown) {
        markdown?.let { badgeKindsIn(it) }.orEmpty().ifEmpty { BADGE_KINDS }
    }
    val hasCover = remember(course?.slug, chapterSlug, badgeVersion) {
        course?.slug?.let { store.coverFile(it, chapterSlug) != null } == true
    }
    val hasTail = remember(course?.slug, chapterSlug, badgeVersion) {
        course?.slug?.let { store.tailFile(it, chapterSlug) != null } == true
    }
    val pendingBadges = remember(course?.slug, badgeVersion, neededBadges) {
        val have = course?.slug?.let { store.badgeKinds(it) }.orEmpty()
        neededBadges.count { it !in have }
    }
    val figureStatus = when (val state = compileState) {
        is CompileState.Illustrating -> "Drawing… ${state.done}/${state.total}"
        is CompileState.Failed -> state.message
        else -> null
    }
    val blocks = remember(markdown, chapter, course) {
        val parsed = markdown?.let { parseMarkdown(it) } ?: emptyList()
        if (chapter == null || parsed.isEmpty()) parsed
        else {
            // Small italic intro line — shows est-minutes + day range + status
            // once at the start of the chapter so the TOC can stay clean.
            val dayLabel = course?.let { c ->
                courseDaySchedule(c).firstOrNull { it.chapterN == chapter.n }?.label
            }
            // "Chapter N" leads the byline so every chapter is numbered the
            // same way, whatever the model wrote. Some chapters opened with
            // "# Chapter 5: …" and others with a bare title, because the
            // number only ever existed when the model happened to type it.
            val metaBody = buildString {
                append("Chapter ${chapter.n}")
                append(" · ~${chapter.estMinutes} min")
                if (!dayLabel.isNullOrEmpty()) append(" · $dayLabel")
                append(" · ${statusLabel(chapter.status)}")
            }
            // The chapter model usually opens with "# Chapter N: Title", but
            // not always — two of six chapters in the first real course came
            // back with no heading at all, and the reader showed a chapter
            // with no title. The title is known from the outline, so stop
            // depending on the model remembering.
            //
            // It rides in the meta block rather than being prepended as its
            // own Heading on purpose: annotations and ink are anchored by
            // block index, and inserting a block would slide every anchor in
            // exactly the chapters that lack a title.
            val opensWithTitle = (parsed.firstOrNull() as? Block.Heading)?.level == 1
            val coverName = course?.slug?.let { slug ->
                store.coverFile(slug, chapter.slug)?.name
            }
            val metaParams = buildMap {
                if (!opensWithTitle) put("title", chapter.title)
                // The cover belongs AFTER the title, always. When the model
                // wrote its own h1 the cover used to sit in the meta block
                // above it, so the picture came before the title in some
                // chapters and after it in others. It rides with whichever
                // block actually shows the title.
                if (coverName != null && !opensWithTitle) put("cover", coverName)
            }
            val meta = Block.CustomFence(kind = "meta", params = metaParams, body = metaBody)
            val rest = if (coverName != null && opensWithTitle) {
                // Attach the cover to the model's own h1 by tagging that
                // heading — no block is added or removed, so every annotation
                // and ink anchor keeps its index.
                parsed.mapIndexed { i, b ->
                    if (i == 0 && b is Block.Heading) b.copy(coverSrc = coverName) else b
                }
            } else {
                parsed
            }
            // The closing picture rides in a synthetic block appended at the
            // END. Unlike the title, this one can safely be a block of its
            // own: adding to the tail shifts no existing index, so no
            // annotation or ink anchor moves.
            val tailName = course?.slug?.let { slug ->
                store.tailFile(slug, chapter.slug)?.name
            }
            val tail = tailName?.let {
                Block.CustomFence(kind = "tail", params = mapOf("src" to it), body = "")
            }
            listOf(meta) + rest + listOfNotNull(tail)
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
                    badgeVersion = badgeVersion,
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
                    onStrokeErased = { hit ->
                        // An eraser contact always dismisses any open chip
                        // (so an eraser tap in empty space closes it) AND
                        // removes the stroke it hit. These used to be
                        // either/or, so erasing a stroke you had just tapped
                        // — chip up — only closed the chip and the stroke
                        // stayed, which read as "erase does nothing".
                        pendingGestureStroke = null
                        viewingStroke = null
                        if (hit != null) {
                            val updated = strokes - hit
                            strokes = updated
                            store.saveStrokes(courseSlug, chapterSlug, updated)
                        }
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
        // Fresh-enclosure gesture chip. Same 4 buttons as the tap chip so
        // muscle memory transfers. One semantic difference: Ask here also
        // sets chatOriginStroke, so closing the chat removes the stroke —
        // the enclosure was a selection cue, not a persistent mark. Cancel
        // just hides the chip; the stroke stays as marginalia (Delete is
        // the explicit "remove" action).
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
                    Text("Delete", color = PaperColor)
                }
                TextButton(onClick = { pendingGestureStroke = null }) {
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
                pendingFigures = pendingFigures,
                figureStatus = figureStatus,
                busyDrawing = compileState is CompileState.Illustrating,
                pendingBadges = pendingBadges,
                hasCover = hasCover,
                hasTail = hasTail,
                onDrawTail = {
                    val c = course ?: return@FormatMenu
                    val ch = chapter ?: return@FormatMenu
                    drawChapterTail(
                        course = c,
                        chapter = ch,
                        scope = scope,
                        store = store,
                        apiKeyStore = apiKeyStore,
                        onState = { compileState = it },
                        onDone = { badgeVersion++ },
                    )
                },
                onDrawCover = {
                    val c = course ?: return@FormatMenu
                    val ch = chapter ?: return@FormatMenu
                    drawChapterCover(
                        course = c,
                        chapter = ch,
                        scope = scope,
                        store = store,
                        apiKeyStore = apiKeyStore,
                        onState = { compileState = it },
                        onDone = { badgeVersion++ },
                    )
                },
                onDrawBadges = {
                    val c = course ?: return@FormatMenu
                    drawCourseBadges(
                        course = c,
                        kinds = neededBadges,
                        scope = scope,
                        store = store,
                        apiKeyStore = apiKeyStore,
                        onState = { compileState = it },
                        onDone = { badgeVersion++ },
                    )
                },
                onIllustrate = {
                    // Menu deliberately stays open — it is where progress shows.
                    val c = course ?: return@FormatMenu
                    val ch = chapter ?: return@FormatMenu
                    val md = markdown ?: return@FormatMenu
                    illustrateExistingChapter(
                        course = c,
                        chapter = ch,
                        markdown = md,
                        scope = scope,
                        store = store,
                        apiKeyStore = apiKeyStore,
                        onState = { compileState = it },
                        onDone = { newMarkdown -> markdown = newMarkdown },
                    )
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
    /** Bumped when badges are drawn, to rebuild the badge cache and the page pack. */
    badgeVersion: Int,
    fontScale: Float,
    lineSpacing: Float,
    textAlign: TextAlignPref,
    anchorBlockIndex: Int,
    menuOpen: Boolean,
    focusRequester: FocusRequester,
    strokes: List<InkStroke>,
    fingerDraws: Boolean,
    onStrokeAdded: (InkStroke) -> Unit,
    onStrokeErased: (InkStroke?) -> Unit,
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
    val prefsContext = LocalContext.current
    val readerPrefs = remember { ReaderPrefs(prefsContext) }

    // The tap hint retires itself once the gesture is learned. Counted here
    // rather than inside goNext/goPrev because pageIndex moves for taps,
    // swipes and the Boox side buttons alike, and all three are page turns.
    var showTapHint by remember {
        mutableStateOf(readerPrefs.getPageTurns() < ReaderPrefs.HINT_RETIRES_AFTER)
    }
    var lastCountedPage by remember { mutableStateOf(-1) }

    val chapterN = course?.chapters?.firstOrNull { it.slug == chapterSlug }?.n

    // Decoded figures, kept for as long as the chapter is open. Without a
    // cache every page turn re-decodes the pictures on the new page, which
    // is real work on the Boox. Keyed on the chapter so leaving it frees the
    // bitmaps. Not observable state on purpose: entries are filled during
    // composition and never change for a given src.
    val figureBitmaps = remember(course?.slug, chapterSlug) { mutableMapOf<String, ImageBitmap?>() }
    val loadFigure: (String) -> ImageBitmap? = { src ->
        figureBitmaps.getOrPut(src) {
            val slug = course?.slug
            val file = if (slug == null) null else store.figureFile(slug, src)
            file?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
        }
    }
    // Cheap existence check for the paginator — a stat, not a decode.
    val hasFigureImage: (String) -> Boolean = { src ->
        course?.slug?.let { store.figureFile(it, src) != null } == true
    }

    // Badges are course-level, not chapter-level: the same two pictures are
    // reused by every exercise and recap in the whole book, so the cache is
    // keyed on the course and survives chapter changes. Keyed by block kind
    // because nothing in the Markdown names the file.
    val badgeBitmaps = remember(course?.slug, badgeVersion) { mutableMapOf<String, ImageBitmap?>() }
    val loadBadge: (String) -> ImageBitmap? = { kind ->
        badgeBitmaps.getOrPut(kind) {
            val slug = course?.slug
            val file = if (slug == null) null else store.badgeFile(slug, kind)
            file?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
        }
    }
    val hasBadge: (String) -> Boolean = { kind ->
        course?.slug?.let { store.badgeFile(it, kind) != null } == true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val widthPx = with(density) { maxWidth.toPx().toInt() }
            val availableHeightPx = with(density) { (maxHeight - 40.dp).toPx().toInt() }

            val pages = remember(blocks, widthPx, availableHeightPx, fontScale, lineSpacing, badgeVersion) {
                paginate(
                    blocks = blocks,
                    contentWidthPx = widthPx,
                    contentHeightPx = availableHeightPx,
                    density = density,
                    measurer = measurer,
                    fontScale = fontScale,
                    lineSpacing = lineSpacing,
                    hasFigureImage = hasFigureImage,
                    hasBadge = hasBadge,
                    inkTopMarginPx = with(density) { INK_TOP_MARGIN.toPx().toInt() },
                )
            }
            // Page index is view state; the block anchor is for persistence
            // and for jumps from outside (TOC, bookmark, cold-start resume).
            //
            // It used to be derived from the anchor on every recomposition,
            // which stopped working the moment paragraphs could split: a split
            // block appears on two pages, so "which page holds block 1" has
            // two answers, and turning the page set an anchor that resolved
            // straight back to the page you left.
            var pageIndex by remember(pages) {
                mutableStateOf(findPageContainingBlock(pages, blocks, anchorBlockIndex).coerceAtLeast(0))
            }
            // Resync only for anchors that aren't already on this page — that
            // is what distinguishes an external jump from the echo of our own
            // page turn.
            LaunchedEffect(anchorBlockIndex, pages) {
                val onThisPage = pages.getOrNull(pageIndex)
                    ?.any { it.index == anchorBlockIndex } == true
                if (!onThisPage) {
                    pageIndex = findPageContainingBlock(pages, blocks, anchorBlockIndex)
                        .coerceAtLeast(0)
                }
            }
            LaunchedEffect(pageIndex) {
                // Opening a chapter lands on a page without turning to it, so
                // the first observed index is seeded rather than counted.
                if (lastCountedPage >= 0 && lastCountedPage != pageIndex) {
                    readerPrefs.notePageTurn()
                    if (readerPrefs.getPageTurns() >= ReaderPrefs.HINT_RETIRES_AFTER) {
                        showTapHint = false
                    }
                }
                lastCountedPage = pageIndex
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
            // Read off the page itself now: a split paragraph appears on two
            // pages, so summing earlier page sizes would double-count it.
            val firstBlockIndex = pages.getOrNull(pageIndex)?.firstOrNull()?.index ?: 0
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
                                    goPrev(pageIndex, pages, { pageIndex = it }, onAnchorChange, onOpenPrevChapter)
                                    true
                                }
                                Key.VolumeDown, Key.PageDown, Key.DirectionDown -> {
                                    goNext(pageIndex, pages, { pageIndex = it }, onAnchorChange, onOpenNextChapter)
                                    true
                                }
                                else -> false
                            }
                        }
                        .pointerInput(pageIndex, pages.size, menuOpen) {
                            val dragThresholdPx = 60.dp.toPx()
                            val vDragThresholdPx = 80.dp.toPx()  // slightly higher — vertical drags happen more accidentally
                            val dragStartPx = 10.dp.toPx()
                            val menuBandPx = MENU_BAND_HEIGHT.toPx()
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
                                                if (totalDx <= -dragThresholdPx) goNext(pageIndex, pages, { pageIndex = it }, onAnchorChange, onOpenNextChapter)
                                                else if (totalDx >= dragThresholdPx) goPrev(pageIndex, pages, { pageIndex = it }, onAnchorChange, onOpenPrevChapter)
                                            } else {
                                                // Vertical: swipe UP → next chapter, swipe DOWN → previous chapter.
                                                if (totalDy <= -vDragThresholdPx) onOpenNextChapter?.invoke()
                                                else if (totalDy >= vDragThresholdPx) onOpenPrevChapter?.invoke()
                                            }
                                        } else {
                                            // Plain tap.
                                            when (
                                                tapZoneFor(
                                                    downPos.x,
                                                    downPos.y,
                                                    size.width.toFloat(),
                                                    menuBandPx,
                                                )
                                            ) {
                                                TapZone.MENU -> onToggleMenu()
                                                TapZone.PREV ->
                                                    goPrev(pageIndex, pages, { pageIndex = it }, onAnchorChange, onOpenPrevChapter)
                                                TapZone.NEXT ->
                                                    goNext(pageIndex, pages, { pageIndex = it }, onAnchorChange, onOpenNextChapter)
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
                            // Drawable margin above the first block. Marking
                            // the first line means stroking slightly ABOVE it —
                            // an underline sits below its baseline, a circle
                            // encloses from above — and with the text flush to
                            // the overlay's top edge there was nowhere to put
                            // the pen. Costs one line of page height and makes
                            // the first line behave like every other one.
                            Spacer(Modifier.height(INK_TOP_MARGIN))
                            val pageStart = firstBlockIndex  // computed above, arithmetic
                            pages.getOrNull(pageIndex)?.forEachIndexed { i, item ->
                                val block = item.block
                                val globalIdx = item.index
                                val blockAnnotations = annotationsForBlock(annotations, block, globalIdx)
                                // Wrapper reports each block's top-Y in root
                                // coords so InkOverlay can convert to its own
                                // local space (via the diff with its own
                                // positionInRoot). See docs comment on
                                // blockTopsInRoot above.
                                //
                                // MUST be a Column, not a Box. Several block
                                // composables emit more than one child —
                                // HeadingBlock is Spacer/Text/Spacer, and
                                // BorderedBlock is Spacer/Column/Spacer — and
                                // a Box overlays its children instead of
                                // stacking them, so every one of those spacers
                                // was drawn on top of the content and added no
                                // space at all. That is why a heading sat 10px
                                // from its own paragraph while ordinary body
                                // lines sat 31px apart: the heading's spacers
                                // were silently doing nothing.
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned { coords ->
                                            // The VIRTUAL top: where this block
                                            // would have started had it not been
                                            // split. Stroke offsets are stored
                                            // relative to the block, not to the
                                            // page, so a continuation has to
                                            // report the original origin — ink
                                            // on lines shown earlier then clips
                                            // off the top of the page, which is
                                            // correct. Keeps the on-disk format
                                            // unchanged across splitting.
                                            blockTopsInRoot[globalIdx] =
                                                coords.positionInRoot().y - item.skippedPx
                                        },
                                ) {
                                    RenderBlock(
                                        block = block,
                                        atPageTop = i == 0,
                                        fontScale = fontScale,
                                        lineSpacing = lineSpacing,
                                        textAlign = textAlign,
                                        annotations = blockAnnotations,
                                        revealed = revealed[globalIdx] == true,
                                        onReveal = { revealed[globalIdx] = true },
                                        onOpenAnnotation = onOpenAnnotation,
                                        loadFigure = loadFigure,
                                        loadBadge = loadBadge,
                                    )
                                }
                            }
                        }
                    }

                    // Freehand-ink layer sits on top of the text, transparent
                    // until a stroke exists. Consumes stylus input (and finger
                    // if the dev toggle is on) so the parent's page-turn
                    // gesture skips those events via change.isConsumed.
                    // Reverted to InkOverlay after 2nd Onyx attempt: Onyx's
                    // raw-input mode captures ALL touches on the SurfaceView
                    // area at a level below Compose, so the finger tap-on-
                    // stroke / chip / erase-button flow can't observe events.
                    // Also hit a limit-rect coord-system quirk. Fixing needs
                    // dedicated device iteration — see task 10.
                    // Attempt 3 at the Onyx path. Both earlier tries failed
                    // the same way — raw-drawing mode captures every touch on
                    // the SurfaceView below Compose, so finger tap-on-stroke,
                    // the chip and barrel-erase never saw an event — and the
                    // v3 seed sets FEATURE_APP_PEN_TOUCH_RENDER to try to let
                    // finger events through. Flip USE_ONYX_PEN back to false
                    // to return to the MotionEvent path, which works but lags.
                    val onStrokeAddedEnriched: (InkStroke) -> Unit = { raw ->
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
                            // Log the decision, not just the outcome: the
                            // gesture needs BOTH a closed shape and text under
                            // it, and "no chip appeared" gives no hint which
                            // of the two failed.
                            val enclosing = isEnclosingStroke(enriched)
                            android.util.Log.d(
                                "InkGesture",
                                "stroke pts=${enriched.points.size} enclosing=$enclosing " +
                                    "text=${enriched.annotatedText?.take(40)?.ifBlank { "<blank>" } ?: "<null>"}",
                            )
                            if (enclosing && !enriched.annotatedText.isNullOrBlank()) {
                                // No explicit unlock needed here, unlike the
                                // tap chip: this fires at the end of a stroke,
                                // and the stroke's own scheduleUnlock("draw")
                                // repaints the panel a moment later.
                                onStrokeGesture(enriched)
                            }
                    }
                    val overlayModifier = Modifier.onGloballyPositioned { coords ->
                        overlayTopInRoot = coords.positionInRoot().y
                    }
                    if (USE_ONYX_PEN) {
                        OnyxInkOverlay(
                            pageStrokes = pageStrokes,
                            blockTopY = { idx -> blockTopsInRoot[idx]?.minus(overlayTopInRoot) },
                            pageBlockRange = pageBlockRange,
                            onStrokeAdded = onStrokeAddedEnriched,
                            onStrokeErased = onStrokeErased,
                            onStrokeTapped = onStrokeTapped,
                            // The Onyx overlay reports page taps itself: with
                            // raw drawing live the SDK owns the host view's
                            // touch stream and the release never reaches the
                            // gesture detector below, so tap-to-turn silently
                            // stopped working. Same thirds as the reader.
                            onPageTap = { x, y, w ->
                                when (
                                    tapZoneFor(x, y, w, with(density) { MENU_BAND_HEIGHT.toPx() })
                                ) {
                                    TapZone.MENU -> onToggleMenu()
                                    TapZone.PREV ->
                                        goPrev(pageIndex, pages, { pageIndex = it }, onAnchorChange, onOpenPrevChapter)
                                    TapZone.NEXT ->
                                        goNext(pageIndex, pages, { pageIndex = it }, onAnchorChange, onOpenNextChapter)
                                }
                            },
                            modifier = overlayModifier,
                        )
                    } else {
                        InkOverlay(
                            pageStrokes = pageStrokes,
                            blockTopY = { idx -> blockTopsInRoot[idx]?.minus(overlayTopInRoot) },
                            pageBlockRange = pageBlockRange,
                            allowFingerDraw = fingerDraws,
                            onStrokeAdded = onStrokeAddedEnriched,
                            onStrokeErased = onStrokeErased,
                            onStrokeTapped = onStrokeTapped,
                            modifier = overlayModifier,
                        )
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
                    // Instructional chrome, so it is temporary by design: once
                    // the tap zones are learned the hint is just noise on every
                    // page of the book. Retires itself after a dozen turns.
                    if (showTapHint) {
                        Text(
                            // Describes the zones as they now are: the menu
                            // moved from the centre third to a band across the
                            // top, and the centre turns forward like the rest
                            // of the page. A hint that teaches the old layout
                            // is worse than none.
                            text = "tap ← / → · top for menu ",
                            style = MaterialTheme.typography.bodySmall.copy(color = MutedColor),
                            textAlign = TextAlign.End,
                        )
                    }
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
        // The gap below is deliberately wide: a running head needs clear air
        // under it or it reads as part of the first block on the page. 20dp
        // was not enough — when a bordered RECAP box leads the page its top
        // border is a hard horizontal line, and the header still read as
        // attached to it. Costs a line of body text per page and is worth it.
        // The content box below measures whatever is left, so pagination
        // follows this automatically.
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 36.dp),
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
    pendingFigures: Int,
    pendingBadges: Int,
    hasCover: Boolean,
    hasTail: Boolean,
    figureStatus: String?,
    busyDrawing: Boolean,
    onIllustrate: () -> Unit,
    onDrawBadges: () -> Unit,
    onDrawCover: () -> Unit,
    onDrawTail: () -> Unit,
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

        // Draw the chapter's outstanding figures. Hidden once every slot
        // has a picture, so a fully illustrated chapter shows no dead row.
        if (pendingFigures > 0 || figureStatus != null) {
            MenuRow(label = "Figures") {
                Box(
                    modifier = Modifier
                        .clickable(enabled = !busyDrawing && pendingFigures > 0) { onIllustrate() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = figureStatus
                            ?: ("Draw $pendingFigures figure" + if (pendingFigures == 1) "" else "s"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (busyDrawing) MutedColor else InkColor,
                    )
                }
            }
        }

        // The chapter's opening picture. One per chapter, so the row goes
        // away for good once it exists.
        if (!hasCover) {
            MenuRow(label = "Chapter") {
                Box(
                    modifier = Modifier
                        .clickable(enabled = !busyDrawing) { onDrawCover() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Draw chapter picture",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (busyDrawing) MutedColor else InkColor,
                    )
                }
            }
        }

        // The closing picture. Only offered once a cover exists, since it is
        // that scene resolved rather than a picture in its own right.
        if (hasCover && !hasTail) {
            MenuRow(label = "Chapter") {
                Box(
                    modifier = Modifier
                        .clickable(enabled = !busyDrawing) { onDrawTail() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Draw closing picture",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (busyDrawing) MutedColor else InkColor,
                    )
                }
            }
        }

        // Draw the book's mascot badges — the small marks on exercise and
        // recap blocks. Two pictures for the whole course, so this row
        // disappears for good once they exist.
        if (pendingBadges > 0) {
            MenuRow(label = "Badges") {
                Box(
                    modifier = Modifier
                        .clickable(enabled = !busyDrawing) { onDrawBadges() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Draw $pendingBadges book badge" + if (pendingBadges == 1) "" else "s",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (busyDrawing) MutedColor else InkColor,
                    )
                }
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
    atPageTop: Boolean,
    fontScale: Float,
    lineSpacing: Float,
    textAlign: TextAlignPref,
    annotations: List<Annotation>,
    revealed: Boolean,
    onReveal: () -> Unit,
    onOpenAnnotation: (Annotation) -> Unit,
    loadFigure: (String) -> ImageBitmap?,
    loadBadge: (String) -> ImageBitmap?,
) {
    when (block) {
        is Block.Heading -> HeadingBlock(block, fontScale, atPageTop, loadFigure)
        is Block.Paragraph -> ParagraphBlock(
            block.inlines, fontScale, lineSpacing, textAlign,
            annotations, onOpenAnnotation,
        )
        is Block.BulletItem -> ListItemBlock("• ", block.inlines, fontScale, lineSpacing)
        is Block.OrderedItem -> ListItemBlock("${block.n}. ", block.inlines, fontScale, lineSpacing)
        is Block.Rule -> RuleBlock()
        is Block.CustomFence -> when (block.kind) {
            "exercise" -> ExerciseBlock(block, fontScale, lineSpacing, revealed, onReveal, loadBadge)
            "recap" -> RecapBlock(block, fontScale, lineSpacing, loadBadge)
            "figure" -> FigureBlock(block, fontScale, lineSpacing, loadFigure)
            "meta" -> MetaBlock(block, fontScale, loadFigure)
            "tail" -> TailBlock(block, loadFigure)
            else -> UnknownFenceBlock(block, fontScale, loadBadge)
        }
    }
}

@Composable
private fun HeadingBlock(
    h: Block.Heading,
    fontScale: Float,
    atPageTop: Boolean = false,
    loadFigure: (String) -> ImageBitmap? = { null },
) {
    // Space below a heading used to be 10dp for an h2 — 19px against a 56px
    // body leading, so the gap under a heading was SMALLER than the gap
    // between two ordinary lines, and the heading read as falling into the
    // paragraph rather than introducing it. Below is now roughly half a line
    // and above is nearly twice that, which is the relationship a heading
    // needs: clearly attached to what follows, clearly separated from what
    // came before. Keep the paginator's copy of these in step.
    val (baseSize, top, bottom) = when (h.level) {
        1 -> Triple(30.sp, 40.dp, 28.dp)
        2 -> Triple(24.sp, 32.dp, 26.dp)
        3 -> Triple(20.sp, 26.dp, 20.dp)
        else -> Triple(18.sp, 22.dp, 16.dp)
    }
    val fontSize = baseSize * fontScale
    // Space above a heading separates it from what came before. At the top of
    // a page there is nothing before it — the running head already supplies
    // the air — and the spacer just pushes the page down.
    if (!atPageTop) Spacer(Modifier.height(top))
    Text(
        // "Chapter 5: " is stripped from the model's own heading because the
        // byline above already numbers every chapter. Leaving it produced
        // "Chapter 5 · ~60 min" directly above "Chapter 5: Stepping Down…".
        text = renderInlines(h.inlines).let {
            if (h.level == 1) AnnotatedString(stripChapterPrefix(it.text)) else it
        },
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        lineHeight = fontSize * 1.2f,
        color = InkColor,
    )
    Spacer(Modifier.height(bottom))
    // The chapter's opening picture, when this heading is the chapter title.
    h.coverSrc?.let(loadFigure)?.let { cover ->
        // Centred like a plate in a printed book — a narrowed full-width
        // element, not a figure aligned to the text column.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                bitmap = cover,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(PLATE_WIDTH_FRACTION).aspectRatio(FIGURE_ASPECT),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Drops a leading "Chapter 5:", "Chapter 5 —" or "Chapter 5." from a title.
 * The model writes the number roughly half the time; the reader numbers every
 * chapter itself, so the model's copy is redundant when present.
 */
private fun stripChapterPrefix(title: String): String =
    CHAPTER_PREFIX.replace(title, "").trim().ifBlank { title }

private val CHAPTER_PREFIX = Regex("""^\s*chapter\s+\d+\s*[:.\u2014\u2013-]\s*""", RegexOption.IGNORE_CASE)

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
    // 12dp was 0.4 of a line: two paragraphs ran together into one slab and
    // there was no way to see where one ended. 20dp reads as a break without
    // resorting to a first-line indent, which would need special-casing for
    // the paragraph after every figure and bordered box.
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
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
    // 8dp is less than one character width — the bullets shared a left edge
    // with the body and the list didn't read as subordinate. One em at the
    // body size is ~18dp. Items stay tighter to each other than paragraphs
    // are, which is right within a list; the air around the list as a whole
    // comes from the paragraphs on either side.
    Row(modifier = Modifier.padding(start = 18.dp, bottom = 10.dp)) {
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
    loadBadge: (String) -> ImageBitmap?,
) {
    val (question, answer) = splitExerciseBody(f.body)
    BorderedBlock(label = "TRY THIS", fontScale = fontScale, badge = loadBadge("exercise")) {
        Text(
            // Parsed, not raw: an exercise says "call `torch.tensor()`" as
            // often as a paragraph does, and rendering the body as a plain
            // string dropped every inline style inside a bordered block.
            text = renderInlines(parseInlines(question)),
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp * fontScale,
            lineHeight = 28.sp * fontScale * lineSpacing,
            color = InkColor,
        )
        Spacer(Modifier.height(8.dp))
        if (revealed || answer.isEmpty()) {
            if (answer.isNotEmpty()) {
                // The mascot again, in its telling-you-a-secret pose. Top
                // aligned rather than centred: the answer usually runs to
                // several lines and a centred badge would float oddly.
                Row {
                    val answerBadge = loadBadge("answer")
                    if (answerBadge != null) {
                        Image(
                            bitmap = answerBadge,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(BADGE_SIZE * fontScale),
                            colorFilter = badgeColorFilter(),
                        )
                    }
                    Text(
                        text = renderInlines(parseInlines("Answer: $answer")),
                        fontFamily = FontFamily.Serif,
                        fontSize = 16.sp * fontScale,
                        lineHeight = 26.sp * fontScale * lineSpacing,
                        fontStyle = FontStyle.Italic,
                        color = InkColor,
                    )
                }
            }
        } else {
            TextButton(onClick = onReveal) { Text("Show answer", color = InkColor) }
        }
    }
}

@Composable
private fun RecapBlock(
    f: Block.CustomFence,
    fontScale: Float,
    lineSpacing: Float,
    loadBadge: (String) -> ImageBitmap?,
) {
    BorderedBlock(label = "RECAP", fontScale = fontScale, badge = loadBadge("recap")) {
        Text(
            text = renderInlines(parseInlines(f.body.trim())),
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp * fontScale,
            lineHeight = 28.sp * fontScale * lineSpacing,
            color = InkColor,
        )
    }
}

/**
 * A figure slot. Once ChapterIllustrator has drawn it, the picture stands on
 * its own in the text flow — no border, no "FIGURE" label, no caption. Book
 * illustrations aren't boxed, and the alt text is a drawing brief ("a very
 * tired robot sorting socks"), which reads oddly as a caption; it stays as
 * the content description instead.
 *
 * Until then it falls back to the bordered alt-text box, which is what every
 * figure looked like before pictures existed.
 */
@Composable
private fun FigureBlock(
    f: Block.CustomFence,
    fontScale: Float,
    lineSpacing: Float,
    loadFigure: (String) -> ImageBitmap?,
) {
    val alt = f.params["alt"] ?: f.body.trim().ifBlank { "(figure)" }
    val picture = f.params["src"]?.let(loadFigure)

    if (picture != null) {
        Image(
            bitmap = picture,
            contentDescription = alt,
            contentScale = ContentScale.Fit,
            // Force greyscale. Unlike our UI chrome — which stays pure black
            // and white to dodge dither patterns — a sketch is *meant* to
            // dither, but a stray colour cast from the model would muddy it.
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            // padding BEFORE aspectRatio: the ratio then governs the picture
            // itself and the 16dp is breathing room around it, so total height
            // is (width / ratio) + 32dp — exactly what the paginator reserves.
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .aspectRatio(FIGURE_ASPECT),
        )
        return
    }

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
private fun MetaBlock(
    f: Block.CustomFence,
    fontScale: Float,
    loadFigure: (String) -> ImageBitmap?,
) {
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
    // Stand-in title for a chapter whose Markdown never wrote one. Styled to
    // match a real h1 so the two cases are indistinguishable on the page.
    f.params["title"]?.let { title ->
        Text(
            text = title,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp * fontScale,
            lineHeight = 30.sp * fontScale * 1.2f,
            color = InkColor,
        )
        Spacer(Modifier.height(16.dp))
    }
    // The chapter's opening picture: the problem the chapter is about to
    // solve, standing on the title page the way a chapter plate does in a
    // printed book. Borderless and uncaptioned, like every other figure.
    f.params["cover"]?.let(loadFigure)?.let { cover ->
        // Centred like a plate in a printed book — a narrowed full-width
        // element, not a figure aligned to the text column.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                bitmap = cover,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(PLATE_WIDTH_FRACTION).aspectRatio(FIGURE_ASPECT),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun UnknownFenceBlock(
    f: Block.CustomFence,
    fontScale: Float,
    loadBadge: (String) -> ImageBitmap?,
) {
    // A code fence carries its language as the kind (```python), so the badge
    // is looked up by the normalised kind — every language shares one "code"
    // mascot rather than each earning its own.
    BorderedBlock(
        label = f.kind.uppercase().ifBlank { "CODE" },
        fontScale = fontScale,
        badge = badgeKindFor(f.kind)?.let(loadBadge),
    ) {
        Text(
            text = f.body,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp * fontScale,
            color = InkColor,
        )
    }
}

@Composable
private fun BorderedBlock(
    label: String,
    fontScale: Float,
    badge: ImageBitmap? = null,
    content: @Composable () -> Unit,
) {
    Spacer(Modifier.height(4.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            // The course mascot, drawn once and reused by every block of this
            // kind in the book. Absent until BookBadger has run — and a course
            // with no badges renders exactly as it did before they existed.
            if (badge != null) {
                Image(
                    bitmap = badge,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(BADGE_SIZE * fontScale),
                    colorFilter = badgeColorFilter(),
                )
            }
            Text(
                text = label,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp * fontScale,
                color = MutedColor,
            )
        }
        content()
    }
    Spacer(Modifier.height(4.dp))
}

/**
 * Size of a badge beside a block label. Walked up from 34dp — the source is
 * 1024px square, so at 34dp it was an eight-times downscale and the pose,
 * which is the entire point of the drawing, was gone. Scales with the
 * reader's font size so a page at A++ doesn't end up with a tiny mark
 * against huge type.
 */
internal val BADGE_SIZE = 72.dp

/**
 * Greyscale plus a hard contrast push, applied to every badge.
 *
 * Two problems, one filter. The panel is grey, so a colour cast from the
 * model has to go. And the model sometimes returns its doodle on textured or
 * off-white "paper" — harmless in a full-width figure, but at badge size it
 * reads as a grey square stamped on the page.
 *
 * The push is deliberately gentle. A hard one (2.6) was tried and wrecked the
 * drawings: shrinking 1024px line art to badge size averages each stroke with
 * the white around it, so the strokes themselves land in the light-grey range
 * the filter was meant to erase, and the badge came out spindly and washed
 * out. 1.5 clears anything above ~0.83 — which covers JPEG "paper" — while
 * leaving a thinned stroke visible. The real fix for the ground is the prompt;
 * this only catches what slips through.
 */
private fun badgeColorFilter(): ColorFilter {
    val contrast = 1.5f
    val offset = (0.5f - 0.5f * contrast) * 255f
    val matrix = ColorMatrix().apply { setToSaturation(0f) }
    matrix *= ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, offset,
            0f, contrast, 0f, 0f, offset,
            0f, 0f, contrast, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    return ColorFilter.colorMatrix(matrix)
}

/**
 * The chapter's closing picture — the cover's payoff, shown after the last
 * block. Borderless and uncaptioned like every other picture in the book;
 * arriving at it is what tells you the chapter is over.
 */
@Composable
private fun TailBlock(f: Block.CustomFence, loadFigure: (String) -> ImageBitmap?) {
    val picture = f.params["src"]?.let(loadFigure) ?: return
    Spacer(Modifier.height(24.dp))
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(
            bitmap = picture,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(PLATE_WIDTH_FRACTION).aspectRatio(FIGURE_ASPECT),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
        )
    }
}

/**
 * A thematic break (`---`). Drawn as whitespace and nothing else.
 *
 * A visible line was tried first and looked like a stray underline. The
 * reason is that in practice every one of these is immediately followed by a
 * heading — the chapter model emits the pair as one gesture — so the line was
 * drawing a separator directly above something that already separates. What
 * the page actually wants there is a beat of silence.
 *
 * The block is kept rather than dropped at parse time on purpose: blocks are
 * addressed by index, and annotations and ink strokes are anchored to those
 * indices. Removing one would slide every anchor after it in the chapter.
 */
@Composable
private fun RuleBlock() {
    Spacer(Modifier.height(10.dp))
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
    pages: List<List<PageItem>>,
    setPage: (Int) -> Unit,
    onAnchorChange: (Int) -> Unit,
    onOpenPrevChapter: (() -> Unit)?,
) {
    if (pageIndex > 0) {
        setPage(pageIndex - 1)
        onAnchorChange(pages.getOrNull(pageIndex - 1)?.firstOrNull()?.index ?: 0)
    } else {
        onOpenPrevChapter?.invoke()
    }
}

private fun goNext(
    pageIndex: Int,
    pages: List<List<PageItem>>,
    setPage: (Int) -> Unit,
    onAnchorChange: (Int) -> Unit,
    onOpenNextChapter: (() -> Unit)?,
) {
    if (pageIndex < pages.size - 1) {
        setPage(pageIndex + 1)
        onAnchorChange(pages.getOrNull(pageIndex + 1)?.firstOrNull()?.index ?: 0)
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
                SpanStyle(fontFamily = FontFamily.Monospace),
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
                        SpanStyle(fontFamily = FontFamily.Monospace),
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
// Gap = distance between the stroke's first and last points, as a
// fraction of the bbox diagonal. A tight 0.25 threshold rejected typical
// hand-drawn circles (users routinely close the loop within 30–50% of the
// diameter, not 25%). 0.5 = at least half-closed, which still filters out
// underlines / straight lines (gap ≈ diagonal → ratio ≈ 1.0).
private const val ENCLOSURE_MAX_GAP_RATIO = 0.5f

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
        is Block.Rule -> return null
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

    // For each line, include it only if its Y range overlaps the stroke's
    // Y range by at least 30% of the smaller of the two. A pure slop check
    // (previous version) grabbed adjacent lines any time the stroke got
    // within a fraction of a line-height of the boundary — a single-line
    // circle around "It is simply a" pulled chunks from the line above and
    // below into the returned text. This ratio test keeps a thin underline
    // (small stroke fully inside a line → ratio ≈ 1) while cutting off
    // neighbours (touch of 1px vs 30px line → ratio ≈ 3%).
    val strokeHeight = maxY - minY

    val segments = mutableListOf<String>()
    for (line in 0 until layout.lineCount) {
        val lineTop = layout.getLineTop(line)
        val lineBottom = layout.getLineBottom(line)
        val overlap = kotlin.math.max(
            0f,
            kotlin.math.min(lineBottom, maxY) - kotlin.math.max(lineTop, minY),
        )
        val lineHeight = lineBottom - lineTop
        val minDim = kotlin.math.min(strokeHeight, lineHeight)
        if (minDim <= 0f || overlap < minDim * 0.3f) continue

        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line)
        var firstHit = -1
        var lastHit = -1
        for (offset in lineStart until lineEnd) {
            val box = layout.getBoundingBox(offset)
            if (box.right >= minX && box.left <= maxX) {
                if (firstHit < 0) firstHit = offset
                lastHit = offset
            }
        }
        if (firstHit >= 0) {
            val endExcl = (lastHit + 1).coerceAtMost(plain.length)
            val seg = plain.substring(firstHit, endExcl).trim()
            if (seg.isNotEmpty()) segments += seg
        }
    }
    if (segments.isEmpty()) return null
    return segments.joinToString(" ").ifBlank { null }
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
    // The side buttons have to be handled here too, not just in the reader.
    // On Boox they arrive as VolumeUp/VolumeDown, so a screen that does not
    // consume them lets Android show the system volume popup — pressing a
    // page-turn button on a not-yet-compiled chapter changed the volume.
    // They are consumed unconditionally, including at the first and last
    // chapter where there is nowhere to go: silently doing nothing is the
    // correct answer for a page-turn key, and the volume popup is not.
    val keyFocus = remember { FocusRequester() }
    LaunchedEffect(chapter.slug) { keyFocus.requestFocus() }

    // Swipe support on the compile prompt: horizontal or vertical swipe still
    // lets you nav to adjacent chapters. Without this, hitting a pending
    // chapter via vertical-swipe-forward is a dead end until the user compiles.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(keyFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.VolumeUp, Key.PageUp, Key.DirectionUp -> {
                        onOpenPrevChapter?.invoke()
                        true
                    }
                    Key.VolumeDown, Key.PageDown, Key.DirectionDown -> {
                        onOpenNextChapter?.invoke()
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(onOpenPrevChapter, onOpenNextChapter) {
                val hDrag = 60.dp.toPx()
                val vDrag = 80.dp.toPx()
                val dragStartPx = 10.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    var dx = 0f
                    var dy = 0f
                    var isDrag = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) {
                            // A child (Compile / Back / Try again) already took
                            // this tap — don't navigate out from under it.
                            if (ch.isConsumed) break
                            if (isDrag) {
                                if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) {
                                    if (dx <= -hDrag) onOpenNextChapter?.invoke()
                                    else if (dx >= hDrag) onOpenPrevChapter?.invoke()
                                } else {
                                    if (dy <= -vDrag) onOpenNextChapter?.invoke()
                                    else if (dy >= vDrag) onOpenPrevChapter?.invoke()
                                }
                            } else {
                                // Same tap zones as the paginated reader. Without
                                // these, a not-yet-compiled chapter is a dead end:
                                // tapping does nothing and the only visible exit
                                // is Back, which leaves the book entirely.
                                when (
                                    tapZoneFor(
                                        downPos.x,
                                        downPos.y,
                                        size.width.toFloat(),
                                        MENU_BAND_HEIGHT.toPx(),
                                    )
                                ) {
                                    // No menu on a prompt, so the band is
                                    // inert here rather than doing something
                                    // different from the reader.
                                    TapZone.MENU -> Unit
                                    TapZone.PREV -> onOpenPrevChapter?.invoke()
                                    TapZone.NEXT -> onOpenNextChapter?.invoke()
                                }
                            }
                            break
                        }
                        val p = ch.positionChange()
                        dx += p.x
                        dy += p.y
                        if (!isDrag && (kotlin.math.abs(dx) > dragStartPx || kotlin.math.abs(dy) > dragStartPx)) {
                            isDrag = true
                        }
                    }
                }
            },
    ) {
    // Book-native "front matter" for a not-yet-compiled chapter: user sees the
    // title, meta, and objectives before spending a Gemini call. Tap Compile
    // to generate; on success the Reader auto-flips to the paginated content.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // The gap below is deliberately wide: a running head needs clear air
        // under it or it reads as part of the first block on the page. 20dp
        // was not enough — when a bordered RECAP box leads the page its top
        // border is a hard horizontal line, and the header still read as
        // attached to it. Costs a line of body text per page and is worth it.
        // The content box below measures whatever is left, so pagination
        // follows this automatically.
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 36.dp),
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
        is CompileState.Illustrating -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).padding(end = 8.dp),
                    color = InkColor,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Drawing figures… ${compileState.done}/${compileState.total}",
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

    // Discoverability: the reader teaches "tap the edges to turn", and this
    // screen honours that, but with no page footer there was nothing saying so.
    if (onOpenPrevChapter != null || onOpenNextChapter != null) {
        Spacer(Modifier.weight(1f))
        Text(
            text = buildString {
                if (onOpenPrevChapter != null) append("← tap for previous chapter")
                if (onOpenPrevChapter != null && onOpenNextChapter != null) append("   ·   ")
                if (onOpenNextChapter != null) append("next chapter →")
            },
            fontFamily = FontFamily.Serif,
            fontSize = 12.sp,
            color = MutedColor,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            textAlign = TextAlign.Center,
        )
    }
    }  // close Column
}

/**
 * Draws the pending figures of one chapter and persists both the pictures
 * and the `src=` params that point at them. Returns the chapter Markdown to
 * display — rewritten if anything was drawn, untouched otherwise.
 *
 * Never throws for a generation failure: figures are decoration, and a
 * figure that didn't come back simply keeps showing its alt text.
 */
private suspend fun illustrateChapter(
    client: GeminiClient,
    imageModel: String,
    course: Course,
    chapter: PersistedChapter,
    markdown: String,
    apiKey: String,
    store: com.inkfirst.core.CourseStore,
    onState: (CompileState) -> Unit,
): String = try {
    val result = ChapterIllustrator(client, imageModel).illustrate(
        course = course,
        chapter = chapter,
        markdown = markdown,
        apiKey = apiKey,
        onProgress = { done, total -> onState(CompileState.Illustrating(done, total)) },
        saveImage = { fileName, bytes -> store.saveFigure(course.slug, fileName, bytes) },
    )
    if (result.generated > 0) {
        store.saveChapter(course.slug, chapter.slug, result.markdown)
    }
    result.markdown
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    System.err.println("chapter illustration failed: ${e::class.simpleName}: ${e.message}")
    markdown
}

/**
 * Draws figures for a chapter whose prose already exists — the retrofit path
 * for courses written before figures were a thing, and the retry path when
 * one picture failed. Idempotent: only slots without a `src=` cost anything.
 */
private fun illustrateExistingChapter(
    course: Course,
    chapter: PersistedChapter,
    markdown: String,
    scope: kotlinx.coroutines.CoroutineScope,
    store: com.inkfirst.core.CourseStore,
    apiKeyStore: ApiKeyStore,
    onState: (CompileState) -> Unit,
    onDone: (String) -> Unit,
) {
    val apiKey = apiKeyStore.getApiKey().orEmpty()
    if (apiKey.isBlank()) {
        onState(CompileState.Failed("No API key set. Go to Settings first."))
        return
    }
    scope.launch {
        try {
            KtorGeminiClient().use { client ->
                val illustrated = illustrateChapter(
                    client = client,
                    imageModel = apiKeyStore.getImageModelName(),
                    course = course,
                    chapter = chapter,
                    markdown = markdown,
                    apiKey = apiKey,
                    store = store,
                    onState = onState,
                )
                onState(CompileState.Idle)
                onDone(illustrated)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            System.err.println("illustrate failed: ${e::class.simpleName}: ${e.message}")
            onState(CompileState.Failed(geminiErrorLabel(e)))
        }
    }
}

/**
 * Draws the course's missing mascot badges. Course-level, not chapter-level:
 * this runs at most once per course, and every chapter thereafter just reads
 * the files. Same best-effort contract as figures — a badge that fails to
 * draw is simply absent, and the block renders as it always did.
 */
private fun drawCourseBadges(
    course: Course,
    kinds: List<String>,
    scope: kotlinx.coroutines.CoroutineScope,
    store: com.inkfirst.core.CourseStore,
    apiKeyStore: ApiKeyStore,
    onState: (CompileState) -> Unit,
    onDone: () -> Unit,
) {
    val apiKey = apiKeyStore.getApiKey().orEmpty()
    if (apiKey.isBlank()) {
        onState(CompileState.Failed("No API key set. Go to Settings first."))
        return
    }
    scope.launch {
        try {
            KtorGeminiClient().use { client ->
                val result = BookBadger(client, apiKeyStore.getImageModelName()).drawMissing(
                    course = course,
                    apiKey = apiKey,
                    kinds = kinds,
                    existing = store.badgeKinds(course.slug),
                    knownMascot = store.readMascot(course.slug),
                    onProgress = { done, total -> onState(CompileState.Illustrating(done, total)) },
                    loadBadge = { kind ->
                        store.badgeFile(course.slug, kind)?.let { f ->
                            GeneratedImage(
                                bytes = f.readBytes(),
                                mimeType = if (f.extension == "jpg") "image/jpeg" else "image/png",
                            )
                        }
                    },
                    saveImage = { kind, ext, bytes -> store.saveBadge(course.slug, kind, ext, bytes) },
                )
                store.saveMascot(course.slug, result.mascot)
                onState(CompileState.Idle)
                onDone()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            System.err.println("badges failed: ${e::class.simpleName}: ${e.message}")
            onState(CompileState.Failed(geminiErrorLabel(e)))
        }
    }
}

/**
 * Draws the chapter's opening picture. Separate from the figure pass because
 * a cover has no fence in the Markdown — nothing to rewrite, nothing to make
 * idempotent beyond "does the file exist".
 */
private fun drawChapterCover(
    course: Course,
    chapter: PersistedChapter,
    scope: kotlinx.coroutines.CoroutineScope,
    store: com.inkfirst.core.CourseStore,
    apiKeyStore: ApiKeyStore,
    onState: (CompileState) -> Unit,
    onDone: () -> Unit,
) {
    val apiKey = apiKeyStore.getApiKey().orEmpty()
    if (apiKey.isBlank()) {
        onState(CompileState.Failed("No API key set. Go to Settings first."))
        return
    }
    scope.launch {
        try {
            onState(CompileState.Illustrating(0, 1))
            KtorGeminiClient().use { client ->
                ChapterIllustrator(client, apiKeyStore.getImageModelName()).drawCover(
                    course = course,
                    chapter = chapter,
                    apiKey = apiKey,
                    textModel = course.model,
                    saveImage = { name, bytes -> store.saveFigure(course.slug, name, bytes) },
                    saveBrief = { brief -> store.saveCoverBrief(course.slug, chapter.slug, brief) },
                )
                onState(CompileState.Idle)
                onDone()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            System.err.println("cover failed: ${e::class.simpleName}: ${e.message}")
            onState(CompileState.Failed(geminiErrorLabel(e)))
        }
    }
}

/**
 * Draws the chapter's closing picture: the cover's payoff.
 *
 * Requires a cover — it is a rewrite of that scene, not a second idea about
 * the chapter — so the cover's brief and the cover PNG both go along, the
 * latter as the image model's reference.
 */
private fun drawChapterTail(
    course: Course,
    chapter: PersistedChapter,
    scope: kotlinx.coroutines.CoroutineScope,
    store: com.inkfirst.core.CourseStore,
    apiKeyStore: ApiKeyStore,
    onState: (CompileState) -> Unit,
    onDone: () -> Unit,
) {
    val apiKey = apiKeyStore.getApiKey().orEmpty()
    if (apiKey.isBlank()) {
        onState(CompileState.Failed("No API key set. Go to Settings first."))
        return
    }
    val coverFile = store.coverFile(course.slug, chapter.slug)
    if (coverFile == null) {
        onState(CompileState.Failed("Draw the chapter picture first."))
        return
    }
    scope.launch {
        try {
            onState(CompileState.Illustrating(0, 1))
            KtorGeminiClient().use { client ->
                ChapterIllustrator(client, apiKeyStore.getImageModelName()).drawTail(
                    course = course,
                    chapter = chapter,
                    apiKey = apiKey,
                    textModel = course.model,
                    coverBrief = store.readCoverBrief(course.slug, chapter.slug),
                    coverImage = GeneratedImage(
                        bytes = coverFile.readBytes(),
                        mimeType = if (coverFile.extension == "jpg") "image/jpeg" else "image/png",
                    ),
                    saveImage = { name, bytes -> store.saveFigure(course.slug, name, bytes) },
                )
                onState(CompileState.Idle)
                onDone()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            System.err.println("tail failed: ${e::class.simpleName}: ${e.message}")
            onState(CompileState.Failed(geminiErrorLabel(e)))
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
                // Pictures last, and best-effort: the chapter is already
                // saved and readable, so a failed or slow illustration pass
                // can never cost the user their generated text.
                val illustrated = illustrateChapter(
                    client = client,
                    imageModel = apiKeyStore.getImageModelName(),
                    course = course,
                    chapter = chapter,
                    markdown = markdown,
                    apiKey = apiKey,
                    store = store,
                    onState = onState,
                )
                // Badges too, if this course has none yet — it is a one-time
                // cost on the first chapter, and then every exercise and
                // recap in the book has its mark. Failures are swallowed
                // inside BookBadger; a course with no badges just renders
                // plain blocks.
                try {
                    BookBadger(client, apiKeyStore.getImageModelName()).drawMissing(
                        course = course,
                        apiKey = apiKey,
                        kinds = badgeKindsIn(illustrated),
                        existing = store.badgeKinds(course.slug),
                        knownMascot = store.readMascot(course.slug),
                        onProgress = { done, total -> onState(CompileState.Illustrating(done, total)) },
                        loadBadge = { kind ->
                            store.badgeFile(course.slug, kind)?.let { f ->
                                GeneratedImage(
                                    bytes = f.readBytes(),
                                    mimeType = if (f.extension == "jpg") "image/jpeg" else "image/png",
                                )
                            }
                        },
                        saveImage = { kind, ext, bytes -> store.saveBadge(course.slug, kind, ext, bytes) },
                    ).also { store.saveMascot(course.slug, it.mascot) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    System.err.println("badge pass failed: ${e::class.simpleName}: ${e.message}")
                }
                onState(CompileState.Idle)
                onDone(updated, illustrated)
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
