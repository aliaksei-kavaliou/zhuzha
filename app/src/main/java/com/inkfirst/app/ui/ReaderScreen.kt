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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.res.stringResource
import com.inkfirst.app.R
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import android.graphics.Rect as AndroidRect
import android.app.Activity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.inkfirst.core.UiLabels
import com.inkfirst.core.CourseStore
import com.inkfirst.core.FIGURE_ASPECT
import com.inkfirst.core.figurePolicy
import com.inkfirst.core.pictureCost
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
import com.inkfirst.core.TopicChecker
import com.inkfirst.core.TopicVerdict
import com.inkfirst.core.parseMarkdown
import com.inkfirst.core.splitExerciseBody
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

// E-ink can only render pure black + pure white directly; anything between
// gets dithered (visible dot pattern). Our warm off-white paper looked nice on
// screens but rendered as noise on the Boox. Pure black on pure white is the
// canonical e-ink palette.
/**
 * Which ink path the reader mounts — see [PenSupport], which owns the
 * decision now. It used to be a constant here, flipped and reinstalled by
 * hand, because the only device that mattered was the Boox on the desk.
 */

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
/**
 * How tall one ruled line of answer space is. Sized for HANDWRITING rather
 * than for type — a pen needs more room than a printed line of the same text,
 * and a rule you cannot comfortably write between is decoration.
 */
internal val ANSWER_RULE_SPACING = 30.dp

/** Rows of dot grid a making-task gets. See [answerSpaceLineCount]. */
internal const val GRID_ROWS = 8

/**
 * How much working room an exercise offers, in rows of [ANSWER_RULE_SPACING].
 *
 * TWO MEASURES, BECAUSE THERE ARE TWO KINDS OF WORK. Ruled lines are for a
 * WRITTEN answer, so the answer the book is holding is exactly the right
 * measure — something paper cannot do, since a printed workbook has to guess
 * how many lines to rule and this one knows. Clamped at both ends: fewer than
 * two does not read as somewhere to write, and past six the page is mostly
 * rules, at which point the exercise has stopped being a question and become a
 * form.
 *
 * A GRID IS SIZED FROM THE TASK INSTEAD, because the answer's length says
 * nothing about the room a drawing needs — a mug takes more page than a
 * sentence about a mug does. It used to be sized from the answer whatever the
 * treatment, which was deliberate at the time (inverting the default changed
 * how the space LOOKED in existing books without changing how much of it there
 * was) and wrong as a rule. Seen on the Boox 2026-10-01: a reader drew eleven
 * circles across a grid sized by a 180-character sentence, and the revealed
 * answer printed straight over the bottom row of them.
 *
 * This is deliberately generous and the page is allowed to take it back —
 * pagination shrinks the room when a box will not otherwise fit, which is what
 * makes being generous here safe.
 */
internal fun answerSpaceLineCount(answer: String, ruled: Boolean = false): Int {
    if (!ruled) return GRID_ROWS
    val text = answer.trim()
    if (text.isNotEmpty()) return (text.length / 42 + 1).coerceIn(2, 6)
    // Writing lines with nothing to write toward are noise, so they get none.
    return 0
}

internal const val PLATE_WIDTH_FRACTION = 0.72f

// Inline `code` is marked by the monospace face alone — no background. A tint
// behind the words was tried in both a dithered and a flat form and read as
// ugly either way on the panel; against a serif body, the switch to mono is
// already an unmistakable signal, and it keeps the page pure black on white.

private sealed interface CompileState {
    data object Idle : CompileState
    data object Running : CompileState
    /**
     * Words are written and saved; pictures are still being drawn.
     *
     * [what] names the stage — "figures", "badges", "the chapter picture".
     * Without it the footer said "Drawing pictures… 2/3" for four different
     * passes in a row, which is why a reader who had already been given
     * figures and badges went to the menu and asked for them again
     * (2026-09-21). Knowing something is happening is not the same as
     * knowing WHAT.
     */
    data class Illustrating(val done: Int, val total: Int, val what: String = "pictures") : CompileState
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
    // The book's own words, re-read when the book (and so the language) changes.
    val labels = rememberBookLabels(store, course)
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
    // Which pane of the menu is open. Reset when the menu closes, so it
    // always opens on reading.
    var menuPane by remember { mutableStateOf(MenuPane.READING) }
    // Which picture the reader has asked to redraw, held until they confirm:
    // false is the opening picture, true the closing one, null neither.
    var confirmingRedraw by remember { mutableStateOf<Boolean?>(null) }
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
    // Which already-drawn figure to replace. A picker rather than a confirm,
    // because a chapter has several and they are told apart by their briefs.
    var pickingRedraw by remember(chapterSlug) { mutableStateOf(false) }

    LaunchedEffect(courseSlug, chapterSlug) {
        course = store.load(courseSlug)
        markdown = store.readChapter(courseSlug, chapterSlug)
        strokes = store.readStrokes(courseSlug, chapterSlug)
        // Record where we are so a cold start of the app resumes here.
        store.writeLastRead(courseSlug, chapterSlug)
    }

    val chapter = course?.chapters?.firstOrNull { it.slug == chapterSlug }
    val chapterAnnotations = course?.annotations?.filter { it.chapterSlug == chapterSlug }.orEmpty()

    // How many slots the chapter has at all, so a finished pass can say "all
    // 3 drawn" instead of vanishing and leaving the reader to guess.
    val figureSlots = remember(markdown) {
        markdown?.let { md -> findFigureSlots(md).size } ?: 0
    }
    // Progress (or the failure) of a manual illustrate pass, shown in the
    // menu row itself. With the chapter already on screen there is no
    // compile prompt to report into, and drawing takes long enough that
    // silence reads as "nothing happened".
    // Badges are course-level, so unlike figures this counts once for the
    // whole book. pictureVersion invalidates every decoded-picture cache and
    // the page pack after anything on disk changes — a badge makes a block
    // label taller, so pagination has to be recomputed, not just repainted.
    //
    // It was called badgeVersion, which is how figures came to be left out of
    // it: it already gated covers, closing pictures and the page pack, but
    // the name said badges, so nobody thought to ask whether FIGURES needed
    // it too. They did (2026-09-21) — see the figure cache below.
    var pictureVersion by remember { mutableStateOf(0) }

