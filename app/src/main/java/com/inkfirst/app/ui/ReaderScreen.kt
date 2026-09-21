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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.graphics.Rect as AndroidRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
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
import com.inkfirst.core.CourseMemory
import com.inkfirst.core.ChapterStatus
import com.inkfirst.core.Annotation
import com.inkfirst.core.Course
import com.inkfirst.core.CourseStore
import com.inkfirst.core.FIGURE_ASPECT
import com.inkfirst.core.Inline
import com.inkfirst.core.GeminiClient
import com.inkfirst.core.KtorGeminiClient
import com.inkfirst.core.MemoryUpdater
import com.inkfirst.core.QaTranscript
import com.inkfirst.core.ReaderSignals
import com.inkfirst.core.PersistedChapter
import com.inkfirst.core.Stroke as InkStroke
import com.inkfirst.core.courseDaySchedule
import com.inkfirst.core.findFigureSlots
import com.inkfirst.core.parseInlines
import com.inkfirst.core.parseMarkdown
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

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
/**
 * Height of the menu tap band, measured up from the BOTTOM of the page.
 *
 * The band sits where the menu itself opens — the panel comes up from the
 * bottom, so that is where you reach for it. Deliberately thin, roughly the
 * footer line carrying the page number: at the top it could afford 120dp
 * because nothing else lived there, but the lower half of the page is prime
 * page-turn territory and a tall band would swallow forward taps.
 */
private val MENU_BAND_HEIGHT = 72.dp

/**
 * Width of the margin strip the ? and \u270e markers live in.
 *
 * Fixed rather than intrinsic so the text column beside it does not narrow as
 * annotations accumulate — a paragraph must not reflow because you asked a
 * question about it.
 */
private val MARKER_GUTTER_WIDTH = 22.dp

/**
 * Padding between the paragraph Row's top and a marker glyph's own line box —
 * the gutter Box's top inset plus the marker Text's. Subtracted when placing
 * a marker, so the number it is positioned by is the glyph's baseline and not
 * the baseline plus whatever padding happens to be in the way.
 */
private val MARKER_PADDING_ABOVE = 4.dp

/**
 * How far a finger must travel for a drag to turn a page, and to change
 * chapter.
 *
 * Vertical is the higher bar on purpose: a vertical wobble during a
 * horizontal drag is common, and reading one as a chapter change costs the
 * reader their place in the book rather than one page.
 */
private val DRAG_TURN_THRESHOLD = 60.dp
private val DRAG_CHAPTER_THRESHOLD = 80.dp

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
    onOpenReaderNotes: () -> Unit,
    onSearch: () -> Unit,
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
    // The bookmark THIS page carries, as its stored block index, or null.
    //
    // Resolved by the pager rather than compared here, and that distinction
    // is the whole fix for orphaned bookmarks. Setting one stores the first
    // block that BEGINS on the page; going to one lands on the first page
    // CONTAINING that block. Those two relations agree only while the
    // pagination that made them is still in force — change the margin, the
    // font or the line spacing and they part company, leaving a bookmark
    // that opens its page but shows no ribbon there and offers "Bookmark
    // this page" instead of "Remove". Asking the pager which bookmark lands
    // here uses the same relation as the jump does, so the two cannot drift
    // apart again.
    var headerBookmarkBlock by remember(chapterSlug) { mutableStateOf<Int?>(null) }
    val headerBookmarked = headerBookmarkBlock != null
    var fontScale by remember { mutableStateOf(readerPrefs.getFontScale()) }
    var lineSpacing by remember { mutableStateOf(readerPrefs.getLineSpacing()) }
    var textAlign by remember { mutableStateOf(readerPrefs.getTextAlign()) }
    var horizontalPadding by remember { mutableStateOf(readerPrefs.getHorizontalPadding()) }
    var showFormatMenu by remember { mutableStateOf(false) }
    // null = closed. "" = open general chat. non-empty = open with quoted passage.
    var chatSelection by remember(chapterSlug) { mutableStateOf<String?>(null) }
    // When non-null, an AddNote overlay is up with this quoted passage.
    var pendingNoteQuote by remember(chapterSlug) { mutableStateOf<String?>(null) }
    /**
     * The ink that opened the Add-note overlay, waiting to hear whether the
     * note is actually written.
     *
     * A mark that becomes a note is spent, the same as one that becomes a
     * question — but a note only exists once it is saved, and a reader who
     * changes their mind in the overlay must get their mark back.
     */
    var pendingNoteStroke by remember(chapterSlug) { mutableStateOf<InkStroke?>(null) }
    /**
     * Drop a stroke that has been turned into a question or a note.
     *
     * One function because the rule has to be the same wherever it is
     * applied: a fresh gesture's chip, a tap on week-old ink, a note saved
     * from either. The ink was how the reader pointed at a passage; once the
     * passage is underlined and marked in the margin, the pointing is done.
     */
    val consumeStroke: (InkStroke?) -> Unit = { stroke ->
        if (stroke != null && stroke in strokes) {
            val updated = strokes - stroke
            strokes = updated
            store.saveStrokes(courseSlug, chapterSlug, updated)
        }
    }
    // When non-null, the NoteView overlay shows this annotation for read + delete.
    var viewingAnnotation by remember(chapterSlug) { mutableStateOf<Annotation?>(null) }
    /**
     * Where the stroke chip last sat, in root coords.
     *
     * Deliberately never cleared: by the time the chip closes it is gone from
     * composition, and the panel still has to repaint the pixels it used to
     * occupy. Keeping the last position is what makes scoping that possible.
     */
    var lastChipBounds by remember(chapterSlug) { mutableStateOf<Rect?>(null) }

    /**
     * Bumped to throw away a live text selection.
     *
     * [AskTextToolbar.hide] can only take the CHIP away — the selection itself
     * lives inside SelectionContainer, which exposes no way to clear it, so
     * Cancel left the passage sitting there highlighted and tapping elsewhere
     * did not shift it either (on the pen path the overlay owns the touch
     * stream, so the container never sees the tap that would normally
     * collapse it). Rebuilding the container is the only lever available.
     *
     * Only bumped when a selection is actually live: recreating the page
     * subtree is not something to do on every stray tap.
     */
    var selectionEpoch by remember(chapterSlug) { mutableStateOf(0) }
    // When non-null, a chip is showing for a tapped drawing — mirrors the
    // text-selection chip's actions (Ask / Add note / Delete). Anchor block
    // (and thus context text) is resolved lazily from stroke.blockIndex.
    var viewingStroke by remember(chapterSlug) { mutableStateOf<InkStroke?>(null) }
    // Fresh marking gesture (enclosure or margin bar) waiting for the user's
    // action. Distinct from viewingStroke because the semantics differ: this
    // one is brand new and Cancel discards it, where Cancel on a tapped
    // stroke only closes the chip.
    var pendingGestureStroke by remember(chapterSlug) { mutableStateOf<InkStroke?>(null) }
    // The Ask toolbar for the paged content — intercepts text-selection UI.
    // Hoisted to ReaderScreen scope so the floating chip can live in the outer
    // Box (which is what supports Alignment.BottomCenter).
    val clipboard = LocalClipboardManager.current
    // The toolbar is remembered, so it must not capture a lambda that
    // closes over composable state — that is the stale-closure trap, and
    // it would also force every collaborator to be declared above it. The
    // holder is reassigned each composition, so the toolbar always calls
    // the live one.
    val askRef = remember { mutableStateOf<(String, Int?) -> Unit>({ _, _ -> }) }
    val askToolbar = remember(clipboard) {
        AskTextToolbar(
            clipboard = clipboard,
            onAsk = { selectedText -> askRef.value(selectedText, null) },
            onAddNote = { selectedText -> pendingNoteQuote = selectedText },
        )
    }

    val clearSelection: () -> Unit = {
        if (askToolbar.selectionRect != null) {
            // dismissForRebuild, not hide: the epoch bump below destroys the
            // selection for real, so nothing needs suppressing afterwards.
            askToolbar.dismissForRebuild()
            selectionEpoch++
        }
    }
    // startBlockIndex = -1 is the "open at last block" sentinel — used when
    // flipping left off the first page of a chapter into the previous
    // chapter's tail. It stays -1 in state and is resolved during composition
    // (see effectiveAnchor below), not in an effect: an effect runs a frame
    // late, so the chapter opened at page 1 and then jumped to its end, which
    // on e-ink is two full repaints and reads as the reader losing the page.
    var anchorBlockIndex by remember(chapterSlug) { mutableStateOf(startBlockIndex) }
    /**
     * Whether the reader has moved within this chapter in this session.
     *
     * Progress is written only once this is true. Opening a chapter at its
     * start — which is what Contents does for a finished one — used to write
     * block 0 straight over the saved position, so merely LOOKING at a
     * chapter you had read destroyed your place in it, and the next cold
     * start resumed at page 1.
     */
    var anchorMoved by remember(chapterSlug) { mutableStateOf(false) }
    var compileState by remember(chapterSlug) { mutableStateOf<CompileState>(CompileState.Idle) }
    // Up while the reader is deciding whether to throw this chapter away.
    var confirmingRewrite by remember(chapterSlug) { mutableStateOf(false) }

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
            // One leading block, and exactly one — see [READER_LEADING_BLOCKS],
            // which anything converting a parsed index into this space relies
            // on. Add a second and search results land a block early.
            listOf(meta) + rest + listOfNotNull(tail)
        }
    }

    // Resolve the "-1 = open at last" sentinel once blocks arrive.
    // Every Ask goes through here so asking always leaves a trace on the
    // page. Without one, a question vanished the moment the chat closed: the
    // Q&A is persisted per chapter, but nothing on the page pointed at it, so
    // flipping back there was no way to see you had ever asked. That absence
    // is also why deleting the ink after Ask felt so lossy — the mark was the
    // only evidence. A margin marker records it without putting a heavy shape
    // over the words, and gives §13's reader signals the passage they want.
    val askAbout: (String, Int?) -> Unit = { quote, blockHint ->
        chatSelection = quote
        val c = course
        val trimmed = quote.trim()
        if (c != null && trimmed.isNotEmpty()) {
            val anchor = blockHint ?: findBlockContainingQuote(blocks, trimmed)
            // Asking twice about the same passage is meaningful, but two
            // identical markers is just noise on the page. The recurrence is
            // still visible in the chapter's Q&A history.
            val already = c.annotations.any {
                it.chapterSlug == chapterSlug && it.kind == "ask" &&
                    it.quote == trimmed
            }
            if (anchor != null && !already) {
                val updated = store.addAnnotation(
                    courseSlug = c.slug,
                    chapterSlug = chapterSlug,
                    blockIndex = anchor,
                    quote = trimmed,
                    note = "",
                    created = LocalDate.now().toString(),
                    at = LocalDateTime.now().withNano(0).toString(),
                    kind = "ask",
                )
                if (updated != null) course = updated
            }
        }
    }
    askRef.value = askAbout

    // The anchor actually in force. Negative means the sentinel is still
    // waiting for blocks; once they arrive it resolves to the chapter's end.
    val effectiveAnchor = if (anchorBlockIndex >= 0) {
        anchorBlockIndex
    } else {
        blocks.lastIndex.coerceAtLeast(0)
    }

    // Persist the anchor whenever it changes — this is the "last block read"
    // that Contents and the cold-start resume use to put you back.
    // Opening at the chapter's END is a position in its own right — it is
    // what flipping backwards out of the next chapter means — so it counts as
    // a move even before the first page turn.
    val anchorWorthSaving = anchorMoved || startBlockIndex < 0
    LaunchedEffect(effectiveAnchor, anchorWorthSaving, courseSlug, chapterSlug) {
        if (anchorWorthSaving && course != null && chapter != null && blocks.isNotEmpty()) {
            store.updateChapterProgress(courseSlug, chapter.n, effectiveAnchor)
        }
    }

    // Status transitions:
    //   GENERATED → READING when the reader opens a fresh chapter
    //   READING   → DONE    when the reader reaches the last block
    LaunchedEffect(chapter, effectiveAnchor, blocks) {
        val c = course ?: return@LaunchedEffect
        val ch = chapter ?: return@LaunchedEffect
        val newStatus = when {
            ch.status == ChapterStatus.GENERATED -> ChapterStatus.READING
            ch.status == ChapterStatus.READING &&
                blocks.isNotEmpty() &&
                effectiveAnchor >= blocks.lastIndex -> ChapterStatus.DONE
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
        // No horizontal padding here any more. The ink overlay lives inside
        // this column, so padding it was what stopped the pen at the text
        // edge: the margins could not be written in, the last word of a line
        // could not be underlined without running off the drawable area, and
        // a stroke that ran off it came back as a contact the tap ladder
        // resolved into a page turn. Widening the margin made the drawable
        // area SMALLER, which is the exact opposite of what the Wide setting
        // is for. The inset moves onto the text, the header and the footer;
        // the page itself, and the pen, get the whole width.
        Column(modifier = Modifier.fillMaxSize()) {
            if (isPending) {
                val pendingChapterIndex = course?.chapters?.indexOfFirst { it.slug == chapterSlug } ?: -1
                val pendingPrev = course?.chapters?.getOrNull(pendingChapterIndex - 1)
                val pendingNext = course?.chapters?.getOrNull(pendingChapterIndex + 1)
                val pendingDayLabel = course?.let { c ->
                    courseDaySchedule(c).firstOrNull { it.chapterN == chapter!!.n }?.label
                }
                CompilePrompt(
                    modifier = Modifier.padding(horizontal = horizontalPadding.dp),
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
            // The node carrying Modifier.focusRequester lives inside
            // PagedContent, which is not attached until there are blocks to
            // show. Keyed on chapterSlug alone, this request raced the
            // content and threw "FocusRequester is not initialized" whenever
            // it won — a cold start straight into the reader loses that race
            // reliably, so the app crashed on launch. Wait for the content,
            // and stay defensive on top: losing side-button focus is a minor
            // annoyance, crashing out of the chapter is not.
            LaunchedEffect(chapterSlug, blocks.isNotEmpty()) {
                if (blocks.isEmpty()) return@LaunchedEffect
                runCatching { focusRequester.requestFocus() }
            }

            // Header at ReaderScreen scope so it can read/toggle bookmarks
            // via the hoisted headerFirstBlock / headerBookmarked state.
            ReaderHeader(
                modifier = Modifier.padding(horizontal = horizontalPadding.dp),
                chapterN = chapter?.n,
                chapterCount = course?.chapters?.size,
                isBookmarked = headerBookmarked,
                canBookmark = headerFirstBlock >= 0 && course != null,
                onOpenContents = onOpenContents,
                onBookmarkToggle = {
                    val c = course ?: return@ReaderHeader
                    if (headerFirstBlock < 0) return@ReaderHeader
                    val updated = if (headerBookmarkBlock != null) {
                        // Its own stored index — which after a reflow is not
                        // necessarily this page's anchor.
                        store.removeBookmark(c.slug, chapterSlug, headerBookmarkBlock!!)
                    } else {
                        store.addBookmark(c.slug, chapterSlug, headerFirstBlock, LocalDate.now().toString())
                    }
                    course = updated ?: course
                },
            )

            CompositionLocalProvider(LocalTextToolbar provides askToolbar) {
                PagedContent(
                    horizontalPadding = horizontalPadding,
                    blocks = blocks,
                    annotations = chapterAnnotations,
                    course = course,
                    chapterSlug = chapterSlug,
                    badgeVersion = badgeVersion,
                    fontScale = fontScale,
                    lineSpacing = lineSpacing,
                    textAlign = textAlign,
                    anchorBlockIndex = effectiveAnchor,
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
                    onPageChanged = {
                        viewingStroke = null
                        pendingGestureStroke = null
                    },
                    chipOpen = viewingStroke != null || pendingGestureStroke != null,
                    chipBoundsInRoot = lastChipBounds,
                    selectionEpoch = selectionEpoch,
                    selectionActive = askToolbar.selectionRect != null,
                    onClearSelection = clearSelection,
                    onDismissChip = {
                        viewingStroke = null
                        pendingGestureStroke = null
                    },
                    onFirstBlockChange = { headerFirstBlock = it },
                    bookmarkedBlocks = course?.bookmarks
                        ?.filter { it.chapterSlug == chapterSlug }
                        ?.map { it.blockIndex }
                        .orEmpty(),
                    onPageBookmarkChange = { headerBookmarkBlock = it },
                    onOpenAnnotation = { a ->
                        // A question mark is a way back into the conversation
                        // it came from; a note opens the note. Reusing the
                        // note viewer for an "ask" would show an empty note,
                        // which is exactly the dead end the marker exists to
                        // remove. Does not re-record: the annotation is
                        // already there.
                        if (a.kind == "ask") chatSelection = a.quote
                        else viewingAnnotation = a
                    },
                    onOpenPrevChapter = prevChapter?.let { p -> { onOpenChapter(p.slug, -1) } },
                    onOpenNextChapter = nextChapter?.let { n -> { onOpenChapter(n.slug, 0) } },
                    menuOpen = showFormatMenu,
                    // Everything that can sit over the page. The pen inks at
                    // driver level and does not know a dialog is there, so
                    // the list has to be exhaustive — a modal missing from it
                    // is one you can accidentally draw straight through.
                    penEnabled = !showFormatMenu &&
                        chatSelection == null &&
                        pendingNoteQuote == null &&
                        viewingAnnotation == null,
                    onAnchorChange = {
                        anchorBlockIndex = it
                        anchorMoved = true
                    },
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
                TextButton(onClick = clearSelection) {
                    Text("Cancel", color = PaperColor)
                }
            }
        }

        // Tap-on-drawing chip — mirrors the text-selection chip but with
        // "Delete" for the drawing itself. Context text = plain text of the
        // stroke's anchor block, so Ask/Add-note behave the same way they
        // would if the user had highlighted that block by hand.
        // Marking-gesture chip, for a freshly drawn enclosure or margin bar.
        // Same 4 buttons as the tap chip so muscle memory transfers. One
        // semantic difference: Cancel here discards the mark, because it is
        // brand new and Cancel means "I did not mean that" — where Cancel on
        // a tapped stroke only closes the chip. Ask and Add note both keep
        // it; Delete is the explicit way to remove one.
        pendingGestureStroke?.let { stroke ->
            val text = stroke.annotatedText.orEmpty()
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .onGloballyPositioned { lastChipBounds = it.boundsInRoot() }
                    .background(InkColor, RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                TextButton(onClick = {
                    askAbout(text, stroke.blockIndex)
                    // A mark that became a question is SPENT.
                    //
                    // The question is now recorded three times over — the
                    // underline on the passage, the ? in the margin, the turn
                    // in the chapter's Q&A — and the ink adds a fourth mark
                    // saying the same thing, on top of the words you are
                    // trying to read. Margin bars used to be kept on the
                    // argument that they cost nothing beside the text; with
                    // the underline back they cost a duplicate.
                    //
                    // Ink that is never turned into anything stays. That is
                    // the whole distinction: a drawing is a drawing, a
                    // gesture is consumed by what it asked for.
                    consumeStroke(stroke)
                    pendingGestureStroke = null
                }) {
                    Text("Ask about this", color = PaperColor)
                }
                TextButton(onClick = {
                    pendingNoteQuote = text
                    // Held, not removed yet: the note does not exist until it
                    // is saved, and cancelling the overlay must not cost the
                    // reader the mark they drew.
                    pendingNoteStroke = stroke
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
                    .onGloballyPositioned { lastChipBounds = it.boundsInRoot() }
                    .background(InkColor, RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                if (blockText != null) {
                    TextButton(onClick = {
                        askAbout(blockText, stroke.blockIndex)
                        // Spent, exactly as a fresh gesture is — the rule
                        // cannot depend on whether the mark is a minute or a
                        // week old.
                        consumeStroke(stroke)
                        viewingStroke = null
                    }) {
                        Text("Ask about this", color = PaperColor)
                    }
                    TextButton(onClick = {
                        pendingNoteQuote = blockText
                        pendingNoteStroke = stroke
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
                onClose = { chatSelection = null },
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
                onCancel = {
                    // The mark survives a change of mind.
                    pendingNoteStroke = null
                    pendingNoteQuote = null
                },
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
                        at = LocalDateTime.now().withNano(0).toString(),
                    )
                    if (updated != null) course = updated
                    consumeStroke(pendingNoteStroke)
                    pendingNoteStroke = null
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

        if (confirmingRewrite) {
            val c = course
            val ch = chapter
            if (c == null || ch == null) {
                confirmingRewrite = false
            } else {
                RewriteChapterDialog(
                    chapterTitle = ch.title,
                    marksLost = describeChapterMarks(store, c, chapterSlug),
                    laterChapters = c.chapters
                        .filter { it.n > ch.n && it.status != ChapterStatus.PENDING }
                        .map { it.n },
                    onDismiss = { confirmingRewrite = false },
                    onConfirm = { feedback ->
                        confirmingRewrite = false
                        compileChapter(
                            course = c,
                            chapterN = ch.n,
                            scope = scope,
                            store = store,
                            apiKeyStore = apiKeyStore,
                            onState = { compileState = it },
                            isRewrite = true,
                            rewriteFeedback = feedback,
                            onDone = { updatedCourse, newMarkdown ->
                                if (updatedCourse != null && newMarkdown != null) {
                                    course = updatedCourse
                                    markdown = newMarkdown
                                    // The ink file is gone from disk; this is
                                    // the in-memory copy of it, which is keyed
                                    // on the chapter and so will not reload.
                                    strokes = emptyList()
                                    // New prose means new pages; the pager
                                    // re-seeks to this anchor on its own.
                                    anchorBlockIndex = 0
                                }
                            },
                        )
                    },
                )
            }
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
                onOpenReaderNotes = {
                    showFormatMenu = false
                    onOpenReaderNotes()
                },
                onSearch = {
                    showFormatMenu = false
                    onSearch()
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
                onRewriteChapter = {
                    showFormatMenu = false
                    confirmingRewrite = true
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
                    val updated = if (headerBookmarkBlock != null) {
                        store.removeBookmark(c.slug, chapterSlug, headerBookmarkBlock!!)
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
    /**
     * The reader's margin setting, in dp. Applied to the TEXT rather than to
     * the page, so the ink overlay can span the full width and the margins
     * are somewhere to write.
     */
    horizontalPadding: Float,
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
    /**
     * False while anything is open over the page — a menu, a modal, the chat
     * panel. Passed down so the pen stops inking the chapter underneath it.
     */
    penEnabled: Boolean,
    focusRequester: FocusRequester,
    strokes: List<InkStroke>,
    fingerDraws: Boolean,
    onStrokeAdded: (InkStroke) -> Unit,
    onStrokeErased: (InkStroke?) -> Unit,
    onStrokeTapped: (InkStroke) -> Unit,
    onStrokeGesture: (InkStroke) -> Unit,
    onAnchorChange: (Int) -> Unit,
    /**
     * The page turned. A stroke chip belongs to a stroke on a particular
     * page, so it has to go when that page does — otherwise it stays up
     * offering Delete for ink the reader can no longer see.
     */
    onPageChanged: () -> Unit,
    /** Whether a stroke chip is up, so a page tap can dismiss it instead. */
    chipOpen: Boolean,
    /**
     * Where the chip sits, so showing and hiding it repaints that pill rather
     * than a quarter of the screen. Null until one has been laid out.
     */
    chipBoundsInRoot: Rect?,
    /** Rebuilds the SelectionContainer when it changes, dropping any selection. */
    selectionEpoch: Int,
    /** Whether a text selection and its chip are showing. */
    selectionActive: Boolean,
    /** Drop a live text selection — a tap on the page means the reader is done with it. */
    onClearSelection: () -> Unit,
    onDismissChip: () -> Unit,
    onToggleMenu: () -> Unit,
    onFirstBlockChange: (Int) -> Unit,
    /** Block indices of this chapter's bookmarks, for resolving against pages. */
    bookmarkedBlocks: List<Int>,
    /** The stored block index of the bookmark that lands on the current page, or null. */
    onPageBookmarkChange: (Int?) -> Unit,
    onOpenPrevChapter: (() -> Unit)?,
    onOpenNextChapter: (() -> Unit)?,
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
    // A mascot beside a block's label pushes that block's text down by its
    // own height, so anything measuring where the text IS has to know.
    val badgeHeightPx: (String) -> Float = { kind ->
        if (kind.isNotBlank() && hasBadge(kind)) {
            with(density) { (BADGE_SIZE * fontScale).toPx() }
        } else {
            0f
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // The TEXT column's width — the page minus its margins. Every
            // measurement downstream (pagination, stroke-to-word extraction)
            // is against the text, not against the page the pen can reach.
            val widthPx = with(density) {
                (maxWidth - horizontalPadding.dp * 2).toPx().toInt().coerceAtLeast(1)
            }
            val textInsetPx = with(density) { horizontalPadding.dp.toPx() }
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
                    // The chip's stroke is on the page we just left.
                    onPageChanged()
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
            /**
             * Each on-page block's REAL top, as opposed to the virtual top in
             * [blockTopsInRoot]. The two differ only for the continuation of a
             * split paragraph, and that difference is what says whether a
             * stroke was drawn on a line this page is actually showing.
             */
            val blockVisibleTopsInRoot =
                remember(chapterSlug) { mutableStateMapOf<Int, Float>() }
            var overlayTopInRoot by remember(chapterSlug) { mutableStateOf(0f) }
            var overlayLeftInRoot by remember(chapterSlug) { mutableStateOf(0f) }
            /**
             * Where each margin marker sits, in root coordinates.
             *
             * The ink overlay owns the touch stream, so a tap on a marker is
             * reported to us as a page tap and Compose's own clickable on the
             * glyph never runs — tapping ? or ✎ just turned the page. The tap
             * handler has to hit-test them itself, the same way it already
             * hit-tests strokes. Keyed by the annotation's identity so a
             * repagination replaces rather than accumulates.
             */
            val markerRects = remember(chapterSlug, pageIndex) {
                mutableStateMapOf<String, Pair<Annotation, Rect>>()
            }
            /**
             * Where each "Show answer" button sits, in root coordinates.
             *
             * Same reason the markers need a map: while the SDK owns the touch
             * stream the button's own `clickable` never runs, so the contact
             * arrives at the tap ladder instead — and a button that happens to
             * sit in a turn band just turned the page. Keyed by the exercise's
             * block index, and rebuilt per page like the markers.
             */
            val revealRects = remember(chapterSlug, pageIndex) {
                mutableStateMapOf<Int, Rect>()
            }

            // The block index of a page's first block is just the sum of block counts
            // in all prior pages — paginate() preserves order and never splits blocks.
            // Read off the page itself now: a split paragraph appears on two
            // pages, so summing earlier page sizes would double-count it.
            val firstBlockIndex = pages.getOrNull(pageIndex)?.firstOrNull()?.index ?: 0
            // What a bookmark or a resume stores for this page. NOT the same
            // as the first block: see [anchorForPage].
            val pageAnchorIndex = anchorForPage(pages.getOrNull(pageIndex))
            LaunchedEffect(pageAnchorIndex) { onFirstBlockChange(pageAnchorIndex) }

            // Which bookmark, if any, this page is the destination of —
            // asked exactly the way the jump asks it, so a bookmark shows its
            // ribbon on the page it actually opens. A split paragraph spans
            // two pages, and findPageContainingBlock answers with the first,
            // so a bookmark can only ever resolve to one of them.
            val pageBookmark = remember(pages, blocks, pageIndex, bookmarkedBlocks) {
                bookmarkOnPage(pages, blocks, pageIndex, bookmarkedBlocks)
            }
            LaunchedEffect(pageBookmark) { onPageBookmarkChange(pageBookmark) }

            // What the ink overlay is allowed to touch on this page: the
            // global block-index range and the subset of persisted strokes
            // whose anchor block falls in it.
            val pageBlockCount = pages.getOrNull(pageIndex)?.size ?: 0
            val pageBlockRange = firstBlockIndex until (firstBlockIndex + pageBlockCount)
            val pageStrokes = strokes.filter { it.blockIndex in pageBlockRange }

            // The side buttons, claimed at the Activity's door rather than
            // through Compose focus — see [PageKeys] for why that had to
            // change. Claimed only while the page is the thing on top:
            // penEnabled is already "nothing is open over the chapter", which
            // is the same question asked for the pen.
            val turnPage = rememberUpdatedState<(Boolean) -> Unit> { forward ->
                if (forward) {
                    goNext(pageIndex, pages, { pageIndex = it }, onAnchorChange, onOpenNextChapter)
                } else {
                    goPrev(pageIndex, pages, { pageIndex = it }, onAnchorChange, onOpenPrevChapter)
                }
            }
            DisposableEffect(penEnabled) {
                val release = if (penEnabled) {
                    PageKeys.register { forward -> turnPage.value(forward) }
                } else {
                    null
                }
                onDispose { release?.invoke() }
            }

            // The one executor. Both transports — the finger detector below
            // and the Onyx overlay, which has to observe its own taps — decide
            // with resolveTap and act here, so a rule exists exactly once.
            val performTap: (TapAction) -> Unit = { action ->
                when (action) {
                    TapAction.CloseMenu -> onToggleMenu()
                    TapAction.DismissChip -> onDismissChip()
                    TapAction.ClearSelection -> onClearSelection()
                    is TapAction.PressButton -> revealed[action.blockIndex] = true
                    is TapAction.OpenMarker -> onOpenAnnotation(action.annotation)
                    is TapAction.ShowStrokeChip -> onStrokeTapped(action.stroke)
                    TapAction.ToggleMenu -> onToggleMenu()
                    TapAction.PrevPage ->
                        goPrev(pageIndex, pages, { pageIndex = it }, onAnchorChange, onOpenPrevChapter)
                    TapAction.NextPage ->
                        goNext(pageIndex, pages, { pageIndex = it }, onAnchorChange, onOpenNextChapter)
                    TapAction.PrevChapter -> onOpenPrevChapter?.invoke()
                    TapAction.NextChapter -> onOpenNextChapter?.invoke()
                    TapAction.Ignore -> Unit
                }
            }

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
                            val dragThresholdPx = DRAG_TURN_THRESHOLD.toPx()
                            val vDragThresholdPx = DRAG_CHAPTER_THRESHOLD.toPx()
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
                                        // A drag is the one meaning the tap
                                        // ladder does not cover — it has no
                                        // position, only a direction — so it
                                        // gets a rule of its own, in the same
                                        // file, resolved by both transports.
                                        if (isDrag) {
                                            performTap(
                                                resolveSwipe(
                                                    totalDx,
                                                    totalDy,
                                                    dragThresholdPx,
                                                    vDragThresholdPx,
                                                    menuOpen = menuOpen,
                                                    chipOpen = chipOpen,
                                                    selectionActive = selectionActive,
                                                )
                                            )
                                        } else {
                                            performTap(
                                                resolveTap(
                                                    downPos.x,
                                                    downPos.y,
                                                    size.width.toFloat(),
                                                    size.height.toFloat(),
                                                    menuBandPx,
                                                    menuOpen = menuOpen,
                                                    chipOpen = chipOpen,
                                                    selectionActive = selectionActive,
                                                    // Null on this path by
                                                    // design: markers are
                                                    // clickable composables and
                                                    // InkOverlay runs its own
                                                    // stroke hit-test, and both
                                                    // consume — which the
                                                    // isConsumed check above has
                                                    // already turned into a break.
                                                    markerHit = null,
                                                    strokeHit = null,
                                                )
                                            )
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
                    key(selectionEpoch) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = horizontalPadding.dp),
                        ) {
                            // Drawable margin above the first block. Marking
                            // the first line means stroking slightly ABOVE it —
                            // an underline sits below its baseline, a circle
                            // encloses from above — and with the text flush to
                            // the overlay's top edge there was nowhere to put
                            // the pen. Costs one line of page height and makes
                            // the first line behave like every other one.
                            Spacer(Modifier.height(INK_TOP_MARGIN))
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
                                            // The real top of the slice shown
                                            // on THIS page, without the
                                            // virtual-top adjustment. Ink
                                            // anchors to the virtual top so a
                                            // split block keeps one
                                            // coordinate space, but deciding
                                            // which page a stroke belongs to
                                            // needs the visible edge: a
                                            // stroke above it was drawn on a
                                            // line that is now on the
                                            // previous page.
                                            blockVisibleTopsInRoot[globalIdx] =
                                                coords.positionInRoot().y
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
                                        onMarkerBounds = { a, r ->
                                            markerRects[a.created + "|" + a.quote] = a to r
                                        },
                                        onRevealBounds = { r ->
                                            if (r == null) revealRects.remove(globalIdx)
                                            else revealRects[globalIdx] = r
                                        },
                                        loadFigure = loadFigure,
                                        loadBadge = loadBadge,
                                    )
                                }
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
                                // A margin bar marks a PASSAGE, and passages
                                // cross paragraphs. Reading only the anchor
                                // block stopped the selection dead at that
                                // paragraph's last line however far the bar
                                // was drawn — measured: a 469px bar against a
                                // block whose text ended 218px short of it.
                                // Underlines and circles stay single-block,
                                // which is what they mean.
                                val text = if (isVerticalMarkStroke(raw)) {
                                    extractMarkedPassage(
                                        raw, blocks, blockTopsInRoot, overlayTopInRoot,
                                        widthPx, measurer, density, fontScale, lineSpacing,
                                        badgeHeightPx,
                                    )
                                } else {
                                    extractStrokeText(
                                        raw, anchor, widthPx, measurer, density,
                                        fontScale, lineSpacing,
                                        badgeHeightPx = badgeHeightPx,
                                    )
                                }
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
                            val verticalMark = isVerticalMarkStroke(enriched)
                            android.util.Log.d(
                                "InkGesture",
                                "stroke pts=${enriched.points.size} enclosing=$enclosing " +
                                    "vertical=$verticalMark " +
                                    "text=${enriched.annotatedText?.take(40)?.ifBlank { "<blank>" } ?: "<null>"}",
                            )
                            if ((enclosing || verticalMark) &&
                                !enriched.annotatedText.isNullOrBlank()
                            ) {
                                // No explicit unlock needed here, unlike the
                                // tap chip: this fires at the end of a stroke,
                                // and the stroke's own scheduleUnlock("draw")
                                // repaints the panel a moment later.
                                onStrokeGesture(enriched)
                            }
                    }
                    val overlayModifier = Modifier.onGloballyPositioned { coords ->
                        overlayTopInRoot = coords.positionInRoot().y
                        overlayLeftInRoot = coords.positionInRoot().x
                    }
                    if (USE_ONYX_PEN) {
                        OnyxInkOverlay(
                            textInsetPx = textInsetPx,
                            pageStrokes = pageStrokes,
                            blockTopY = { idx -> blockTopsInRoot[idx]?.minus(overlayTopInRoot) },
                            blockVisibleTop = { idx ->
                                blockVisibleTopsInRoot[idx]?.minus(overlayTopInRoot)
                            },
                            pageBlockRange = pageBlockRange,
                            onStrokeAdded = onStrokeAddedEnriched,
                            onStrokeErased = onStrokeErased,
                            chipOpen = chipOpen,
                            drawingEnabled = penEnabled,
                            chipRect = chipBoundsInRoot?.let { b ->
                                AndroidRect(
                                    (b.left - overlayLeftInRoot).toInt(),
                                    (b.top - overlayTopInRoot).toInt(),
                                    (b.right - overlayLeftInRoot).toInt(),
                                    (b.bottom - overlayTopInRoot).toInt(),
                                )
                            },
                            // The overlay observes its own taps but does not
                            // interpret them: it hands the contact and the
                            // stroke under it to the one ladder, and carries
                            // out whatever comes back. Markers are hit-tested
                            // here because their rects live on this side — a
                            // marker is a target, not part of the page, and
                            // while the SDK owns the touch stream the
                            // clickable on the glyph never runs, so a tap on
                            // ? or \u270e simply turned the page.
                            resolveTap = { x, y, w, h, strokeHit ->
                                val inRoot = Offset(x + overlayLeftInRoot, y + overlayTopInRoot)
                                val hitMarker = markerRects.values.firstOrNull { (_, r) ->
                                    r.contains(inRoot)
                                }?.first
                                val hitButton = revealRects.entries
                                    .firstOrNull { (_, r) -> r.contains(inRoot) }?.key
                                resolveTap(
                                    x, y, w, h,
                                    with(density) { MENU_BAND_HEIGHT.toPx() },
                                    menuOpen = menuOpen,
                                    chipOpen = chipOpen,
                                    selectionActive = selectionActive,
                                    buttonHit = hitButton,
                                    markerHit = hitMarker,
                                    strokeHit = strokeHit,
                                )
                            },
                            resolveSwipe = { dx, dy ->
                                resolveSwipe(
                                    dx, dy,
                                    with(density) { DRAG_TURN_THRESHOLD.toPx() },
                                    with(density) { DRAG_CHAPTER_THRESHOLD.toPx() },
                                    menuOpen = menuOpen,
                                    chipOpen = chipOpen,
                                    selectionActive = selectionActive,
                                )
                            },
                            onTapAction = performTap,
                            modifier = overlayModifier,
                        )
                    } else {
                        InkOverlay(
                            textInsetPx = textInsetPx,
                            pageStrokes = pageStrokes,
                            blockTopY = { idx -> blockTopsInRoot[idx]?.minus(overlayTopInRoot) },
                            blockVisibleTop = { idx ->
                                blockVisibleTopsInRoot[idx]?.minus(overlayTopInRoot)
                            },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp)
                        // The footer is a menu target too — the ink overlay's
                        // box stops short of this row, so a tap at the true
                        // bottom edge of the page, which is where a reader
                        // aims for "bottom", would otherwise land outside
                        // every detector and do nothing.
                        //
                        // But it is the menu target only across its MIDDLE.
                        // It used to toggle the menu at any x, which left the
                        // bottom of the page saying two different things one
                        // pixel apart: the band above turned the page at its
                        // corners while the line below opened the menu there.
                        // The whole bottom strip reads as one thing now — the
                        // corners turn, the centre opens the menu — which is
                        // the rule the band already followed. Passing the
                        // row's own height as the band makes every y in it
                        // "bottom", so only x decides.
                        .pointerInput(menuOpen, chipOpen, selectionActive, horizontalPadding) {
                            detectTapGestures { at ->
                                performTap(
                                    resolveTap(
                                        at.x,
                                        at.y,
                                        size.width.toFloat(),
                                        size.height.toFloat(),
                                        size.height.toFloat(),
                                        menuOpen = menuOpen,
                                        chipOpen = chipOpen,
                                        selectionActive = selectionActive,
                                    )
                                )
                            }
                        }
                        // AFTER the tap node, so the node still measures the
                        // full page and its turn bands line up with the ones
                        // in the band above. Only the type is inset.
                        .padding(horizontal = horizontalPadding.dp),
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
                            // Describes the zones as they now are: narrow
                            // bands at the two EDGES turn the page, the menu
                            // band runs along the bottom where the panel
                            // itself opens, and the middle of the page answers
                            // to nothing. A hint that teaches the old layout
                            // is worse than none — and now that the centre is
                            // inert, an arrow pointing at the edges is the
                            // hint that matters.
                            text = "tap edge ← / → · bottom for menu ",
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
    modifier: Modifier = Modifier,
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
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 36.dp),
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
    onOpenReaderNotes: () -> Unit,
    onSearch: () -> Unit,
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
    onRewriteChapter: () -> Unit,
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
            // From the page too, not only from Contents: "where did it say
            // that" is a thought you have mid-paragraph.
            Box(
                modifier = Modifier
                    .clickable { onSearch() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Search",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkColor,
                )
            }
        }

        // What the book has noticed about this reader, and the means to
        // cross a line out. Reachable from the page as well as from Contents
        // on purpose: the moment you want to argue with an observation is the
        // moment you are reading the chapter it shaped.
        MenuRow(label = "Reading") {
            Box(
                modifier = Modifier
                    .clickable { onOpenReaderNotes() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "How it has gone",
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

        // The repair mechanism the generate-a-book premise needs: the model
        // will occasionally write a dud, and without this the reader's only
        // option is to live with it for the rest of the course.
        MenuRow(label = "Chapter") {
            Box(
                modifier = Modifier
                    .clickable(enabled = !busyDrawing) { onRewriteChapter() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Rewrite this chapter",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (busyDrawing) MutedColor else InkColor,
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
            // 70dp broke "Reading" across two lines — "Readin" over a lone
            // "g" — and left "Chapter" a hair from the same fate. The column
            // is a label gutter, so it costs the controls beside it nothing
            // to be wide enough for the longest word in it.
            modifier = Modifier.width(82.dp),
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
    onMarkerBounds: (Annotation, Rect) -> Unit,
    /** Where a "Show answer" button sits, or null when there is no longer one. */
    onRevealBounds: (Rect?) -> Unit,
    loadFigure: (String) -> ImageBitmap?,
    loadBadge: (String) -> ImageBitmap?,
) {
    when (block) {
        is Block.Heading -> HeadingBlock(block, fontScale, atPageTop, loadFigure)
        is Block.Paragraph -> ParagraphBlock(
            block.inlines, fontScale, lineSpacing, textAlign,
            annotations, onOpenAnnotation, onMarkerBounds,
        )
        is Block.BulletItem -> ListItemBlock("• ", block.inlines, fontScale, lineSpacing)
        is Block.OrderedItem -> ListItemBlock("${block.n}. ", block.inlines, fontScale, lineSpacing)
        is Block.Rule -> RuleBlock()
        is Block.CustomFence -> when (block.kind) {
            "exercise" ->
                ExerciseBlock(block, fontScale, lineSpacing, revealed, onReveal, onRevealBounds, loadBadge)
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
    onMarkerBounds: (Annotation, Rect) -> Unit,
) {
    // Every annotation underlines its passage, questions included.
    //
    // An "ask" used to be a margin ? and nothing else, on the theory that a
    // clean page was worth more than the mark. On the device that reads as a
    // question mark floating beside a paragraph with no way to tell WHICH
    // sentence it belongs to — and the answer, from Aliaksei, was to ask for
    // the underline back.
    val annotated = if (annotations.isEmpty()) {
        renderInlines(inlines)
    } else {
        renderInlinesWithUnderlines(inlines, annotations.map { it.quote })
    }
    // Precompute character ranges of each underlined quote so a tap inside
    // one opens the note directly, matching how physical marginalia work
    // (the mark IS the affordance). Falls through to the parent page-turn
    // handler when the tap misses every range — we only .consume() on a hit.
    val plainText = annotated.text
    val annotationRanges = remember(plainText, annotations) {
        annotations.mapNotNull { a ->
            val needle = firstLineOf(a.quote.trim())
            if (needle.isBlank()) return@mapNotNull null
            val start = plainText.indexOf(needle)
            if (start < 0) null else (start until start + needle.length) to a
        }
    }
    var textLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    // Where each margin marker belongs vertically: on the BASELINE of the
    // line its quote starts on.
    //
    // Stacking them from the paragraph's top happened to look right at the
    // default type size, because a short paragraph is a couple of lines and
    // the error was small. Turn the font up and the same paragraph is six
    // lines tall, and the ? sits two lines away from the sentence it is
    // about. The layout already knows the answer; it was simply never asked.
    //
    // Baselines rather than line tops, and measured rather than estimated.
    // A line BOX is mostly leading — for 18sp type on 30sp leading, roughly a
    // quarter of the box is empty space above the glyphs — so hanging a 14sp
    // ? from the box top floats it above the words, and dropping it by some
    // guessed fraction only moves the error somewhere else. Two baselines on
    // the same line is what "on the same line" means on paper, and both
    // numbers are available exactly.
    val markerMeasurer = rememberTextMeasurer()
    val markerStyle = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 14.sp * fontScale,
    )
    val markerLead = with(LocalDensity.current) { MARKER_PADDING_ABOVE.toPx() }
    val markerBaseline = remember(fontScale) {
        markerMeasurer.measure(AnnotatedString("?"), markerStyle).firstBaseline
    }
    val markerYs = remember(plainText, annotations, textLayout, fontScale) {
        val layout = textLayout
        val minGapPx = 24f * fontScale
        var lastY = Float.NEGATIVE_INFINITY
        annotations
            .map { a ->
                val needle = firstLineOf(a.quote.trim())
                val start = if (needle.isBlank()) -1 else plainText.indexOf(needle)
                val line = if (start < 0 || layout == null) 0 else layout.getLineForOffset(start)
                // The glyph's own box sits [markerLead] below wherever this
                // offset puts it — the paddings between the Row and the text.
                val y = (layout?.getLineBaseline(line) ?: markerBaseline) -
                    markerBaseline - markerLead
                a to y
            }
            .sortedBy { it.second }
            // Two questions about the same line would otherwise print one
            // marker on top of the other.
            .map { (a, y) ->
                val placed = maxOf(y, lastY + minGapPx)
                lastY = placed
                a to placed
            }
            .toMap()
    }
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
        // Margin markers to the right of the paragraph — one per annotation,
        // tappable. Sits outside the Text so clicks aren't fighting
        // SelectionContainer. A written note and a question asked are
        // different acts and read differently at a glance: ✎ is something you
        // wrote, ? is something you wanted explained.
        if (annotations.isNotEmpty()) {
            // A Box with per-marker offsets, not a Column: the markers are
            // positioned against the TEXT beside them, which a stack cannot
            // express. Fixed width so the text column is the same width
            // whether a paragraph carries one marker or four.
            Box(
                modifier = Modifier
                    .padding(start = 6.dp, top = 2.dp)
                    .width(MARKER_GUTTER_WIDTH * fontScale),
            ) {
                annotations.forEach { a ->
                    val y = markerYs[a] ?: 0f
                    Text(
                        text = if (a.kind == "ask") "?" else "✎",
                        style = markerStyle,
                        color = InkColor,
                        modifier = Modifier
                            .offset { IntOffset(0, y.toInt()) }
                            .onGloballyPositioned { onMarkerBounds(a, it.boundsInRoot()) }
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
    onRevealBounds: (Rect?) -> Unit,
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
            // The bounds are reported so the tap ladder can treat this as the
            // target it is. On the pen path the SDK owns the touch stream and
            // this button's own click never runs — the contact goes to the
            // ladder, and before it knew about buttons a tap here just turned
            // the page. Cleared on disposal so a revealed answer (or a page
            // turn) does not leave a live target behind.
            TextButton(
                onClick = onReveal,
                modifier = Modifier.onGloballyPositioned { onRevealBounds(it.boundsInRoot()) },
            ) { Text("Show answer", color = InkColor) }
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose { onRevealBounds(null) }
            }
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
        onAnchorChange(anchorForPage(pages.getOrNull(pageIndex - 1)))
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
        onAnchorChange(anchorForPage(pages.getOrNull(pageIndex + 1)))
    } else {
        onOpenNextChapter?.invoke()
    }
}

internal fun renderInlines(inlines: List<Inline>): AnnotatedString = buildAnnotatedString {
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
/**
 * A tall, narrow stroke spanning more than one line's worth of height: a
 * margin bar or bracket beside a passage.
 *
 * This is a marking gesture like an enclosure, so it opens the chip. An
 * underline is not — underlines and free scribbles stay silent marginalia,
 * which is the whole point of only SOME strokes speaking up.
 */
private fun isVerticalMarkStroke(stroke: InkStroke): Boolean {
    val points = stroke.points
    if (points.size < 4) return false
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
    return h >= VERTICAL_MARK_MIN_HEIGHT_PX && h > w * VERTICAL_STROKE_RATIO
}

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
/**
 * How much taller than wide a stroke must be, while crossing more than one
 * line, to read as a margin bar or bracket rather than an underline.
 */
/**
 * Text under a margin bar, across every block the bar spans.
 *
 * Ink stores one blockIndex, and that stays true — this only widens what the
 * bar READS, at draw time, and the stroke still anchors where it always did.
 * Each block is handled by translating the stroke into that block's local
 * coordinate space and reusing the ordinary per-block extraction, so the
 * whole-line behaviour and the line-overlap rules stay in exactly one place.
 *
 * Near-miss recovery is off for the walk: it exists for the reader being
 * slightly off on the block they marked, whereas here a miss means the bar
 * simply ended, and claiming a line would run the selection past its end.
 */
private fun extractMarkedPassage(
    stroke: InkStroke,
    blocks: List<Block>,
    blockTopsInRoot: Map<Int, Float>,
    overlayTopInRoot: Float,
    widthPx: Int,
    measurer: TextMeasurer,
    density: Density,
    fontScale: Float,
    lineSpacing: Float,
    badgeHeightPx: (String) -> Float,
): String? {
    val anchorTop = blockTopsInRoot[stroke.blockIndex]?.minus(overlayTopInRoot)
        ?: return null
    val parts = mutableListOf<String>()
    for (index in blockTopsInRoot.keys.sorted()) {
        val block = blocks.getOrNull(index) ?: continue
        val top = blockTopsInRoot[index]?.minus(overlayTopInRoot) ?: continue
        // Stroke offsets are relative to their own block's top, so shift them
        // into this block's space before asking what they cover.
        val shift = anchorTop - top
        val moved = stroke.copy(
            blockIndex = index,
            points = stroke.points.map { it.copy(y = it.y + shift) },
        )
        val part = extractStrokeText(
            moved, block, widthPx, measurer, density, fontScale, lineSpacing,
            allowNearMiss = false,
            badgeHeightPx = badgeHeightPx,
        )
        if (!part.isNullOrBlank()) parts += part
    }
    if (parts.isEmpty()) return null
    return parts.joinToString(" ").trim().ifBlank { null }
}

private const val VERTICAL_STROKE_RATIO = 1.5f

/**
 * Minimum height for a stroke to read as a margin bar rather than a stray
 * tick — about two lines at default settings, so one deliberate drag beside a
 * passage qualifies and a flick of the pen does not.
 */
private const val VERTICAL_MARK_MIN_HEIGHT_PX = 90f

/**
 * Below this share of a line's height, a stroke is an underline or a
 * strikethrough — a mark on ONE line — rather than a shape drawn around text.
 */
private const val THIN_STROKE_FRACTION = 0.8f

/**
 * Top share of a line box that is leading rather than glyphs. A stroke
 * starting in there is underlining the line ABOVE.
 */
private const val LINE_LEADING_FRACTION = 0.3f

/**
 * Share of a line a shape must cover to count as drawn around it.
 */
private const val LINE_COVERAGE_FRACTION = 0.5f

/**
 * Share of a word that must sit under the stroke for a clipped word at either
 * end to be kept whole rather than dropped.
 */
private const val PARTIAL_WORD_KEEP_FRACTION = 0.5f

/**
 * Where a block's TEXT sits inside the block, and how it is set.
 *
 * Stroke coordinates are stored relative to a block's top-left corner, but
 * what a stroke marks is words — so anything that wants to know which words
 * has to measure the same text, at the same size, in the same column, offset
 * by however far into the block the renderer actually put it.
 *
 * Keep this in step with the renderer and with Paginator.estimateHeight. All
 * three measure the same page; when they disagree the reader sees it as ink
 * selecting the wrong words, or as a clipped last line.
 */
private data class BlockTextGeometry(
    val plain: String,
    val style: TextStyle,
    /** Width of the text column, which is not the block's width inside a border. */
    val widthPx: Int,
    val leftOffsetPx: Float,
    val topOffsetPx: Float,
)

private fun blockTextGeometry(
    block: Block,
    widthPx: Int,
    density: Density,
    measurer: TextMeasurer,
    fontScale: Float,
    lineSpacing: Float,
    badgeHeightPx: (String) -> Float,
): BlockTextGeometry? = with(density) {
    val bodyStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 18.sp * fontScale,
        lineHeight = 30.sp * fontScale * lineSpacing,
        hyphens = Hyphens.Auto,
    )
    return@with when (block) {
        is Block.Paragraph -> BlockTextGeometry(
            plain = block.inlines.plainText(),
            style = bodyStyle,
            widthPx = widthPx,
            leftOffsetPx = 0f,
            topOffsetPx = 0f,
        )
        is Block.Heading -> BlockTextGeometry(
            plain = block.inlines.plainText(),
            style = bodyStyle,
            widthPx = widthPx,
            leftOffsetPx = 0f,
            topOffsetPx = 0f,
        )
        is Block.BulletItem, is Block.OrderedItem -> {
            val prefix = if (block is Block.OrderedItem) "${block.n}. " else "• "
            val inlines = when (block) {
                is Block.BulletItem -> block.inlines
                is Block.OrderedItem -> block.inlines
                else -> emptyList()
            }
            // ListItemBlock indents the row and sets the marker in a Text of
            // its own, so the body column starts after both.
            val indent = 18.dp.toPx()
            val markerWidth = measurer.measure(
                text = AnnotatedString(prefix),
                style = bodyStyle,
            ).size.width.toFloat()
            BlockTextGeometry(
                plain = inlines.plainText(),
                style = bodyStyle,
                widthPx = (widthPx - indent - markerWidth).toInt().coerceAtLeast(1),
                leftOffsetPx = indent + markerWidth,
                topOffsetPx = 0f,
            )
        }
        is Block.CustomFence -> {
            val body = when (block.kind) {
                // Only the question is on the page until the answer is
                // revealed, and the revealed answer is set differently again.
                "exercise" -> splitExerciseBody(block.body).first
                "recap" -> block.body.trim()
                else -> return@with null
            }
            // BorderedBlock: 4dp spacer, 8dp outer vertical padding, a 1dp
            // border, 12dp inner padding, then the label row (as tall as the
            // mascot when the course has one) and its 6dp gap.
            val labelText = 11.sp.toPx() * fontScale
            val label = maxOf(labelText, badgeHeightPx(badgeKindFor(block.kind) ?: ""))
            val inset = 12.dp.toPx() + 1.dp.toPx()
            BlockTextGeometry(
                // Parsed, like the renderer: the backticks and asterisks are
                // gone from the page, so they must be gone from the measure.
                plain = parseInlines(body).plainText(),
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 17.sp * fontScale,
                    lineHeight = 28.sp * fontScale * lineSpacing,
                ),
                widthPx = (widthPx - inset * 2).toInt().coerceAtLeast(1),
                leftOffsetPx = inset,
                topOffsetPx = 4.dp.toPx() + 8.dp.toPx() + inset + label + 6.dp.toPx(),
            )
        }
        is Block.Rule -> null
    }
}

private fun extractStrokeText(
    stroke: InkStroke,
    block: Block,
    widthPx: Int,
    measurer: TextMeasurer,
    density: Density,
    fontScale: Float,
    lineSpacing: Float,
    /**
     * Whether a stroke that misses every line may still claim the nearest
     * one. True for the block a stroke is anchored to, where a near miss is
     * the reader being slightly off. False when walking neighbouring blocks
     * for a margin bar, where a near miss means the bar simply stopped there
     * and claiming a line would over-select.
     */
    allowNearMiss: Boolean = true,
    /** Height of the mascot beside a block label, or 0 when the course has none. */
    badgeHeightPx: (String) -> Float = { 0f },
): String? {
    val geometry = blockTextGeometry(
        block, widthPx, density, measurer, fontScale, lineSpacing, badgeHeightPx,
    ) ?: return null
    val plain = geometry.plain
    if (plain.isBlank() || stroke.points.isEmpty() || widthPx <= 0) return null

    val layout = measurer.measure(
        text = AnnotatedString(plain),
        style = geometry.style,
        constraints = Constraints(maxWidth = geometry.widthPx),
    )

    var minX = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    stroke.points.forEach { p ->
        // Stroke points are relative to the BLOCK's top-left; the layout just
        // measured is relative to the TEXT's. For a paragraph those are the
        // same corner and this subtracts nothing. For a bordered block they
        // are a border, a padding and a whole mascot apart — which is why a
        // pen mark inside a RECAP resolved to nothing at all, fell back to
        // the block's entire body, and sent the whole box to the model.
        val x = p.x - geometry.leftOffsetPx
        val y = p.y - geometry.topOffsetPx
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
    }

    // Which lines the stroke touches. A line counts only if its Y range
    // overlaps the stroke's by at least 30% of the smaller of the two. A pure
    // slop check (an older version) grabbed adjacent lines any time the stroke
    // came within a fraction of a line-height of the boundary — a single-line
    // circle around "It is simply a" pulled chunks from the lines above and
    // below. This ratio keeps a thin underline (small stroke fully inside a
    // line → ratio ≈ 1) while cutting neighbours (1px of a 30px line → 3%).
    val strokeHeight = maxY - minY
    val strokeWidth = maxX - minX
    val bodyLineHeight =
        if (layout.lineCount > 0) layout.getLineBottom(0) - layout.getLineTop(0) else 0f

    // A tall stroke crossing lines is a margin bar or bracket: the reader is
    // marking a PASSAGE, not picking words, and its X range is meaningless
    // (often it is in the margin, touching no glyph at all).
    val isVerticalMark = strokeHeight > strokeWidth * VERTICAL_STROKE_RATIO &&
        bodyLineHeight > 0f && strokeHeight > bodyLineHeight * 1.2f

    // A thin stroke is an underline or a strikethrough, and it marks exactly
    // ONE line. Letting the overlap test choose was the "returns the line
    // below" defect: `minDim` is min(strokeHeight, lineHeight), so for a 6px
    // underline the bar to clear is ~2px, and an underline that dips barely
    // into the next line's box qualifies it too. Underlining "Artificial
    // Intelligence" returned that plus "fiction movie" from the line beneath.
    val isThinMark = !isVerticalMark && bodyLineHeight > 0f &&
        strokeHeight < bodyLineHeight * THIN_STROKE_FRACTION

    val touched = mutableListOf<Int>()
    if (isThinMark) {
        touched += lineMarkedBy(layout, minY)
    } else {
        // Circles and other tall shapes genuinely span lines. A line counts
        // only if the stroke covers most of THAT LINE.
        //
        // The measure used to be a share of min(strokeHeight, lineHeight),
        // which is the wrong denominator for a shape drawn around text: a
        // circle stands taller than its line, so min() is the line, and a
        // circle poking ~20px into the line above cleared 30% of a 66px line
        // and dragged it in. Circling a word kept taking the neighbouring
        // line with it. Measured against the line alone, that same poke is
        // 30% and fails, while a circle actually drawn around a line covers
        // it fully and passes.
        for (line in 0 until layout.lineCount) {
            val lineTop = layout.getLineTop(line)
            val lineBottom = layout.getLineBottom(line)
            val overlap = kotlin.math.max(
                0f,
                kotlin.math.min(lineBottom, maxY) - kotlin.math.max(lineTop, minY),
            )
            val lineHeight = lineBottom - lineTop
            if (lineHeight <= 0f || overlap < lineHeight * LINE_COVERAGE_FRACTION) continue
            touched += line
        }
    }
    if (touched.isEmpty()) {
        // Nothing passed the ratio. A stroke sitting just off a line is a
        // near miss and should still resolve — returning null there is what
        // made the enclosure chip look unpredictable, since the gesture needs
        // BOTH a shape and text. But a stroke nowhere near this block's text
        // means the stroke was anchored to the WRONG BLOCK, and answering
        // with that block's nearest line is worse than answering nothing: it
        // quotes confident, wrong text back at the reader and sends it to the
        // model. So the fallback is bounded to one line height.
        val centre = (minY + maxY) / 2f
        val nearest = (0 until layout.lineCount).minByOrNull { l ->
            kotlin.math.abs((layout.getLineTop(l) + layout.getLineBottom(l)) / 2f - centre)
        } ?: return null
        val nearTop = layout.getLineTop(nearest)
        val nearBottom = layout.getLineBottom(nearest)
        val distance = when {
            centre < nearTop -> nearTop - centre
            centre > nearBottom -> centre - nearBottom
            else -> 0f
        }
        val lineHeight = nearBottom - nearTop
        if (!allowNearMiss || (lineHeight > 0f && distance > lineHeight)) {
            android.util.Log.d(
                "InkGesture",
                "extract blk=${stroke.blockIndex} REJECT strokeY=$minY..$maxY " +
                    "nearestLine=$nearest ($nearTop..$nearBottom) distance=$distance " +
                    "— stroke is not over this block's text, likely mis-anchored",
            )
            return null
        }
        touched += nearest
    }

    val segments = if (isVerticalMark) {
        touched.map { line ->
            plain.substring(layout.getLineStart(line), lineEndOf(layout, plain, line)).trim()
        }
    } else {
        touched.mapNotNull { line -> wordsUnderStroke(plain, layout, line, minX, maxX) }
    }
    val result = segments.joinToString(" ").filterNot { it == '\n' }.trim()
    android.util.Log.d(
        "InkGesture",
        "extract blk=${stroke.blockIndex} strokeY=$minY..$maxY x=$minX..$maxX " +
            "lines=${layout.lineCount} textH=${layout.size.height} " +
            "touched=$touched vertical=$isVerticalMark -> ${result.take(120)}",
    )
    if (segments.isEmpty() || result.isBlank()) return null
    return result
}

/**
 * The line a thin stroke marks, from the stroke's TOP edge.
 *
 * The top is the reliable end of an underline: it sits right under the glyph
 * bottoms of the line being marked. The stroke's centre or bottom can easily
 * fall inside the NEXT line's box, because a line box extends well below its
 * baseline and the leading belongs to the line beneath.
 *
 * If the top lands in the leading at the very top of a box, the stroke is in
 * the gap under the previous line's glyphs, and that is the line meant.
 */
private fun lineMarkedBy(layout: TextLayoutResult, strokeTop: Float): Int {
    var line = 0
    for (l in 0 until layout.lineCount) {
        if (strokeTop >= layout.getLineTop(l)) line = l else break
    }
    val top = layout.getLineTop(line)
    val height = layout.getLineBottom(line) - top
    if (line > 0 && height > 0f && (strokeTop - top) < height * LINE_LEADING_FRACTION) {
        return line - 1
    }
    return line
}

/** [TextLayoutResult.getLineEnd] can run past the string on the last line. */
private fun lineEndOf(layout: TextLayoutResult, plain: String, line: Int): Int =
    layout.getLineEnd(line).coerceAtMost(plain.length)

/**
 * The words on one line that a stroke passes under, snapped to whole words.
 *
 * Character-level boundaries returned fragments like "to your ve" and "irst
 * day o" — real examples from the device log. A fragment is worse than
 * useless downstream: it is what the model gets asked about, and what the
 * note quotes back. So a partial word at either END of the run is dropped,
 * which is what a reader means by underlining "the middle bit".
 *
 * The exception is a stroke that covers part of ONE word and nothing else.
 * Dropping both ends would leave nothing, and the reader clearly meant that
 * word, so it comes back whole.
 */
private fun wordsUnderStroke(
    plain: String,
    layout: TextLayoutResult,
    line: Int,
    minX: Float,
    maxX: Float,
): String? {
    val lineStart = layout.getLineStart(line)
    val lineEnd = lineEndOf(layout, plain, line)
    var firstHit = -1
    var lastHit = -1
    for (offset in lineStart until lineEnd) {
        val box = layout.getBoundingBox(offset)
        if (box.right >= minX && box.left <= maxX) {
            if (firstHit < 0) firstHit = offset
            lastHit = offset
        }
    }
    if (firstHit < 0) return null
    val hitEnd = (lastHit + 1).coerceAtMost(lineEnd)

    fun isSpace(i: Int) = i < 0 || i >= plain.length || plain[i].isWhitespace()

    // Started mid-word. Dropping it outright was too eager: clipping the
    // first letter of a word threw the word away. If most of it is under the
    // stroke, the reader meant it — take it whole; otherwise step past it.
    var s = firstHit
    if (!isSpace(s - 1) && s - 1 >= lineStart) {
        var wordStart = s
        while (wordStart > lineStart && !isSpace(wordStart - 1)) wordStart--
        var wordEnd = s
        while (wordEnd < lineEnd && !isSpace(wordEnd)) wordEnd++
        val total = wordEnd - wordStart
        if (total > 0 && (wordEnd - s).toFloat() / total >= PARTIAL_WORD_KEEP_FRACTION) {
            s = wordStart
        } else {
            while (s < hitEnd && !isSpace(s)) s++
            while (s < hitEnd && isSpace(s)) s++
        }
    }
    // Ended mid-word, same rule from the other side.
    var e = hitEnd
    if (!isSpace(e) && e < lineEnd) {
        var wordStart = e
        while (wordStart > lineStart && !isSpace(wordStart - 1)) wordStart--
        var wordEnd = e
        while (wordEnd < lineEnd && !isSpace(wordEnd)) wordEnd++
        val total = wordEnd - wordStart
        if (total > 0 && (e - wordStart).toFloat() / total >= PARTIAL_WORD_KEEP_FRACTION) {
            e = wordEnd
        } else {
            while (e > s && !isSpace(e - 1)) e--
            while (e > s && isSpace(e - 1)) e--
        }
    }
    if (e > s) {
        val inner = plain.substring(s, e).trim()
        if (inner.isNotEmpty()) return inner
    }

    // Part of a single word — widen to the whole of it.
    var ws = firstHit
    while (ws > lineStart && !isSpace(ws - 1)) ws--
    var we = hitEnd
    while (we < lineEnd && !isSpace(we)) we++
    return plain.substring(ws, we).trim().ifEmpty { null }
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
    modifier: Modifier = Modifier,
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
    // Same guard as the reader's: the request can outrun its node's
    // attachment, and an unhandled throw here takes the whole screen down.
    LaunchedEffect(chapter.slug) { runCatching { keyFocus.requestFocus() } }

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
                                        size.height.toFloat(),
                                        MENU_BAND_HEIGHT.toPx(),
                                    )
                                ) {
                                    // No menu on a prompt, so the band is
                                    // inert here rather than doing something
                                    // different from the reader.
                                    TapZone.MENU -> Unit
                                    TapZone.PREV -> onOpenPrevChapter?.invoke()
                                    TapZone.NEXT -> onOpenNextChapter?.invoke()
                                    TapZone.NONE -> Unit
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

/**
 * Writes a chapter and everything that hangs off it — memory, figures,
 * badges.
 *
 * [isRewrite] replaces a chapter that already exists because the reader
 * rejected it. The order below matters: nothing of theirs is destroyed until
 * a replacement chapter is actually in hand, because a rewrite that fails at
 * the API must leave the book exactly as it was.
 */
private fun compileChapter(
    course: Course,
    chapterN: Int,
    scope: kotlinx.coroutines.CoroutineScope,
    store: com.inkfirst.core.CourseStore,
    apiKeyStore: ApiKeyStore,
    onState: (CompileState) -> Unit,
    onDone: (Course?, String?) -> Unit,
    isRewrite: Boolean = false,
    rewriteFeedback: String = "",
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
                // On a rewrite, hide this chapter's own memory block from
                // the model: it summarises the draft being thrown away, and
                // reading it back is the surest way to get the same chapter
                // again.
                val memory = store.readMemory(course.slug).let {
                    if (isRewrite) CourseMemory.withoutChapter(it, chapterN) else it
                }
                // Record how the already-read chapters went before writing
                // the next one. Compile time is the natural moment: it is
                // "feed forward only" by construction, needs no new trigger,
                // and the pass is idempotent — appendReaderNotes refuses a
                // chapter it has already recorded, so running it over the
                // whole course every compile costs nothing and cannot miss a
                // chapter that was read while the app was closed.
                recordReaderNotes(store, course, upTo = chapterN - 1)
                val readerNotes = store.readReaderNotes(course.slug)
                val markdown = ChapterGenerator(client, model)
                    .generate(
                        course = course,
                        n = chapterN,
                        memory = memory,
                        apiKey = apiKey,
                        readerNotes = readerNotes,
                        isRewrite = isRewrite,
                        rewriteFeedback = rewriteFeedback,
                    )
                val chapter = course.chapters.first { it.n == chapterN }
                // Only now, with the new chapter in hand, throw away what was
                // anchored into the old one.
                if (isRewrite) {
                    // The old body is filed beside the new one rather than
                    // overwritten. Nothing in the app reads it back; it is
                    // there so a chapter someone annotated is still on disk.
                    store.archiveChapter(course.slug, chapter.slug)
                    store.clearChapterMarks(course.slug, chapter.slug)
                    // The saved reading position is a block index into prose
                    // that no longer exists.
                    store.updateChapterProgress(course.slug, chapterN, 0)
                }
                store.saveChapter(course.slug, chapter.slug, markdown)
                val updated = store.updateChapterStatus(course.slug, chapterN, ChapterStatus.GENERATED)
                try {
                    val summary = MemoryUpdater(client, model).summarize(course, chapterN, markdown, apiKey)
                    // Replace rather than append on a rewrite: memory.md is
                    // read whole by every later chapter, so leaving the old
                    // summary in it teaches the book prose nobody will read.
                    if (isRewrite) {
                        store.replaceMemory(course.slug, chapterN, chapter.title, summary)
                    } else {
                        store.appendMemory(course.slug, chapterN, chapter.title, summary)
                    }
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

/**
 * Writes reader.md sections for every chapter up to [upTo] that has been read
 * and left a trace — see docs/design.md §13.
 *
 * Assembled from what the reader actually did (questions asked, passages
 * marked, notes written), so it costs no API call and states nothing it
 * cannot show. Best-effort by design: this is a nicety attached to compiling
 * a chapter, and it must never be the reason a compile fails.
 */
private fun recordReaderNotes(store: CourseStore, course: Course, upTo: Int) {
    if (upTo < 1) return
    for (n in 1..upTo) {
        val chapter = course.chapters.firstOrNull { it.n == n } ?: continue
        try {
            val body = ReaderSignals.notesForChapter(
                chapterSlug = chapter.slug,
                annotations = course.annotations,
                qaMarkdown = store.readQa(course.slug, chapter.slug),
            ) ?: continue
            store.appendReaderNotes(course.slug, n, chapter.title, body)
        } catch (e: Exception) {
            System.err.println("reader notes failed for chapter $n: ${e.message}")
        }
    }
}

/**
 * The confirm step before a chapter is thrown away and written again.
 *
 * It asks for a reason, because that reason is the whole difference between
 * this and a blind re-roll — the model cannot see the draft being replaced,
 * so what the reader says about it is the only thing steering the new one.
 * Leaving it empty is allowed and means "just try again", which is a fair
 * request of a model that is stochastic anyway.
 */
@Composable
private fun RewriteChapterDialog(
    chapterTitle: String,
    marksLost: String,
    laterChapters: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var feedback by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rewrite “$chapterTitle”") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "The chapter is written again from its objectives, and this " +
                        "chapter starts over: everything you did in it goes with the " +
                        "old text.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                if (marksLost.isNotEmpty()) {
                    // Everything here points INTO the prose being replaced —
                    // a mark at a block index, a question quoting a sentence.
                    // Naming it, and counting it, is what makes this a
                    // decision rather than a surprise.
                    Text(
                        text = "That means losing $marksLost.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
                Text(
                    text = "The old chapter and its questions stay on disk as files, " +
                        "and the chapter's pictures are kept.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MutedColor,
                )
                if (laterChapters.isNotEmpty()) {
                    // Not blocked, just said out loud: cascading the rewrite
                    // forward would spend real money on chapters the reader
                    // has not complained about.
                    val list = laterChapters.joinToString(", ")
                    Text(
                        text = "Chapter${if (laterChapters.size == 1) "" else "s"} $list " +
                            "already exist and were written against the old version of " +
                            "this one. They are left alone.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MutedColor,
                    )
                }
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                    placeholder = {
                        Text(
                            text = "What didn't work? (optional)",
                            fontFamily = FontFamily.Serif,
                            fontSize = 14.sp,
                            color = MutedColor,
                        )
                    },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = InkColor,
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = PaperColor,
                        unfocusedContainerColor = PaperColor,
                        cursorColor = InkColor,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(feedback) }) { Text("Rewrite") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep it") }
        },
    )
}

/**
 * A plain-English inventory of what one chapter's rewrite destroys, for the
 * confirm dialog. Empty when the chapter carries no marks, so a chapter that
 * was skimmed and disliked asks no scary question.
 */
private fun describeChapterMarks(
    store: com.inkfirst.core.CourseStore,
    course: Course,
    chapterSlug: String,
): String {
    val mine = course.annotations.filter { it.chapterSlug == chapterSlug }
    val marked = mine.count { it.kind == "ask" }
    val notes = mine.size - marked
    val bookmarks = course.bookmarks.count { it.chapterSlug == chapterSlug }
    val hasInk = store.readStrokes(course.slug, chapterSlug).isNotEmpty()
    // Counted from the transcript rather than from the markers, because the
    // two disagree: a passage can be marked and never asked about, and one
    // passage asked about twice carries a single marker.
    val answered = QaTranscript.parse(store.readQa(course.slug, chapterSlug)).size
    val parts = buildList {
        if (notes > 0) add(if (notes == 1) "1 note" else "$notes notes")
        if (answered > 0) {
            add(if (answered == 1) "1 answered question" else "$answered answered questions")
        }
        // A passage marked with Ask where no question was ever typed. Worth
        // counting separately: it is the only trace of a place they stopped.
        val unasked = (marked - answered).coerceAtLeast(0)
        if (unasked > 0) {
            add(if (unasked == 1) "1 marked passage" else "$unasked marked passages")
        }
        if (bookmarks > 0) add(if (bookmarks == 1) "1 bookmark" else "$bookmarks bookmarks")
        if (hasInk) add("the handwriting on it")
    }
    return when (parts.size) {
        0 -> ""
        1 -> parts.first()
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }
}