    // Figure slots with no picture ON DISK. Drives the menu's "Draw figures"
    // row — the retrofit path for chapters written before figures existed,
    // and the retry path when one came back empty.
    //
    // A slot counts as outstanding when it has no `src=` OR when the file
    // that `src=` names is not there. Those two used to disagree: pending-ness
    // was read from the Markdown while RENDERING was read from the file, so
    // deleting a picture left the menu insisting everything was drawn while
    // the page showed alt text, with no way back. Asking the same question
    // both places is the fix, and it is also what makes the draw row honest —
    // the ordinals below are handed to `illustrate` so the row draws exactly
    // what it counted.
    val missingPictures = remember(markdown, course?.slug, pictureVersion) {
        val slug = course?.slug
        markdown?.let { md ->
            findFigureSlots(md)
                .filter { it.brief.isNotBlank() }
                .filter { slot ->
                    val src = slot.src
                    src == null || slug == null || store.figureFile(slug, src) == null
                }
                .map { it.ordinal }
                .toSet()
        }.orEmpty()
    }
    val pendingFigures = missingPictures.size
    // What this chapter actually needs — a chapter with code blocks earns a
    // code badge the first time it is opened, so the set grows with the book
    // instead of being fixed up front.
    val neededBadges = remember(markdown) {
        markdown?.let { badgeKindsIn(it) }.orEmpty().ifEmpty { BADGE_KINDS }
    }
    val hasCover = remember(course?.slug, chapterSlug, pictureVersion) {
        course?.slug?.let { store.coverFile(it, chapterSlug) != null } == true
    }
    val hasTail = remember(course?.slug, chapterSlug, pictureVersion) {
        course?.slug?.let { store.tailFile(it, chapterSlug) != null } == true
    }
    // The closing picture is a rewrite of the cover's brief, so redrawing the
    // cover leaves it paying off a scene that is no longer there — the pair
    // stops rhyming, which is the only reason the pair exists. Read off the
    // files rather than stored: drawn together the cover is always written
    // first, so a tail OLDER than its cover can only mean the cover was
    // redrawn since.
    val tailStale = remember(course?.slug, chapterSlug, pictureVersion) {
        val slug = course?.slug ?: return@remember false
        val cover = store.coverFile(slug, chapterSlug) ?: return@remember false
        val tail = store.tailFile(slug, chapterSlug) ?: return@remember false
        tail.lastModified() < cover.lastModified()
    }
    val pendingBadges = remember(course?.slug, pictureVersion, neededBadges) {
        val have = course?.slug?.let { store.badgeKinds(it) }.orEmpty()
        neededBadges.count { it !in have }
    }
    val figureStatus = when (val state = compileState) {
        is CompileState.Illustrating -> "Drawing… ${state.done}/${state.total}"
        is CompileState.Failed -> state.message
        else -> null
    }
    // The same state said out loud on the page itself. A rewrite replaces a
    // chapter the reader is looking at, and until it lands the page is
    // unchanged — so without this, tapping Rewrite does nothing visible for
    // the best part of a minute and the only honest reading is that it
    // failed. It goes in the footer because that is the one strip of the
    // page that is already chrome, and it is a LABEL rather than a spinner:
    // an animation on e-ink is a repaint every frame.
    // What is still missing, said once nothing is running. A picture pass
    // that has finished leaves no trace on a page you have not scrolled to,
    // so "is it done, or did my tap miss?" had no answer anywhere — and the
    // menu hides a row the moment its work completes, which makes absence
    // mean "done" and "not started" at the same time. Naming the leftovers
    // is what stops a reader drawing the same picture twice.
    //
    // Null once everything exists: a finished book page should be a book
    // page, not a status board.
    // Resolved OUTSIDE remember: stringResource is @Composable and a remember
    // block is not a composable scope.
    val figuresWord = stringResource(R.string.figures_count, pendingFigures)
    val badgesWord = stringResource(R.string.badges_count, pendingBadges)
    val coverWord = stringResource(R.string.chapter_picture)
    val closingWord = stringResource(R.string.closing_picture)
    val outstandingWords = remember(
        pendingFigures, hasCover, hasTail, pendingBadges,
        figuresWord, badgesWord, coverWord, closingWord,
    ) {
        buildList {
            if (pendingFigures > 0) add(figuresWord)
            if (!hasCover) add(coverWord)
            // The closing picture is the cover resolved, so it is not
            // outstanding until there is a cover for it to pay off.
            if (hasCover && !hasTail) add(closingWord)
            if (pendingBadges > 0) add(badgesWord)
        }
    }
    val picturesOutstanding =
        if (outstandingWords.isEmpty()) null
        else stringResource(R.string.still_to_draw, outstandingWords.joinToString(" · "))
    val readerStatus = when (val state = compileState) {
        CompileState.Running -> "Rewriting this chapter…"
        is CompileState.Illustrating ->
            // A one-shot pass has no meaningful count — "the chapter picture
            // 0/1" reads worse than saying nothing about progress at all.
            if (state.total <= 1) "Drawing ${state.what}…"
            else "Drawing ${state.what}… ${state.done}/${state.total}"
        is CompileState.Failed -> state.message
        CompileState.Idle -> picturesOutstanding
    }
    // Anything that spends an API call is off while one is in flight. Failed
    // is deliberately not busy — the way to recover is to try again.
    val compileBusy = compileState is CompileState.Running ||
        compileState is CompileState.Illustrating
    val blocks = remember(markdown, chapter, course) {
        val parsed = markdown?.let { parseMarkdown(it) } ?: emptyList()
        if (chapter == null || parsed.isEmpty()) parsed
        else {
            // Small italic intro line — shows est-minutes + day range + status
            // once at the start of the chapter so the TOC can stay clean.
            val dayLabel = course?.let { c ->
                courseDaySchedule(c).firstOrNull { it.chapterN == chapter.n }?.labelIn(labels)
            }
            // "Chapter N" leads the byline so every chapter is numbered the
            // same way, whatever the model wrote. Some chapters opened with
            // "# Chapter 5: …" and others with a bare title, because the
            // number only ever existed when the model happened to type it.
            val metaBody = buildString {
                append(labels.t("Chapter {n}", "n" to chapter.n))
                append(" · ")
                append(labels.t("~{n} min", "n" to chapter.estMinutes))
                if (!dayLabel.isNullOrEmpty()) append(" · $dayLabel")
                append(" · ${statusLabel(chapter.status, labels)}")
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
            // A chapter opens with its own title if its first block is a
            // heading at title depth. LEVEL 1 OR 2, not level 1 alone: the
            // model writes the title as "##" often enough, and reading that
            // as "no title here" made the app inject one from the outline and
            // then render the model's heading underneath it — the same
            // chapter titled twice, the second time carrying "Chapter 6:".
            // Level 3 and deeper are section headings; a chapter that opens
            // straight into one genuinely has no title of its own.
            val openingHeading = (parsed.firstOrNull() as? Block.Heading)
                ?.takeIf { it.level <= 2 }
            val opensWithTitle = openingHeading != null
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
            val rest = if (openingHeading != null) {
                // Tag the model's own heading rather than adding a block, so
                // every annotation and ink anchor keeps its index. Normalised
                // to level 1 while we are here: whether a chapter title is
                // set in title type or section type should not depend on how
                // many hashes the model happened to type.
                parsed.mapIndexed { i, b ->
                    if (i == 0 && b is Block.Heading) {
                        b.copy(
                            level = 1,
                            inlines = stripChapterNumber(b.inlines),
                            coverSrc = coverName,
                        )
                    } else {
                        b
                    }
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

    // Everything from here down is the BOOK, so it speaks the book's
    // language — the header, the page numbers, the menu over the page. The
    // Library and Settings that got here are not part of this and stay in
    // the reader's own language. See [LocalLabels].
    // Reported, not decided: MainActivity owns the bars (see ReaderChrome).
    // Cleared on the way out so a menu left open when you leave the chapter
    // cannot strand the shelf without its status bar.
    DisposableEffect(showFormatMenu) {
        ReaderChrome.menuOpen = showFormatMenu
        onDispose { ReaderChrome.menuOpen = false }
    }

    CompositionLocalProvider(LocalLabels provides labels) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper),
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
                    courseDaySchedule(c).firstOrNull { it.chapterN == chapter!!.n }?.labelIn(labels)
                }
                CompilePrompt(
                        chapter = chapter!!,
                    horizontalPadding = horizontalPadding,
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
                                    // The compile drew this chapter's cover,
                                    // its closing picture and the book's
                                    // badges, and everything that ASKS
                                    // whether those exist is keyed on
                                    // pictureVersion. The page is keyed on
                                    // markdown instead, so it re-reads the
                                    // disk and shows them — which is how the
                                    // two came to disagree: pictures on the
                                    // page, a footer still saying "still to
                                    // draw: chapter picture", and a menu
                                    // offering to draw one that was already
                                    // there, at full price. Until covers were
                                    // drawn at compile time this path had
                                    // nothing stale to invalidate.
                                    pictureVersion++
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
                    labels = labels,
                    fontScale = fontScale,
                    lineSpacing = lineSpacing,
                    textAlign = textAlign,
                    horizontalPadding = horizontalPadding,
                    blocks = blocks,
                    annotations = chapterAnnotations,
                    course = course,
                    chapterSlug = chapterSlug,
                    pictureVersion = pictureVersion,
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
                    status = readerStatus,
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
                    onToggleMenu = {
                        showFormatMenu = !showFormatMenu
                        if (!showFormatMenu) menuPane = MenuPane.READING
                    },
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
                    .background(Ink, RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                TextButton(onClick = { askToolbar.ask() }) {
                    Text(stringResource(R.string.ask_about_this), color = Paper)
                }
                TextButton(onClick = { askToolbar.addNote() }) {
                    Text(stringResource(R.string.add_note), color = Paper)
                }
                TextButton(onClick = clearSelection) {
                    Text(stringResource(R.string.cancel), color = Paper)
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
                    .background(Ink, RoundedCornerShape(24.dp))
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
                    Text(stringResource(R.string.ask_about_this), color = Paper)
                }
                TextButton(onClick = {
                    pendingNoteQuote = text
                    // Held, not removed yet: the note does not exist until it
                    // is saved, and cancelling the overlay must not cost the
                    // reader the mark they drew.
                    pendingNoteStroke = stroke
                    pendingGestureStroke = null
                }) {
                    Text(stringResource(R.string.add_note), color = Paper)
                }
                TextButton(onClick = {
                    val updated = strokes - stroke
                    strokes = updated
                    store.saveStrokes(courseSlug, chapterSlug, updated)
                    pendingGestureStroke = null
                }) {
                    Text(stringResource(R.string.delete), color = Paper)
                }
                TextButton(onClick = { pendingGestureStroke = null }) {
                    Text(stringResource(R.string.cancel), color = Paper)
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
                    .background(Ink, RoundedCornerShape(24.dp))
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
                        Text(stringResource(R.string.ask_about_this), color = Paper)
                    }
                    TextButton(onClick = {
                        pendingNoteQuote = blockText
                        pendingNoteStroke = stroke
                        viewingStroke = null
                    }) {
                        Text(stringResource(R.string.add_note), color = Paper)
                    }
                }
                TextButton(onClick = {
                    val updated = strokes - stroke
                    strokes = updated
                    store.saveStrokes(courseSlug, chapterSlug, updated)
                    viewingStroke = null
                }) {
                    Text(stringResource(R.string.delete), color = Paper)
                }
                TextButton(onClick = { viewingStroke = null }) {
                    Text(stringResource(R.string.cancel), color = Paper)
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

        if (pickingRedraw) {
            val c = course
            val ch = chapter
            val md = markdown
            if (c == null || ch == null || md == null) {
                pickingRedraw = false
            } else {
                InkChoiceDialog(
                    title = stringResource(R.string.which_picture),
                    onDismiss = { pickingRedraw = false },
                    dismissLabel = stringResource(R.string.cancel),
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        findFigureSlots(md).filter { it.brief.isNotBlank() }.forEach { slot ->
                            TextButton(
                                onClick = {
                                    pickingRedraw = false
                                    illustrateExistingChapter(
                                        course = c,
                                        chapter = ch,
                                        markdown = md,
                                        scope = scope,
                                        store = store,
                                        apiKeyStore = apiKeyStore,
                                        onState = { compileState = it },
                                        onDone = { newMarkdown ->
                                            markdown = newMarkdown
                                            // The file changed under the same
                                            // name, so every cache keyed on
                                            // that name is now stale.
                                            pictureVersion++
                                        },
                                        only = setOf(slot.ordinal),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "${slot.ordinal}. ${slot.brief.take(70)}" +
                                        if (slot.brief.length > 70) "…" else "",
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = Ink,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        confirmingRedraw?.let { closing ->
            // A confirm, because this spends money and throws away a picture
            // that cannot be got back — the model does not draw the same one
            // twice. Same shape as the delete confirms elsewhere: name what
            // goes, and what it costs.
            val c = course
            val ch = chapter
            if (c == null || ch == null) {
                confirmingRedraw = null
            } else {
                InkDialog(
                    title = stringResource(R.string.redraw_this_picture),
                    onDismiss = { confirmingRedraw = null },
                    confirmLabel = stringResource(R.string.redraw) + " · ${pictureCost(1)}",
                    onConfirm = {
                        confirmingRedraw = null
                        store.deleteChapterPicture(c.slug, ch.slug, closing = closing)
                        pictureVersion++
                        if (closing) {
                            drawChapterTail(
                                course = c,
                                chapter = ch,
                                scope = scope,
                                store = store,
                                apiKeyStore = apiKeyStore,
                                onState = { compileState = it },
                                onDone = { pictureVersion++ },
                            )
                        } else {
                            drawChapterCover(
                                course = c,
                                chapter = ch,
                                markdown = markdown.orEmpty(),
                                scope = scope,
                                store = store,
                                apiKeyStore = apiKeyStore,
                                onState = { compileState = it },
                                onDone = { pictureVersion++ },
                            )
                        }
                    },
                    dismissLabel = stringResource(R.string.cancel),
                ) {
                    Text(
                        text = stringResource(R.string.the_one_you_have_is_replaced_this_cannot_be_undone),
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
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
                    // The inline slots this chapter has, plus its own two
                    // pictures. An estimate: the rewritten chapter may ask for
                    // a different number of figures, which is why the price
                    // carries a "~".
                    picturesRedrawn =
                        if (c.figurePolicy.automatic) figureSlots + 2 else 0,
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
                                    // Same reason the ink is dropped above:
                                    // the figures on disk were replaced, and
                                    // they kept their filenames, so every
                                    // decoded-picture cache is now holding
                                    // the previous chapter's art.
                                    pictureVersion++
                                }
                            },
                        )
                    },
                )
            }
        }

        if (showFormatMenu && menuPane == MenuPane.READING) {
            FormatMenu(
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
                figureStatus = figureStatus,
                onOpenTypeTools = { menuPane = MenuPane.TYPE },
                onOpenChapterTools = { menuPane = MenuPane.CHAPTER },
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
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (showFormatMenu && menuPane == MenuPane.TYPE) {
            TypeToolsMenu(
                fontScale = fontScale,
                lineSpacing = lineSpacing,
                textAlign = textAlign,
                horizontalPadding = horizontalPadding,
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
                onBack = { menuPane = MenuPane.READING },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (showFormatMenu && menuPane == MenuPane.CHAPTER) {
            ChapterToolsMenu(
                onRewriteChapter = {
                    showFormatMenu = false
                    confirmingRewrite = true
                },
                onPickRedraw = {
                    showFormatMenu = false
                    pickingRedraw = true
                },
                onIllustrate = {
                    // Menu deliberately stays open — it is where progress shows.
                    val c = course ?: return@ChapterToolsMenu
                    val ch = chapter ?: return@ChapterToolsMenu
                    val md = markdown ?: return@ChapterToolsMenu
                    illustrateExistingChapter(
                        course = c,
                        chapter = ch,
                        markdown = md,
                        scope = scope,
                        store = store,
                        apiKeyStore = apiKeyStore,
                        onState = { compileState = it },
                        onDone = { newMarkdown ->
                            markdown = newMarkdown
                            pictureVersion++
                        },
                        // Exactly the slots the row counted. Without this a
                        // slot whose FILE went missing would be counted and
                        // then skipped, because core's own `isPending` can
                        // only see the Markdown.
                        only = missingPictures,
                    )
                },
                onDrawCover = {
                    val c = course ?: return@ChapterToolsMenu
                    val ch = chapter ?: return@ChapterToolsMenu
                    drawChapterCover(
                        course = c,
                        chapter = ch,
                        markdown = markdown.orEmpty(),
                        scope = scope,
                        store = store,
                        apiKeyStore = apiKeyStore,
                        onState = { compileState = it },
                        onDone = { pictureVersion++ },
                    )
                },
                onDrawTail = {
                    val c = course ?: return@ChapterToolsMenu
                    val ch = chapter ?: return@ChapterToolsMenu
                    drawChapterTail(
                        course = c,
                        chapter = ch,
                        scope = scope,
                        store = store,
                        apiKeyStore = apiKeyStore,
                        onState = { compileState = it },
                        onDone = { pictureVersion++ },
                    )
                },
                onDrawBadges = {
                    val c = course ?: return@ChapterToolsMenu
                    drawCourseBadges(
                        course = c,
                        kinds = neededBadges,
                        scope = scope,
                        store = store,
                        apiKeyStore = apiKeyStore,
                        onState = { compileState = it },
                        onDone = { pictureVersion++ },
                    )
                },
                pendingFigures = pendingFigures,
                figureSlots = figureSlots,
                figureStatus = figureStatus,
                busyDrawing = compileBusy,
                pendingBadges = pendingBadges,
                hasCover = hasCover,
                hasTail = hasTail,
                tailStale = tailStale,
                onRedrawCover = { confirmingRedraw = false },
                onRedrawTail = { confirmingRedraw = true },
                onBack = { menuPane = MenuPane.READING },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
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
    pictureVersion: Int,
    /** The book's own words, for the labels printed on exercise and recap blocks. */
    labels: UiLabels,
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
    /** A line of chrome for the footer — what the app is doing, or null. */
    status: String?,
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
    // bitmaps. Entries are filled during composition, and the cache is thrown
    // away whenever [pictureVersion] moves.
    //
    // It used to be keyed on the chapter alone, with a note saying a given
    // src never changes. A REWRITE breaks that: it regenerates a chapter's
    // figures under the same names — they are <chapter-slug>-<ordinal> — so
    // the bytes change while every key stays identical. The reported symptom
    // was that a redrawn chapter still showed the previous illustration; the
    // new one was on disk the whole time, correctly drawn.
    val figureBitmaps = remember(course?.slug, chapterSlug, pictureVersion) {
        mutableMapOf<String, ImageBitmap?>()
    }
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
    val badgeBitmaps = remember(course?.slug, pictureVersion) { mutableMapOf<String, ImageBitmap?>() }
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

            // The answer's label is typeset into the page, so it is the
            // book's word, not the app's — and the paginator measures the
            // revealed answer, so it needs the same one. In the remember key
            // because a book whose labels arrive late must repaginate.
            val answerPrefix = labels.t("Answer:")
            // Declared here rather than beside the renderer because
            // PAGINATION reads it now: a block with its answer open measures
            // to the answer, one with it closed measures to the button.
            val revealed = remember(blocks) { mutableStateMapOf<Int, Boolean>() }
            /** Block whose answer was just opened; the page follows it. */
            var revealTarget by remember(blocks) { mutableIntStateOf(-1) }
            // Opening an answer REPAGINATES. Read as a sorted key rather than
            // passed as a map, so this composition actually subscribes to the
            // snapshot and the key changes with it — a lambda closing over
            // the map would read the right values and never re-run.
            val revealedKey = revealed.filterValues { it }.keys.sorted().joinToString(",")
            val pages = remember(
                blocks, widthPx, availableHeightPx, fontScale, lineSpacing, pictureVersion,
                answerPrefix, revealedKey,
            ) {
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
                    answerPrefix = answerPrefix,
                    isRevealed = { idx -> revealed[idx] == true },
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
            // REVEALING MUST NOT MOVE THE READER. Opening an answer
            // repaginates, `pageIndex` is remembered against `pages`, so it
            // is re-derived from the anchor — and the anchor is the page's
            // FIRST block, which after the reflow may well sit on another
            // page. Reported: "show answer on page 14 opens answer but
            // changes the page."
            //
            // So the reveal names its own target and the page follows it.
            // indexOfLast, not indexOf: a split block appears on two pages
            // and the answer is always on the last piece — the one that
            // carries the reveal.
            // REVEALING MUST NOT MOVE THE READER. Opening an answer
            // repaginates, and `pageIndex` is remembered against `pages`, so
            // it is re-derived from the anchor — which is the page's FIRST
            // block, and after the reflow that may sit elsewhere. Reported:
            // "show answer on page 14 opens answer but changes the page."
            //
            // Keyed on pageIndex as well as pages, and it does NOT clear the
            // target until the page it asked for is the page being shown.
            // One shot was not enough: the index is re-initialised again on
            // the next rebuild, and the correction has to outlive that.
            //
            // The target is the piece that CARRIES the answer, not the block:
            // a split exercise puts its task on one page and its reveal on
            // another and both pieces share the block's index, so following
            // the index alone lands on the task — which is where the reader
            // already was, one page back.
            LaunchedEffect(pages, pageIndex, revealTarget) {
                val target = revealTarget
                if (target < 0) return@LaunchedEffect
                val p = pages.indexOfFirst { page ->
                    page.any {
                        it.index == target &&
                            (it.block as? Block.CustomFence)?.params?.get(FENCE_CONT_TO) == null
                    }
                }
                when {
                    p < 0 -> revealTarget = -1
                    pageIndex != p -> {
                        pageIndex = p
                        // Agree with the anchor machinery rather than fight
                        // it: block `target` is on this page, so its own
                        // resync sees nothing to do.
                        onAnchorChange(target)
                    }
                    else -> revealTarget = -1
                }
            }
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
                    is TapAction.PressButton -> {
                        revealed[action.blockIndex] = true
                        revealTarget = action.blockIndex
                    }
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
                                // A child that consumed ANY part of this gesture owns the
                                // whole gesture. Checking only the final change was enough
                                // while every child was a tap target — a tap is one event,
                                // so consumed-on-up said it all. A SCROLLING child breaks
                                // that: `verticalScroll` consumes the drag deltas as they
                                // arrive and has nothing left to consume on the release, so
                                // scrolling the menu arrived here as a bare swipe and
                                // resolved to CloseMenu. The menu closed under the finger
                                // instead of scrolling, which is why it could not simply be
                                // given a scroll container.
                                var consumedByChild = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (change.isConsumed) consumedByChild = true
                                    if (!change.pressed) {
                                        // If a child composable (TextButton, SelectionContainer,
                                        // the menu's own scroll, etc.) consumed any of this,
                                        // don't page-turn on top of it. That was the "Show
                                        // answer button also flipped a page" bug.
                                        if (consumedByChild) break
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
                                        onReveal = {
                                            revealed[globalIdx] = true
                                            revealTarget = globalIdx
                                        },
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
                                        labels = labels,
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
                    // finger events through. PenSupport decides which path
                    // is mounted; the MotionEvent one works but lags.
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
                            // A mark in the room the box offered you to work
                            // in is an ANSWER, not a selection. Decided here
                            // rather than inside the extractors because it
                            // disqualifies the gesture outright, whichever
                            // shape it had — a circle and a vertical line in
                            // the grid are both the reader working.
                            val inWorkingRoom = anchor != null && strokeIsInWorkingRoom(
                                enriched, anchor, widthPx, measurer, density,
                                fontScale, lineSpacing, badgeHeightPx,
                            )
                            android.util.Log.d(
                                "InkGesture",
                                "stroke pts=${enriched.points.size} enclosing=$enclosing " +
                                    "vertical=$verticalMark " +
                                    "text=${enriched.annotatedText?.take(40)?.ifBlank { "<blank>" } ?: "<null>"} " +
                                    "workingRoom=$inWorkingRoom",
                            )
                            if ((enclosing || verticalMark) &&
                                !enriched.annotatedText.isNullOrBlank() &&
                                !inWorkingRoom
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
                    if (PenSupport.useRawPen) {
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
                        text = if (pages.isEmpty()) ""
                        else stringResource(R.string.page_n_of_total, pageIndex + 1, pages.size),
                        style = MaterialTheme.typography.bodySmall.copy(color = Muted),
                        modifier = Modifier.weight(1f),
                    )
                    // What the app is doing outranks the hint: the hint is
                    // there to teach a gesture, and being told a chapter is
                    // being rewritten matters more than that, for the minute
                    // it takes.
                    if (status != null) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall.copy(color = Ink),
                            textAlign = TextAlign.End,
                        )
                    } else if (showTapHint) {
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
                            style = MaterialTheme.typography.bodySmall.copy(color = Muted),
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
        // attached to it.
        //
        // 36dp, then 24dp, now 8dp — and the last cut is the one with a reason
        // rather than a measurement behind it. THIS GAP AND INK_TOP_MARGIN ARE
        // THE SAME GAP. Both are white space between the running head and the
        // first line of text; they were argued for separately (one to detach
        // the header, one to give the pen somewhere to start a stroke above
        // the first line) and so they stacked, 24 + 28, for a single visual
        // job. The ink margin is the one that cannot shrink, so it is the one
        // that does the separating, and this drops to a hairline of its own.
        //
        // Above the header is 4dp rather than 8dp for the same reason: with
        // the status bar hidden the header sits at the very edge of the glass,
        // where there is nothing above it to be separated from.
        // ONE STRIP, ONE OWNER AT A TIME. With the menu open the system bar
        // paints across this row — it has to, since reserving space for it
        // would push the page down and repaginate the chapter. Rather than
        // let the clock cut through the glyphs, the running head stands down
        // while the menu is up: you are operating the app, not reading, and
        // the menu carries its own way to Contents.
        //
        // alpha, not a conditional: the row keeps its height, so nothing below
        // it moves and the page count cannot change.
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (ReaderChrome.menuOpen) 0f else 1f)
            .padding(top = 4.dp, bottom = 8.dp),
    ) {
        // Explicit-destination nav rather than popBackStack — the auto-resume
        // cold-start pushes Reader directly on top of Library with no
        // Contents in between, so a back-pop would land on Library. The
        // button label says Contents; it should always go to Contents.
        // A plain target rather than a TextButton. M3 gives a TextButton a
        // 40dp minimum height and its own content padding, so a 20px label was
        // costing about twice that in chrome at the very top of every page —
        // an unrestyled Material default in an app whose rule is to restyle
        // every one of them. The padding here is the same shape the menu rows
        // use, so the header and the menus agree about what a tap target is.
        // THE PADDING MUST NOT MOVE THE TEXT. Measured on the Boox from a
        // screenshot after Aliaksei reported the bars looking wrong: the
        // header sat 53.9dp from the edge against the body's 40.5dp, because
        // this Box's 12dp of touch padding pushed the label inward while the
        // page's own margin was already applied by the parent. A running head
        // that does not line up with the text under it reads as a mistake,
        // and on a 7in page 13dp is visible.
        //
        // Offset it back out by exactly what the padding adds, rather than
        // dropping the padding: the tap target is the point of it. This is
        // strictly better than shrinking it, because the target now extends
        // 12dp INTO the margin, which is the easiest place to hit a header.
        Box(
            modifier = Modifier
                .offset(x = (-12).dp)
                .clickable { onOpenContents() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("≡ " + stringResource(R.string.contents), color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.weight(1f))
        if (chapterN != null && chapterCount != null) {
            Text(
                text = stringResource(R.string.ch_n_of_total, chapterN, chapterCount),
                style = MaterialTheme.typography.bodySmall.copy(color = Muted),
                // Only a SEPARATOR from the ribbon, so it exists only when the
                // ribbon does. Unconditional, it held the chapter counter 8dp
                // off the right margin the body text uses — the same
                // misalignment as the Contents label, mirrored.
                modifier = Modifier.padding(end = if (isBookmarked) 8.dp else 0.dp),
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
                // Same correction as the Contents label, mirrored: cancel the
                // button's own content padding so the ribbon sits on the
                // page's right margin instead of 8dp inside it.
                modifier = Modifier.offset(x = 8.dp),
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
                    drawPath(path, color = Ink)
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
/** Which pane of the reader's menu is showing. */
/**
 * How tall a menu pane is allowed to get, as a fraction of the screen.
 *
 * It has to leave page visible, because tapping the page is how the menu is
 * dismissed — [resolveTap] turns any tap into `CloseMenu` while one is open,
 * and a sheet that covered everything would swallow every one of them. The
 * root pane has no "back" row to escape through, so this is the only way out
 * of it.
 */
private const val MENU_MAX_HEIGHT_FRACTION = 0.62f

/**
 * The chrome every menu pane shares: a bordered sheet at the bottom of the
 * page that swallows taps, refuses to grow past [MENU_MAX_HEIGHT_FRACTION],
 * and SCROLLS whatever does not fit.
 *
 * Found 2026-09-22 on a 320x640 emulator and left open for six days because
 * the fix was known to be more than a scroll container: a `Column` sizes to
 * its content, so below ~700px everything after "Chapter" was simply off the
 * screen and unreachable — Rewrite, Figures, Opening, Closing, Badges. It was
 * invisible on the Boox, which is tall enough, and it is the fourth screen in
 * the class the 2026-09-21 audit thought it had closed.
 *
 * The cap and the scroll are ONE fix, not two. A scroll with no cap fills the
 * screen and takes the dismiss target with it; a cap with no scroll is the
 * bug again with a shorter sheet.
 *
 * The gesture half of it is in the page's own detector rather than here: with
 * a menu open a finger drag means `CloseMenu`, so scrolling this would have
 * turned into closing it. `verticalScroll` CONSUMES the drag, and the
 * detector now drops any gesture a child consumed — see the `consumedByChild`
 * flag there. That keeps the DECISION in TapRouting and leaves the detector
 * doing what its own doc says is its job: dealing with the consequences of
 * its transport.
 *
 * Three panes were three identical `Column` headers before this; sharing them
 * is also how the three stop drifting apart.
 */
@Composable
private fun MenuSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * MENU_MAX_HEIGHT_FRACTION).dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .background(Paper)
            .border(width = 1.dp, color = Hairline)
            // Consume taps so they don't bubble to the page underneath, where
            // an open menu turns any tap into CloseMenu.
            .pointerInput(Unit) { detectTapGestures { /* swallow */ } }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

private enum class MenuPane { READING, TYPE, CHAPTER }

@Composable
private fun FormatMenu(
    isBookmarked: Boolean,
    canBookmark: Boolean,
    onOpenContents: () -> Unit,
    onAskAboutChapter: () -> Unit,
    onOpenReaderNotes: () -> Unit,
    onSearch: () -> Unit,
    figureStatus: String?,
    onOpenChapterTools: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenTypeTools: () -> Unit,
    modifier: Modifier = Modifier,
) {

    MenuSheet(modifier = modifier) {
        // Contents jump
        MenuRow(label = stringResource(R.string.go_to)) {
            Box(
                modifier = Modifier
                    .clickable { onOpenContents() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.contents),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
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
                    text = stringResource(R.string.search),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
            }
        }

        // What the book has noticed about this reader, and the means to
        // cross a line out. Reachable from the page as well as from Contents
        // on purpose: the moment you want to argue with an observation is the
        // moment you are reading the chapter it shaped.
        MenuRow(label = stringResource(R.string.reading)) {
            Box(
                modifier = Modifier
                    .clickable { onOpenReaderNotes() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.how_it_has_gone),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
            }
        }

        // Ask about this chapter (general Q&A, no selection)
        MenuRow(label = stringResource(R.string.ask)) {
            Box(
                modifier = Modifier
                    .clickable { onAskAboutChapter() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.about_this_chapter),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
            }
        }

        // Bookmark toggle for the current page.
        MenuRow(label = stringResource(R.string.mark)) {
            Box(
                modifier = Modifier
                    .clickable(enabled = canBookmark) { onToggleBookmark() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (isBookmarked) stringResource(R.string.remove_bookmark) else stringResource(R.string.bookmark_this_page),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canBookmark) Ink else Muted,
                )
            }
        }

        MenuDivider()

        // Font size
        // How the page is set — size, spacing, alignment, margins — behind one
        // row. Four rows of options do not fit a 320dp screen beside anything
        // else: "Relaxed" and "Notebook" wrap to a second line each, and the
        // rows below them fall off the bottom. It costs a tap, which font
        // size can afford: it is a set-once decision in practice (design.md
        // §11), unlike Contents or Ask which are used on every page.
        MenuRow(label = stringResource(R.string.text)) {
            Box(
                modifier = Modifier
                    .clickable { onOpenTypeTools() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.size_spacing_margins),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
            }
        }

        MenuDivider()

        // Everything that PRODUCES the book — rewriting it, drawing its
        // pictures — lives in a panel of its own now. Those rows are used a
        // handful of times per chapter and are the only ones that spend
        // money; the rows above are used on every page. Thirteen rows in one
        // sheet also did not fit a 320x640 screen, and the third that fell
        // off the bottom was all of these.
        //
        // The row carries the drawing status when there is one, so progress
        // is visible from here without opening the panel to watch it.
        MenuRow(label = stringResource(R.string.chapter)) {
            Box(
                modifier = Modifier
                    .clickable { onOpenChapterTools() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = figureStatus ?: stringResource(R.string.pictures_and_rewrite),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
            }
        }
    }
}

/**
 * A hairline between groups of menu rows.
 *
 * The menu had grown to thirteen rows in one flat list, mixing "what do I
 * want to do" (contents, search, ask, bookmark) with "how should this look"
 * (font, spacing, align, margin) and with things touched twice a year
 * (rewrite). Every row carried equal weight in the same label gutter, so
 * finding either meant reading all thirteen. Two rules cost two pixels and
 * turn one list into three short ones; headings would have cost back the
 * height this is trying to save, and the rows are already labelled.
 */
@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Hairline),
    )
}

/**
 * How the page is set: type size, line spacing, alignment, margins.
 *
 * The third pane of the reader's menu, and the last one to be split off
 * (2026-09-23). Four rows of options need more width than a 320dp screen has
 * beside anything else — "Relaxed" and "Notebook" each wrap to a second line,
 * which is what pushed the rows below them past the bottom edge.
 *
 * Splitting these out costs a tap on a setting that design.md §11 already
 * observes is made once and then left alone, which is why it is these rows
 * that moved rather than Contents or Ask.
 */
@Composable
private fun TypeToolsMenu(
    fontScale: Float,
    lineSpacing: Float,
    textAlign: TextAlignPref,
    horizontalPadding: Float,
    onFontSmaller: () -> Unit,
    onFontLarger: () -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onTextAlignChange: (TextAlignPref) -> Unit,
    onHorizontalPaddingChange: (Float) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canShrink = ReaderPrefs.FONT_SCALES.indexOfNearest(fontScale) > 0
    val canGrow = ReaderPrefs.FONT_SCALES.indexOfNearest(fontScale) < ReaderPrefs.FONT_SCALES.lastIndex

    MenuSheet(modifier = modifier) {
        MenuRow(label = "") {
            Box(
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "← " + stringResource(R.string.reading),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
            }
        }

        MenuDivider()

        MenuRow(label = stringResource(R.string.font)) {
            TextButton(onClick = onFontSmaller, enabled = canShrink) {
                Text("A−", fontSize = 16.sp, color = if (canShrink) Ink else Muted)
            }
            Text(
                text = "%.2fx".format(fontScale),
                style = MaterialTheme.typography.bodySmall.copy(color = Muted),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            TextButton(onClick = onFontLarger, enabled = canGrow) {
                Text("A+", fontSize = 18.sp, color = if (canGrow) Ink else Muted)
            }
        }

        // Line spacing
        MenuRow(label = stringResource(R.string.spacing)) {
            ReaderPrefs.LINE_SPACINGS.forEachIndexed { i, v ->
                SelectableChip(
                    label = ReaderPrefs.LINE_SPACING_LABELS[i],
                    selected = kotlin.math.abs(lineSpacing - v) < 0.01f,
                    onClick = { onLineSpacingChange(v) },
                )
            }
        }

        // Alignment
        MenuRow(label = stringResource(R.string.align)) {
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
        MenuRow(label = stringResource(R.string.margin)) {
            ReaderPrefs.HORIZONTAL_PADDINGS.forEachIndexed { i, v ->
                SelectableChip(
                    label = ReaderPrefs.HORIZONTAL_PADDING_LABELS[i],
                    selected = kotlin.math.abs(horizontalPadding - v) < 0.5f,
                    onClick = { onHorizontalPaddingChange(v) },
                )
            }
        }    }
}

/**
 * What this chapter still needs made: its pictures, and the option to have it
 * written again.
 *
 * Split out of [FormatMenu] on 2026-09-23, when thirteen rows in one sheet
 * turned out not to fit a 320x640 screen — the bottom third, all of it these
 * rows, could not be reached at all, and nobody saw it on the Boox because
 * that screen is tall enough. The size was the symptom. These rows PRODUCE
 * the book: used a handful of times per chapter, the only ones that spend
 * money, and about the chapter rather than about reading it. The rows they
 * sat among — contents, search, ask, bookmark, type size — are used on every
 * page.
 *
 * A second panel rather than a scrolling sheet, originally because with the
 * menu open a finger drag means "close the menu" in [resolveSwipe], so a
 * scrolling sheet would have needed the gesture path taught first. That has
 * since been done — [MenuSheet] scrolls and the page detector drops gestures
 * a child consumed — but the split stays on its own merits: these rows
 * PRODUCE the book and the ones they left behind are used on every page.
 * Grouping was the better half of the reason all along; the height was only
 * what forced the question.
 */
@Composable
private fun ChapterToolsMenu(
    pendingFigures: Int,
    figureSlots: Int,
    pendingBadges: Int,
    hasCover: Boolean,
    hasTail: Boolean,
    /** The closing picture pays off a cover that has since been redrawn. */
    tailStale: Boolean,
    figureStatus: String?,
    busyDrawing: Boolean,
    onIllustrate: () -> Unit,
    onPickRedraw: () -> Unit,
    onDrawBadges: () -> Unit,
    onDrawCover: () -> Unit,
    onDrawTail: () -> Unit,
    onRedrawCover: () -> Unit,
    onRedrawTail: () -> Unit,
    onRewriteChapter: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MenuSheet(modifier = modifier) {
        // Names where it goes back to, like every other header in this app.
        MenuRow(label = "") {
            Box(
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "← " + stringResource(R.string.reading),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
            }
        }

        MenuDivider()

        // The repair mechanism the generate-a-book premise needs: the model
        // will occasionally write a dud, and without this the reader's only
        // option is to live with it for the rest of the course.
        MenuRow(label = stringResource(R.string.chapter)) {
            Box(
                modifier = Modifier
                    .clickable(enabled = !busyDrawing) { onRewriteChapter() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.rewrite_this_chapter),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (busyDrawing) Muted else Ink,
                )
            }
        }

        // Four rows for what is, nine times out of ten, one sentence. The
        // picture rows had to stop hiding when their work finished — absence
        // read as "not done yet" just as easily as "done", which is how a
        // reader redrew art he already had — but saying that four times over
        // is most of the reason this menu grew too tall to read.
        //
        // So: one line while there is nothing to do, which is the common
        // state, and the individual rows only when something is genuinely
        // outstanding — which is exactly when you need them separately
        // anyway, because each one is a different action.
        // The rows used to collapse to one line — "Pictures · all drawn" —
        // whenever there was nothing outstanding, because thirteen rows in
        // one sheet was too tall to read. The pane split fixed the height,
        // and the collapse now hides the only path to a REDRAW: a picture
        // that is drawn and wrong is exactly the case where the row matters,
        // and "all drawn" was a dead end saying everything is fine.
        run {
            // The picture rows below REPORT before they offer. Each one used to
            // disappear the moment its work finished, which reads as "not done
            // yet" exactly as easily as "done" — so a reader whose figures and
            // badges had already been drawn went looking for something to tap
            // and redrew what he already had (2026-09-21). A finished row says
            // so and is not clickable; only genuinely outstanding work is.
            if (figureSlots > 0) {
                // A drawn figure used to be a dead end: the row reported
                // "all N drawn" and did nothing, so the only way to replace a
                // bad picture was to rewrite the chapter — which deliberately
                // KEEPS its pictures. Opening and Closing were given a redraw
                // in 2026-09-23 and the figures were not, which went unnoticed
                // until the house style changed and there was no way to apply
                // it. The row reports when there is nothing outstanding AND
                // offers to redraw; the picker names which picture.
                MenuRow(label = stringResource(R.string.figures)) {
                    MenuAction(
                        text = figureStatus ?: when {
                            pendingFigures == 0 ->
                                stringResource(R.string.all_n_drawn, figureSlots) + " · " + stringResource(R.string.redraw)
                            else ->
                                stringResource(R.string.draw_n, pendingFigures) +
                                    " · ${pictureCost(pendingFigures)}"
                        },
                        enabled = !busyDrawing && figureStatus == null,
                        onClick = if (pendingFigures > 0) onIllustrate else onPickRedraw,
                    )
                }
            }

            // "Opening" rather than "Chapter": now that the row no longer
            // disappears when drawn, it sits permanently above Rewrite, which is
            // also a chapter-level action — two rows labelled "Chapter" doing
            // unrelated things. Opening/Closing reads as the pair it is.
            MenuRow(label = stringResource(R.string.opening)) {
                MenuAction(
                    // A row that reports can also offer. Drawn rows used to be
                    // dead ends: the only way to replace a picture was to
                    // rewrite the chapter, which deliberately KEEPS its
                    // pictures, so a wrong one was permanent.
                    text = if (hasCover) stringResource(R.string.redraw) + " · ${pictureCost(1)}"
                    else stringResource(R.string.draw_opening_picture) + " · ${pictureCost(1)}",
                    enabled = !busyDrawing,
                    onClick = if (hasCover) onRedrawCover else onDrawCover,
                )
            }

            // The closing picture is the cover's scene resolved rather than a
            // picture in its own right, so it cannot be drawn first. Saying that
            // beats offering nothing and leaving the gap unexplained.
            MenuRow(label = stringResource(R.string.closing)) {
                MenuAction(
                    text = when {
                        hasTail && tailStale ->
                            stringResource(R.string.no_longer_matches_the_opening) + " · " +
                                stringResource(R.string.redraw) + " · ${pictureCost(1)}"
                        hasTail -> stringResource(R.string.redraw) + " · ${pictureCost(1)}"
                        !hasCover -> stringResource(R.string.needs_the_chapter_picture_first)
                        else -> stringResource(R.string.draw_closing_picture) + " · ${pictureCost(1)}"
                    },
                    enabled = !busyDrawing && hasCover,
                    onClick = if (hasTail) onRedrawTail else onDrawTail,
                )
            }

            // The book's mascot badges — the small marks on exercise and recap
            // blocks. Drawn once for the whole course, so after the first chapter
            // this row is a report rather than an offer.
            MenuRow(label = stringResource(R.string.badges)) {
                MenuAction(
                    text = if (pendingBadges == 0) {
                        stringResource(R.string.all_drawn)
                    } else {
                        stringResource(R.string.draw_n, pendingBadges) +
                            " · ${pictureCost(pendingBadges)}"
                    },
                    enabled = !busyDrawing && pendingBadges > 0,
                    onClick = onDrawBadges,
                )
            }
        }
    }
}

/**
 * One tappable word in the menu — or, when there is nothing left to do, the
 * same row saying so in grey. Sharing it keeps "offer" and "report" looking
 * like one control rather than two, which is the point: the reader should not
 * have to work out whether a row is a button or a status line.
 */
@Composable
private fun MenuAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Ink else Muted,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MenuRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(color = Ink),
            // 70dp broke "Reading" across two lines — "Readin" over a lone
            // "g" — and left "Chapter" a hair from the same fate. The column
            // is a label gutter, so it costs the controls beside it nothing
            // to be wide enough for the longest word in it.
            modifier = Modifier.width(82.dp),
        )
        // Options WRAP rather than squeeze. A plain Row let a narrow screen
        // hyphenate the chips into "Rela xed" and "Wi de", and pushed the
        // fourth margin off the row entirely — "Notebook" could not be
        // chosen at all at 320dp, which is a setting you cannot reach rather
        // than a setting that looks cramped. There is vertical room in a
        // panel; there is no horizontal room to invent.
        FlowRow(verticalArrangement = Arrangement.Center) { content() }
    }
}

@Composable
private fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) 1.dp else 0.dp
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .then(
                if (selected) Modifier.border(border, Ink, RoundedCornerShape(4.dp))
                else Modifier,
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = Ink,
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
    labels: UiLabels,
) {
    when (block) {
        is Block.Heading -> HeadingBlock(block, fontScale, atPageTop, loadFigure)
        is Block.Paragraph -> ParagraphBlock(
            block.inlines, fontScale, lineSpacing, textAlign,
            annotations, onOpenAnnotation, onMarkerBounds,
        )
        is Block.BulletItem ->
            ListItemBlock("• ", block.inlines, fontScale, lineSpacing, block.cont)
        is Block.OrderedItem ->
            ListItemBlock("${block.n}. ", block.inlines, fontScale, lineSpacing, block.cont)
        is Block.Rule -> RuleBlock()
        is Block.CustomFence -> when (block.kind) {
            "exercise" ->
                ExerciseBlock(
                    block, fontScale, lineSpacing, revealed, onReveal, onRevealBounds,
                    loadBadge, labels,
                )
            "recap" -> RecapBlock(block, fontScale, lineSpacing, loadBadge, labels)
            "figure" -> FigureBlock(block, fontScale, lineSpacing, loadFigure, labels)
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
        color = Ink,
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
                color = Ink,
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
                        color = Ink,
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
private fun ListItemBlock(
    prefix: String,
    inlines: List<Inline>,
    fontScale: Float,
    lineSpacing: Float,
    /** A piece carried over a page break: keep the column, drop the mark. */
    cont: Boolean = false,
) {
    // 8dp is less than one character width — the bullets shared a left edge
    // with the body and the list didn't read as subordinate. One em at the
    // body size is ~18dp. Items stay tighter to each other than paragraphs
    // are, which is right within a list; the air around the list as a whole
    // comes from the paragraphs on either side.
    Row(modifier = Modifier.padding(start = 18.dp, bottom = 10.dp)) {
        Text(
            text = prefix,
            style = listItemStyle(fontScale, lineSpacing),
            // Drawn in nothing rather than skipped: the continuation's text
            // has to stay in the same hanging indent as the lines above it,
            // and the surest way to reserve exactly the marker's width is
            // the marker itself.
            color = if (cont) Color.Transparent else Ink,
        )
        Text(
            text = renderInlines(inlines),
            style = listItemStyle(fontScale, lineSpacing),
            color = Ink,
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
    labels: UiLabels,
) {
    val (question, answer) = splitExerciseBody(f.body)
    // A block cut across a page boundary. The head keeps the name and the
    // mascot and drops the reveal button — the answer travels with the tail —
    // and the tail says it is a continuation rather than introducing itself a
    // second time, which would read as a second exercise.
    val isHead = f.params[FENCE_CONT_TO] != null
    val isTail = f.params[FENCE_CONT_FROM] != null
    FlowBlock(
        label = labels.t("TRY THIS"),
        fontScale = fontScale,
        badge = loadBadge("exercise"),
        closer = !isHead,
        // A continuation says nothing at all. With no frame there is no open
        // edge to explain and no box to reintroduce: the italic simply
        // carries on over the leaf, the way a paragraph does.
        opener = !isTail,
    ) {
        // A part split can leave a tail with no words at all — the task fitted
        // where it was and only the room and the answer came over the leaf.
        // An empty Text still draws a blank line, which reads as a gap.
        if (question.isNotBlank()) {
            Text(
                // Parsed, not raw: an exercise says "call `torch.tensor()`" as
                // often as a paragraph does, and rendering the body as a plain
                // string dropped every inline style inside a bordered block.
                text = renderInlines(parseInlines(question)),
                // ITALIC IS THE FRAME NOW. The border used to say "this is a
                // thing you DO"; it also made the block a unit that had to
                // land whole or reintroduce itself. Italic says the same at
                // no cost to the page, because styled text flows. The style is
                // passed WHOLE — see [flowBodyStyle].
                style = flowBodyStyle(fontScale, lineSpacing),
                color = Ink,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (isHead) {
            // A head carries no answer and no reveal button — but it CAN
            // carry working room, since the part split. When a box will not
            // fit, what moves over the leaf is the room and the answer, not
            // the words: the head keeps the whole task plus as many rows as
            // its page will take, which is what stops a full page being
            // spent on four lines. Only the count pagination wrote is drawn
            // here; a head that was given none shows none.
            val headRows = f.params[FENCE_SPACE_ROWS]?.toIntOrNull() ?: 0
            if (headRows > 0 && f.params["space"] != "none") {
                AnswerSpace(
                    lines = headRows,
                    fontScale = fontScale,
                    ruled = f.params["space"] == "lines",
                )
            }
        } else {
        // Room to write, ABOVE the answer rather than where the answer will
        // go. Revealing must not land printed text on top of the reader's own
        // handwriting, and after the tap the two sit one above the other,
        // which is the whole point of writing first and comparing after.
        // Default is the GRID, and the model opts out of it. Inverted on
        // 2026-09-30, Aliaksei's call, because the failure is asymmetric: you
        // can write on a dot grid, but you cannot comfortably draw on writing
        // lines. Forgetting to mark a fence should land on the harmless side.
        val spaceKind = f.params["space"]
        val ruledSpace = spaceKind == "lines"
        AnswerSpace(
            // A row count written by PAGINATION outranks the natural one: the
            // page could not hold the box at its full size and gave the
            // working room up to fit it. See FENCE_SPACE_ROWS.
            lines = if (spaceKind == "none") {
                0
            } else {
                f.params[FENCE_SPACE_ROWS]?.toIntOrNull()
                    ?: answerSpaceLineCount(answer, ruledSpace)
            },
            fontScale = fontScale,
            // Ruled unless the chapter model said otherwise. Ruled is the
            // right default for a study book — most answers are written — and
            // it is also the milder failure: blank space under a question
            // still reads as somewhere to answer, where rules across a drawing
            // read as a mistake.
            ruled = ruledSpace,
        )
        Spacer(Modifier.height(8.dp))
        if (revealed || answer.isEmpty()) {
            if (answer.isNotEmpty()) {
                // The mascot again, in its telling-you-a-secret pose. Top
                // aligned rather than centred: the answer usually runs to
                // several lines and a centred badge would float oddly.
                Row {
                    // Pagination drops the mascot when the page needs the
                    // width it was taking from the answer beside it. See
                    // FENCE_PLAIN_ANSWER.
                    val answerBadge =
                        if (f.params[FENCE_PLAIN_ANSWER] == null) loadBadge("answer") else null
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
                        // The label in bold, the answer in italic like the
                        // task it belongs to. With no frame to mark where the
                        // answer starts, the word has to do it: a run of
                        // italic that merely continues reads as more task.
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(labels.t("Answer:"))
                            }
                            append(" ")
                            append(renderInlines(parseInlines(answer)))
                        },
                        style = answerBodyStyle(fontScale, lineSpacing),
                        color = Ink,
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
            ) { Text(labels.t("Show answer"), color = Ink) }
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose { onRevealBounds(null) }
            }
        }
        }
    }
}

/**
 * Ruled lines under an exercise: somewhere to write the answer before reading
 * it.
 *
 * Aliaksei's own loop, 2026-09-29 — "I use stylus to mark answer, or write on
 * the page then open and compare" — and the reason the blank area under an
 * exercise was never dead space. It was the working area; we just had not
 * drawn it.
 *
 * It earns its place on a device with no stylus too, which is most of them.
 * Head First prints these lines knowing most readers never fill them in: they
 * mark a beat to stop and think, they break the wall of text, and they set the
 * page's mode to workbook rather than article. Only the fourth job needs a pen.
 *
 * ALWAYS DRAWN, including after the answer is revealed. Ink is anchored to a
 * block and does not reflow, so a space that disappeared on reveal would leave
 * the reader's handwriting floating over the printed answer.
 */
@Composable
private fun AnswerSpace(lines: Int, fontScale: Float, ruled: Boolean) {
    if (lines <= 0) return
    Spacer(Modifier.height(6.dp))
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(ANSWER_RULE_SPACING * fontScale * lines),
    ) {
        // Same room either way; only the treatment differs. A task you answer
        // by MAKING something gets a DOT GRID rather than rules — what a
        // sketchbook actually uses, because dots give proportion and
        // alignment to work against without lines cutting through the
        // drawing. Rules across a drawing are worse than nothing; an empty
        // rectangle reads as space nobody filled in. A grid says "work here"
        // to a reader with no stylus too, which is the same argument the
        // ruled lines earn their place on.
        //
        // The grid steps by the same measure as the rules, so the two
        // treatments share a vertical rhythm and read as one family. It is
        // also about 5mm, which is what dot-grid paper uses.
        val step = size.height / lines
        // A ZERO STEP IS AN INFINITE GRID. `cols` below divides the width by
        // this, and a Float divided by zero is Infinity, whose toInt() is
        // Int.MAX_VALUE — so one layout pass that hands this Canvas no height
        // asks it to draw two billion dots per row, and the app wedges at
        // 100% CPU with an ANR. Caught on the emulator the same afternoon the
        // centring went in; the old uncentred loop had the same hole by a
        // different route (`x += 0` never terminates), which is why neither
        // was noticed. Nothing sane to draw, so draw nothing.
        if (step <= 0.5f || !step.isFinite()) return@Canvas
        if (!ruled) {
            // E-INK HAS NO GREY. Reported from the Boox: the dots were
            // "almost invisible". They were 0.75dp of Hairline (0xFF888888),
            // which at the panel's 300dpi is a disc about 2.8px across with
            // no fully covered pixel — an antialiased mid-grey smudge. An LCD
            // renders that as a faint dot; the panel thresholds and dithers,
            // so it can resolve to nothing at all. Verified at 1x on the
            // emulator only, which is exactly where this could not show up:
            // there 0.75dp is a SUB-PIXEL radius.
            //
            // Black, and never smaller than a whole pixel. A dot grid on
            // PAPER is light grey because ink reproduces greys faithfully;
            // this panel does not, so the equivalent perceptual weight is a
            // small BLACK dot. It stays about 0.3mm on the Boox, which is
            // what dot-grid paper actually uses, so it reads as a guide
            // rather than as decoration.
            val radius = maxOf(1.dp.toPx(), 1.5f)
            // CENTRED, both ways. The columns used to start one step in from
            // the left and stop wherever the last one fell short of the right
            // edge, so the margins were a full step on the left and whatever
            // was left over on the right — the grid read as shoved sideways
            // under text that is flush to both margins. Rows had the same
            // bias: a step of air at the top and none at the bottom, with the
            // last row sitting exactly on the edge of the band.
            //
            // Take as many columns as fit, then share the remainder equally.
            // Clamped as well as guarded: belt and braces on the one loop
            // in this file whose bound comes from a division.
            val cols = (size.width / step).toInt().coerceIn(1, 128)
            val spanX = cols * step
            val x0 = (size.width - spanX) / 2f
            val spanY = (lines - 1) * step
            val y0 = (size.height - spanY) / 2f
            for (r in 0 until lines) {
                for (c in 0..cols) {
                    val x = x0 + c * step
                    if (x < -0.5f || x > size.width + 0.5f) continue
                    drawCircle(Ink, radius, Offset(x, y0 + r * step))
                }
            }
            return@Canvas
        }
        val stroke = 1.dp.toPx()
        // Dotted, not solid. The box around this already has solid hairline
        // borders, so solid rules inside it read as table rows — structure
        // rather than an invitation. A dotted rule is the established "fill
        // this in" mark and cannot be mistaken for the frame.
        val dots = PathEffect.dashPathEffect(
            floatArrayOf(2.dp.toPx(), 4.dp.toPx()),
            0f,
        )
        for (i in 1..lines) {
            val y = step * i
            drawLine(
                color = Hairline,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = stroke,
                pathEffect = dots,
            )
        }
    }
}

@Composable
private fun RecapBlock(
    f: Block.CustomFence,
    fontScale: Float,
    lineSpacing: Float,
    loadBadge: (String) -> ImageBitmap?,
    labels: UiLabels,
) {
    val isTail = f.params[FENCE_CONT_FROM] != null
    FlowBlock(
        label = labels.t("RECAP"),
        fontScale = fontScale,
        badge = loadBadge("recap"),
        opener = !isTail,
        closer = f.params[FENCE_CONT_TO] == null,
        // WASHED rather than ruled. Aliaksei's call: a recap is the one block
        // a wash is safe on, because it has no working room — nothing is ever
        // written on top of it, which is the objection that rules a grey fill
        // out everywhere else in this app. It also reads as a different
        // speech act at a glance: the book summarising itself, where TRY THIS
        // asks you for something.
        wash = true,
    ) {
        Text(
            text = renderInlines(parseInlines(f.body.trim())),
            // EXPLICIT STYLE, NOT MERGED PARAMS. A `Text` given fontSize/fontFamily
                // one by one MERGES them onto the ambient LocalTextStyle and keeps
                // everything else it carries — including Material's letterSpacing of
                // 0.5sp. The paginator measures a bare TextStyle with no such spacing,
                // so the renderer fitted ~2 characters fewer per line than the estimate
                // believed: a recap sized for 13 lines drew 15, and its last line came
                // off the bottom of the page. ParagraphBlock has always passed a whole
                // style, which is exactly why paragraphs measured to the pixel and
                // everything else drifted. Pass the style the measurer uses.
            style = flowBodyStyle(fontScale, lineSpacing),
            color = Ink,
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
    labels: UiLabels,
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

    BorderedBlock(label = labels.t("FIGURE"), fontScale = fontScale) {
        Text(
            text = alt,
            fontFamily = FontFamily.Serif,
            fontSize = 16.sp * fontScale,
            lineHeight = 26.sp * fontScale * lineSpacing,
            fontStyle = FontStyle.Italic,
            color = Muted,
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
        color = Muted,
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
            color = Ink,
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
            color = Ink,
        )
    }
}

/**
 * A block that FLOWS: a mascot and a name, then styled text, and no frame.
 *
 * The frame was the problem rather than the decoration. A bordered box is a
 * unit — it has to land whole on one page, and when it cannot it must leave
 * an edge open and reintroduce itself overleaf, paying its label, its mascot
 * and two lots of padding every time it is cut. Reported from the Boox with
 * the font turned up (2026-10-06): one task came out as three pages of four
 * or five lines each, most of every page white, because the only thing the
 * paginator could cut was the words while the furniture stayed whole.
 *
 * Aliaksei's call, and it is the right one: keep the icon and the title, set
 * the body in italic, and drop the box. Italic marks a task as well as a
 * border does and costs the page nothing, because styled text flows exactly
 * like prose — which is what he expected of it in the first place.
 *
 * A continuation passes `opener = false` and draws nothing of its own.
 *
 * Code and undrawn-figure fallbacks keep [BorderedBlock]: a code listing in a
 * box is a convention readers expect, and the figure box is a placeholder for
 * a picture rather than something to read.
 */
/**
 * The body style of a flow block — exercise and recap — built WHOLE.
 *
 * Passing fontSize and fontFamily to `Text` as separate parameters merges them
 * onto the ambient `LocalTextStyle` and inherits everything else it carries,
 * Material's 0.5sp letterSpacing included. The paginator measures a bare
 * `TextStyle`, so the two disagreed by about two characters a line and a
 * recap sized for 13 lines drew 15. Anything the paginator measures has to be
 * drawn from a style it can reproduce exactly.
 */
private fun flowBodyStyle(fontScale: Float, lineSpacing: Float) = TextStyle(
    fontSize = 17.sp * fontScale,
    lineHeight = 28.sp * fontScale * lineSpacing,
    fontFamily = FontFamily.Serif,
    fontStyle = FontStyle.Italic,
)

/** The revealed answer's style. Same rule as [flowBodyStyle]. */
private fun answerBodyStyle(fontScale: Float, lineSpacing: Float) = TextStyle(
    fontSize = 16.sp * fontScale,
    lineHeight = 26.sp * fontScale * lineSpacing,
    fontFamily = FontFamily.Serif,
    fontStyle = FontStyle.Italic,
)

/** A list item's style, marker and body alike. Same rule as [flowBodyStyle]. */
private fun listItemStyle(fontScale: Float, lineSpacing: Float) = TextStyle(
    fontSize = 18.sp * fontScale,
    lineHeight = 30.sp * fontScale * lineSpacing,
    fontFamily = FontFamily.Serif,
)

@Composable
private fun FlowBlock(
    label: String,
    fontScale: Float,
    badge: ImageBitmap? = null,
    /** False on a piece that began on the page before. */
    opener: Boolean = true,
    /** True on the piece the block ENDS on: the rule turns its foot. */
    closer: Boolean = true,
    /** A lighter claim on the reader — see [RecapBlock]. */
    dashed: Boolean = false,
    /**
     * Fill the block instead of ruling it. A recap has no working room, so
     * nothing is ever written on top of it — which is the objection that
     * ruled a wash out everywhere else.
     */
    wash: Boolean = false,
    content: @Composable () -> Unit,
) {
    // THE RULE IS THE HEADER AND THE FOOTER AT ONCE, which is why it is a
    // rule and not the two devices it replaces. A title band appears once at
    // the start and a footer once at the end — so the MIDDLE page of a block
    // that runs over three pages would get neither, and the middle page is
    // exactly the one that reads as orphaned. A marginal rule is continuous
    // along the block, so every page it touches is marked, and because it
    // sits BESIDE the text it costs no vertical space on any of them — the
    // resource this whole redesign was fighting for.
    //
    // The convention is the change bar of technical setting, plus the
    // set-off extract: a passage stepped in from the measure needs neither a
    // box nor quotation marks. The foot is the end-bracket of marginal
    // annotation: it turns right only on the piece where the block ends, so
    // "no foot" means "carries on over the leaf" — one bit of information,
    // carried by geometry, costing no words and no height.
    //
    // Inside the text column rather than out in the page margin, deliberately:
    // the outer margin is where a pen mark MEANS something in this app (a
    // passage-marking gesture), and a printed rule out there invites the
    // reader to trace it.
    val strokePx = with(LocalDensity.current) { FLOW_RULE_WIDTH.toPx() }
    val dashes = if (dashed) {
        PathEffect.dashPathEffect(
            floatArrayOf(with(LocalDensity.current) { 10.dp.toPx() }, with(LocalDensity.current) { 6.dp.toPx() }),
            0f,
        )
    } else {
        null
    }
    val cornerPx = with(LocalDensity.current) { 4.dp.toPx() }
    Spacer(Modifier.height(4.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .drawBehind {
                if (wash) {
                    // A FILL HAS EDGES, so it has to handle a cut like the
                    // box did — but far more gracefully, because a wash that
                    // runs off the page bottom reads as "carries on", where a
                    // broken border reads as a rendering fault. Corners are
                    // rounded only where the block actually begins and ends.
                    val top = if (opener) cornerPx else 0f
                    val bottom = if (closer) cornerPx else 0f
                    drawRoundRect(
                        color = Wash,
                        cornerRadius = CornerRadius(maxOf(top, bottom)),
                    )
                    if (top == 0f || bottom == 0f) {
                        // Square off the cut edge the rounding just softened.
                        val fillTop = if (top == 0f) 0f else cornerPx
                        val fillBottom = if (bottom == 0f) size.height else size.height - cornerPx
                        drawRect(
                            color = Wash,
                            topLeft = Offset(0f, fillTop),
                            size = Size(size.width, (fillBottom - fillTop).coerceAtLeast(0f)),
                        )
                    }
                    return@drawBehind
                }
                val x = strokePx / 2f
                drawLine(
                    color = Ink,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = strokePx,
                    pathEffect = dashes,
                )
                // No foot. It was the end-bracket of marginal annotation and
                // it carried a real bit — "no foot means this carries on over
                // the leaf" — but Aliaksei read the L as clutter rather than
                // as information, and he is the one reading. The vertical
                // line alone marks the span; where the block ends is already
                // obvious from the text stopping.
            }
            .padding(
                start = if (wash) WASH_PAD else FLOW_RULE_INDENT,
                end = if (wash) WASH_PAD else 0.dp,
                top = if (wash) WASH_PAD else 0.dp,
                bottom = if (wash) WASH_PAD else 0.dp,
            ),
    ) {
        if (opener) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                if (badge != null) {
                    // MULTIPLIED, not drawn. The mascots are black line art
                    // on an OPAQUE WHITE field, which is invisible on a white
                    // page and a white card punched through the recap's wash
                    // — reported the moment the wash landed. Multiply makes
                    // white transparent by arithmetic (white × anything is
                    // that thing) while black stays black, which is the
                    // standard trick for line art and needs no change to the
                    // PNGs every book has already generated and paid for.
                    val filter = badgeColorFilter()
                    Canvas(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(BADGE_SIZE * fontScale),
                    ) {
                        drawImage(
                            image = badge,
                            srcSize = IntSize(badge.width, badge.height),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                            colorFilter = filter,
                            blendMode = BlendMode.Multiply,
                        )
                    }
                }
                Text(
                    text = label,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = FLOW_LABEL_SP.sp * fontScale,
                    letterSpacing = 1.5.sp,
                    // Black. The frame used to carry the block and the label
                    // could afford to be quiet; now the label is half the
                    // signal, and grey is the one thing this panel cannot
                    // render — it thresholds and dithers, so Muted at 11sp
                    // is noise rather than softness.
                    color = Ink,
                )
            }
        }
        content()
    }
}

/** The marginal rule's weight. Never below one physical pixel on the panel. */
private val FLOW_RULE_WIDTH = 2.dp

/** How far the block's content steps in from its rule. */
private val FLOW_RULE_INDENT = 14.dp

/** Air between a washed block's edge and its text. */
private val WASH_PAD = WASH_PAD_DP.dp

@Composable
private fun BorderedBlock(
    label: String,
    fontScale: Float,
    badge: ImageBitmap? = null,
    /** This block carries on over the leaf: leave the foot of the box open. */
    openBottom: Boolean = false,
    /** This block began on the page before: leave the head of the box open. */
    openTop: Boolean = false,
    content: @Composable () -> Unit,
) {
    // An open edge is drawn by hand because `Modifier.border` can only close
    // all four. Square corners at the break are correct rather than a
    // compromise — a rounded corner on a box that does not end there reads as
    // an ending. The unsplit case keeps the rounded border exactly as before.
    val strokePx = with(LocalDensity.current) { 1.dp.toPx() }
    val edges = Modifier.drawBehind {
        val w = size.width
        val h = size.height
        if (!openTop) drawLine(Hairline, Offset(0f, 0f), Offset(w, 0f), strokePx)
        if (!openBottom) drawLine(Hairline, Offset(0f, h), Offset(w, h), strokePx)
        drawLine(Hairline, Offset(0f, 0f), Offset(0f, h), strokePx)
        drawLine(Hairline, Offset(w, 0f), Offset(w, h), strokePx)
    }
    Spacer(Modifier.height(4.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .then(
                if (openTop || openBottom) edges
                else Modifier.border(1.dp, Hairline, RoundedCornerShape(4.dp)),
            )
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
                color = Muted,
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

/**
 * Is this mark the reader DOING the task rather than selecting part of it?
 *
 * An exercise offers room to work in below its question — a dot grid or ruled
 * lines — and a mark made there is an answer, not a gesture. Without this,
 * every circle of "draw ten circles on your page" popped the Ask/Note chip:
 * the stroke is enclosing, it is anchored to the exercise block, and the
 * near-miss fallback in [extractStrokeText] let it claim the last line of the
 * question. Reported from the Boox 2026-10-02, drawing the ten circles.
 *
 * Asked GEOMETRICALLY rather than by "did the model mark this fence as a
 * drawing task", because the room is offered whatever the treatment — you can
 * write an answer on ruled lines and that is not a selection either.
 *
 * The test is the stroke's CENTRE, not its top: a circle drawn around the last
 * line of the question dips below the text and must still count as marking it,
 * while one drawn in the first row of the grid sits below the text even if its
 * top edge grazes a descender.
 */
private fun strokeIsInWorkingRoom(
    stroke: InkStroke,
    block: Block,
    widthPx: Int,
    measurer: TextMeasurer,
    density: Density,
    fontScale: Float,
    lineSpacing: Float,
    badgeHeightPx: (String) -> Float = { 0f },
): Boolean {
    val fence = block as? Block.CustomFence ?: return false
    if (fence.kind != "exercise") return false
    if (fence.params["space"] == "none") return false
    if (stroke.points.isEmpty() || widthPx <= 0) return false
    val geometry = blockTextGeometry(
        block, widthPx, density, measurer, fontScale, lineSpacing, badgeHeightPx,
    ) ?: return false
    if (geometry.plain.isBlank()) return false
    val layout = measurer.measure(
        text = AnnotatedString(geometry.plain),
        style = geometry.style,
        constraints = Constraints(maxWidth = geometry.widthPx),
    )
    var minY = Float.POSITIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    stroke.points.forEach { p ->
        val y = p.y - geometry.topOffsetPx
        if (y < minY) minY = y
        if (y > maxY) maxY = y
    }
    return (minY + maxY) / 2f > layout.size.height
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
    /**
     * The reader's margin setting, in dp. Applied AFTER the gesture modifier
     * below so the tap zones still measure the whole page while the text is
     * inset — the same rule the paginated reader follows (design.md §11).
     *
     * This page used to take a `modifier` parameter, get
     * `Modifier.padding(horizontal = ...)` handed to it by its caller, and
     * then never use it: the root Column started from a fresh
     * `Modifier.fillMaxSize()`. So a not-yet-compiled chapter rendered its
     * title and objectives hard against the bezel while every other page in
     * the book honoured the setting. Found 2026-09-21.
     */
    horizontalPadding: Float,
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

    // Swipe support on the compile prompt, so a pending chapter is not a dead
    // end until it is compiled.
    //
    // A VERTICAL swipe used to navigate here too. It no longer does: the front
    // matter scrolls now, and a vertical drag inside it is the scroll. That is
    // the conventional reading of a drag on a scrolling page, and one rule
    // beats "sometimes scrolls, sometimes turns the chapter" — but it IS a
    // capability this screen used to have, so the ways out were checked rather
    // than assumed. Edge taps navigate, horizontal swipes navigate, the side
    // buttons navigate, and the footer says so in words.
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
                    // Set when a child consumed part of the DRAG — in practice
                    // the scrolling content below. Without it a swipe that
                    // scrolled the objectives also turned the chapter, which
                    // is the same "two things owned one contact" mistake the
                    // reader's own tap ladder exists to prevent.
                    var childTookIt = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) {
                            // A child (Compile / Back / Try again) already took
                            // this tap — don't navigate out from under it.
                            if (ch.isConsumed || childTookIt) break
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
                        if (ch.isConsumed) childTookIt = true
                        val p = ch.positionChange()
                        dx += p.x
                        dy += p.y
                        if (!isDrag && (kotlin.math.abs(dx) > dragStartPx || kotlin.math.abs(dy) > dragStartPx)) {
                            isDrag = true
                        }
                    }
                }
            }
            .padding(horizontal = horizontalPadding.dp),
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
        TextButton(onClick = onBack) { Text("← " + stringResource(R.string.back), color = Ink) }
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.ch_n_of_total, chapter.n, chapterCount),
            style = MaterialTheme.typography.bodySmall.copy(color = Muted),
        )
    }

    // The front matter scrolls; the action below never does.
    //
    // It used to be one column that sized to its content, so a chapter with
    // several objectives pushed "Compile chapter" off the bottom of a short
    // screen and the chapter could not be written at all. Invisible on the
    // Boox, which is tall enough, and found on a 320x640 emulator — the same
    // shape of defect as the New course form, and fixed the same way it was:
    // the button must never be the thing that scrolled away.
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
    ) {
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
        color = Muted,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Chapter ${chapter.n}: ${chapter.title}",
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        color = Ink,
    )

    if (chapter.objectives.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = "What you'll learn",
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Muted,
        )
        Spacer(Modifier.height(8.dp))
        chapter.objectives.forEach { obj ->
            Row(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = "• ",
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp,
                    color = Ink,
                )
                Text(
                    text = obj,
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = Ink,
                )
            }
        }
    }

    }  // close the scrolling front matter

    Spacer(Modifier.height(28.dp))

    when (compileState) {
        CompileState.Running -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).padding(end = 8.dp),
                    color = Ink,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Compiling chapter…",
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                    color = Ink,
                )
            }
        }
        is CompileState.Illustrating -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).padding(end = 8.dp),
                    color = Ink,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Drawing figures… ${compileState.done}/${compileState.total}",
                    fontFamily = FontFamily.Serif,
                    fontSize = 15.sp,
                    color = Ink,
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
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) {
                Text(stringResource(R.string.try_again), color = Paper)
            }
        }
        CompileState.Idle -> {
            Button(
                onClick = onCompile,
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) {
                Text(stringResource(R.string.compile_chapter), color = Paper)
            }
        }
    }

    // Discoverability: the reader teaches "tap the edges to turn", and this
    // screen honours that, but with no page footer there was nothing saying so.
    if (onOpenPrevChapter != null || onOpenNextChapter != null) {
        // No weight spacer any more: the scrolling region above takes the
        // slack, and a second weight in the same column would fight it.
        Spacer(Modifier.height(16.dp))
        Text(
            text = buildString {
                if (onOpenPrevChapter != null) append("← tap for previous chapter")
                if (onOpenPrevChapter != null && onOpenNextChapter != null) append("   ·   ")
                if (onOpenNextChapter != null) append("next chapter →")
            },
            fontFamily = FontFamily.Serif,
            fontSize = 12.sp,
            color = Muted,
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
    maxFigures: Int = course.figurePolicy.maxPerChapter,
    only: Set<Int>? = null,
): String = try {
    val result = ChapterIllustrator(client, imageModel).illustrate(
        course = course,
        chapter = chapter,
        markdown = markdown,
        apiKey = apiKey,
        maxFigures = maxFigures,
        only = only,
        onProgress = { done, total -> onState(CompileState.Illustrating(done, total, "figures")) },
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
    only: Set<Int>? = null,
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
                    // No ceiling on this path. The ceiling bounds what gets
                    // spent without being asked; here the reader tapped a row
                    // that named the count and the price.
                    maxFigures = Int.MAX_VALUE,
                    only = only,
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
                    onProgress = { done, total -> onState(CompileState.Illustrating(done, total, "badges")) },
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
/**
 * Draws the chapter's opening and closing pictures if it has none, inside
 * whichever coroutine is already compiling it.
 *
 * These used to be menu rows only, which meant that in practice most chapters
 * had neither: drawing them took knowing they existed, opening the menu, and
 * tapping twice, and a picture you have to go and find is a picture the book
 * does not have. The evidence was on the device — a book read for weeks whose
 * footer still said "still to draw: chapter picture".
 *
 * Best-effort like every other picture pass, and after the prose is saved, so
 * a failure costs a picture rather than a chapter. Missing only: a chapter
 * that already has a cover keeps it, which makes this idempotent.
 *
 * On a REWRITE the two files are deleted before this runs (see
 * CourseStore.clearChapterPictures), so "missing only" redraws them. They used
 * to be kept deliberately, on the grounds that both came from the outline
 * rather than from the body — true until the cover started being briefed from
 * the chapter's own opening.
 *
 * The closing picture needs the cover: it is the cover's scene resolved, and
 * takes the cover's own brief and image as input. So it is drawn second, and
 * skipped entirely if the cover did not come back.
 */
private suspend fun drawChapterPictures(
    client: GeminiClient,
    imageModel: String,
    course: Course,
    chapter: PersistedChapter,
    /** The chapter's finished text — the cover is briefed from its opening. */
    markdown: String,
    apiKey: String,
    store: com.inkfirst.core.CourseStore,
    onState: (CompileState) -> Unit,
) {
    val illustrator = ChapterIllustrator(client, imageModel)

    if (store.coverFile(course.slug, chapter.slug) == null) {
        onState(CompileState.Illustrating(0, 1, "the chapter picture"))
        illustrator.drawCover(
            course = course,
            chapter = chapter,
            apiKey = apiKey,
            textModel = course.model,
            saveImage = { name, bytes -> store.saveFigure(course.slug, name, bytes) },
            saveBrief = { brief -> store.saveCoverBrief(course.slug, chapter.slug, brief) },
            chapterMarkdown = markdown,
        )
    }

    val cover = store.coverFile(course.slug, chapter.slug) ?: return
    if (store.tailFile(course.slug, chapter.slug) != null) return

    onState(CompileState.Illustrating(0, 1, "the closing picture"))
    illustrator.drawTail(
        course = course,
        chapter = chapter,
        apiKey = apiKey,
        textModel = course.model,
        coverBrief = store.readCoverBrief(course.slug, chapter.slug),
        coverImage = GeneratedImage(
            bytes = cover.readBytes(),
            mimeType = if (cover.extension == "jpg") "image/jpeg" else "image/png",
        ),
        saveImage = { name, bytes -> store.saveFigure(course.slug, name, bytes) },
    )
}

private fun drawChapterCover(
    course: Course,
    chapter: PersistedChapter,
    /** The chapter's text, so the picture matches the page it faces. */
    markdown: String,
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
            onState(CompileState.Illustrating(0, 1, "the chapter picture"))
            KtorGeminiClient().use { client ->
                ChapterIllustrator(client, apiKeyStore.getImageModelName()).drawCover(
                    course = course,
                    chapter = chapter,
                    apiKey = apiKey,
                    textModel = course.model,
                    saveImage = { name, bytes -> store.saveFigure(course.slug, name, bytes) },
                    saveBrief = { brief -> store.saveCoverBrief(course.slug, chapter.slug, brief) },
                    chapterMarkdown = markdown,
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
            onState(CompileState.Illustrating(0, 1, "the closing picture"))
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

                // THE LAST GATE. The request and the outline were both
                // screened before this chapter existed, so this is the only
                // check that sees actual text — and the chapter model will
                // write instructions when asked, which is how the whole area
                // came to be looked at (design.md §19).
                //
                // Placed HERE, above the rewrite teardown, for the reason the
                // teardown itself is placed late: a refusal must leave the
                // book exactly as it was. A rewrite that is refused still has
                // its original chapter, its annotations and its ink.
                val verdict = TopicChecker(client, model)
                    .checkChapter(chapter.title, markdown, apiKey)
                if (verdict.verdict == TopicVerdict.REFUSE) {
                    // Nothing is saved and the chapter stays as it was, so
                    // "Rewrite" is still the way forward. The money is spent
                    // either way; what this decides is whether the text lands
                    // on disk.
                    onState(CompileState.Failed(verdict.reason))
                    return@use
                }

                // Only now, with the new chapter in hand, throw away what was
                // anchored into the old one.
                if (isRewrite) {
                    // The old body is set aside as <slug>.previous.md rather
                    // than overwritten. Nothing in the app reads it back; it
                    // is there so a chapter someone annotated is recoverable
                    // by hand. One generation deep — see archiveChapter.
                    store.archiveChapter(course.slug, chapter.slug)
                    store.clearChapterMarks(course.slug, chapter.slug)
                    // The cover and the closing picture go too. They were kept
                    // while they came from the OUTLINE, which a rewrite does
                    // not change; since 2026-09-28 the cover is briefed from
                    // the chapter's own opening and the closing from the cover,
                    // so both describe prose that is about to be replaced by a
                    // model explicitly told to find different metaphors.
                    store.clearChapterPictures(course.slug, chapter.slug)
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
                //
                // A book set to "on request" or "none" stops with the words.
                // Its briefs are still in the chapter and still render as alt
                // text, and the reader's menu draws any of them later — with
                // the price on the row. Compiling a chapter should not spend
                // money the reader did not ask to spend.
                val drawsNow = course.figurePolicy.automatic
                val illustrated = if (drawsNow) {
                    illustrateChapter(
                        client = client,
                        imageModel = apiKeyStore.getImageModelName(),
                        course = course,
                        chapter = chapter,
                        markdown = markdown,
                        apiKey = apiKey,
                        store = store,
                        onState = onState,
                    )
                } else {
                    markdown
                }
                // The chapter's own two pictures, drawn with it rather than
                // waiting behind a menu nobody opens. A chapter arrives
                // looking like a chapter.
                if (drawsNow) {
                    try {
                        drawChapterPictures(
                            client = client,
                            imageModel = apiKeyStore.getImageModelName(),
                            course = course,
                            chapter = chapter,
                            // The prose, not `markdown`: same words either
                            // way, and this is the copy that is on disk.
                            markdown = illustrated,
                            apiKey = apiKey,
                            store = store,
                            onState = onState,
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        System.err.println(
                            "chapter pictures failed: ${e::class.simpleName}: ${e.message}",
                        )
                    }
                }

                // Badges too, if this course has none yet — it is a one-time
                // cost on the first chapter, and then every exercise and
                // recap in the book has its mark. Failures are swallowed
                // inside BookBadger; a course with no badges just renders
                // plain blocks.
                try {
                    // Badges are drawn with the pictures or not at all — they are
                    // the same spend and the same decision.
                    if (drawsNow) {
                        BookBadger(client, apiKeyStore.getImageModelName()).drawMissing(
                            course = course,
                            apiKey = apiKey,
                            kinds = badgeKindsIn(illustrated),
                            existing = store.badgeKinds(course.slug),
                            knownMascot = store.readMascot(course.slug),
                            onProgress = { done, total -> onState(CompileState.Illustrating(done, total, "badges")) },
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
                    }
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
    /**
     * How many pictures the rewrite will draw again, or 0 for a book whose
     * figure policy does not draw on its own.
     *
     * Named with its price because this app's rule is that every row which
     * spends says what it costs, and rewriting had been the one place that
     * spent without saying — it has always redrawn the inline figures, and now
     * redraws the cover and closing picture too.
     */
    picturesRedrawn: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var feedback by remember { mutableStateOf("") }
    InkDialog(
        title = "Rewrite “$chapterTitle”",
        onDismiss = onDismiss,
        confirmLabel = "Rewrite",
        onConfirm = { onConfirm(feedback) },
        dismissLabel = "Keep it",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "The chapter is written again from its objectives, and this " +
                        "chapter starts over: everything you did in it goes with the " +
                        "old text.",
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Ink,
                )
                if (marksLost.isNotEmpty()) {
                    // Everything here points INTO the prose being replaced —
                    // a mark at a block index, a question quoting a sentence.
                    // Naming it, and counting it, is what makes this a
                    // decision rather than a surprise.
                    Text(
                        text = "That means losing $marksLost.",
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = Ink,
                    )
                }
                Text(
                    text = "The old chapter and its questions stay on disk as files." +
                        if (picturesRedrawn > 0) {
                            " Its pictures are drawn again from the new text — about " +
                                "$picturesRedrawn, ${pictureCost(picturesRedrawn)}."
                        } else {
                            ""
                        },
                    fontFamily = FontFamily.Serif,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Muted,
                )
                if (laterChapters.isNotEmpty()) {
                    // Not blocked, just said out loud: cascading the rewrite
                    // forward would spend real money on chapters the reader
                    // has not complained about.
                    val list = laterChapters.joinToString(", ")
                    Text(
                        text = if (laterChapters.size == 1) {
                            "Chapter $list already exists and was written against the " +
                                "old version of this one. It is left alone."
                        } else {
                            "Chapters $list already exist and were written against the " +
                                "old version of this one. They are left alone."
                        },
                        fontFamily = FontFamily.Serif,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Muted,
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
                            color = Muted,
                        )
                    },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = Ink,
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Paper,
                        unfocusedContainerColor = Paper,
                        cursorColor = Ink,
                        // The default indicator is the theme's purple.
                        focusedIndicatorColor = Ink,
                        unfocusedIndicatorColor = Muted,
                    ),
                )
        }
    }
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

/** "Chapter 6: ", "Chapter 6 — ", "Chapter 6." at the head of a title. */
internal val CHAPTER_NUMBER_PREFIX = Regex("""^\s*chapter\s+\d+\s*[:.–—-]\s*""", RegexOption.IGNORE_CASE)

/**
 * Drops a leading "Chapter N:" from a chapter's own title heading.
 *
 * The byline directly above it already reads "Chapter 6 · ~30 min · Day 8",
 * and it is there precisely so every chapter is numbered the same way
 * whatever the model wrote. Leaving the model's prefix in place puts the
 * number twice on two adjacent lines.
 *
 * Only the first inline is touched, and only if something is left afterwards
 * — a heading that is nothing but "Chapter 6" is the title, badly written,
 * and blanking it would be worse than repeating the number.
 */
internal fun stripChapterNumber(inlines: List<Inline>): List<Inline> {
    val first = inlines.firstOrNull() as? Inline.Text ?: return inlines
    val stripped = first.text.replaceFirst(CHAPTER_NUMBER_PREFIX, "")
    if (stripped == first.text) return inlines
    if (stripped.isBlank() && inlines.size == 1) return inlines
    return listOf(Inline.Text(stripped)) + inlines.drop(1)
}
