package com.inkfirst.app.ui

import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Rect as AndroidRect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import com.inkfirst.core.InkPoint
import com.inkfirst.core.Stroke
import android.graphics.PorterDuff
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Onyx-SDK-backed ink overlay for Boox devices. Wraps a native SurfaceView
 * with [TouchHelper], which renders stylus strokes DIRECTLY to the EPD
 * framebuffer at native latency (no Compose recomposition round-trip).
 *
 * Layer stack (bottom → top in Compose sibling order):
 *   1. SurfaceView + TouchHelper. `setZOrderOnTop(true)` puts it on top
 *      of every Compose sibling VISUALLY regardless of declaration order,
 *      so Onyx-EPD strokes are always visible above the Compose Canvas
 *      below. Onyx reads stylus input via a low-level EPD driver hook that
 *      is INDEPENDENT of Android view touch dispatch — so we can safely
 *      claim/consume stylus events at the Compose level without breaking
 *      the draw path.
 *   2. Compose Canvas rendering persisted strokes for the current page.
 *      Drawn UNDER the SurfaceView by z-order.
 *
 * A single [pointerInput] on the outer Box handles:
 *   - Stylus events → CONSUME them so the reader's page-turn gesture on
 *     the parent Box doesn't fire on stroke end (regression seen in the
 *     first Onyx attempt). Onyx SDK still receives the stylus via its
 *     EPD hook — Compose consumption doesn't reach that path.
 *   - Finger TAP on an existing stroke → fire onStrokeTapped and consume
 *     the stream (opens the tap-chip; parent page-turn skipped). A press
 *     HELD past TAP_MAX_MS is left alone even over a stroke, because that
 *     is a text selection starting.
 *   - Finger DOWN that misses → do NOT consume, letting the parent's
 *     page-turn gesture handle it normally.
 *
 * Erase does not come from the SDK: Onyx's onRawErasing callbacks have
 * never fired on the Boox Go 7 pen we tested. Instead the interop filter
 * below OWNS any contact whose MotionEvent says eraser tip or barrel
 * button, collects its path, erases whatever it crossed on release, and
 * asks for a panel unlock so the change (and the driver's own ink of the
 * sweep) becomes visible. The tap-chip's "Delete" button still works too.
 *
 * Phantom strokes (EPD-drawn stroke staying visible while the Compose
 * Canvas below also draws the newly-persisted stroke) are a known issue.
 * The proper fix is the bitmap+blit pattern from [onyx-pen-sdk] memory;
 * not yet implemented.
 *
 * On non-Boox devices [TouchHelper] init throws — caught and logged.
 * The overlay is then non-functional but the app doesn't crash.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun OnyxInkOverlay(
    pageStrokes: List<Stroke>,
    blockTopY: (globalIdx: Int) -> Float?,
    /**
     * The block's top as actually drawn on this page. Equals [blockTopY]
     * except for the continuation of a split paragraph, where [blockTopY]
     * reports a virtual top above the page so stored ink keeps one coordinate
     * space. A stroke rendering above this edge was drawn on a line that now
     * lives on the previous page, and belongs to that page alone.
     */
    blockVisibleTop: (globalIdx: Int) -> Float? = { null },
    pageBlockRange: IntRange,
    onStrokeAdded: (Stroke) -> Unit,
    onStrokeErased: (Stroke?) -> Unit,
    /**
     * Resolve what a finger contact means. Pure query, no side effects — it is
     * called BEFORE anything is acted on, because this overlay has to know the
     * meaning in advance to give the panel the right treatment (see the
     * ordering note in the finger branch).
     *
     * The overlay reports and resolves its own taps rather than letting them
     * fall through: in this mode the SDK is attached to the host view's touch
     * stream and the parent's detector never sees the release, so tap-to-turn
     * silently stopped working. But it does not DECIDE — the caller owns the
     * one ladder, which is what stops the two detectors drifting apart.
     *
     * Coordinates are overlay-local pixels plus the overlay's own width and
     * height, because the zone rule measures against them. [strokeHit] is the
     * stroke under the contact, hit-tested here since only this side holds the
     * page's ink.
     */
    resolveTap: (x: Float, y: Float, width: Float, height: Float, strokeHit: Stroke?) -> TapAction,
    /**
     * Resolve what a finger DRAG means, from its total displacement.
     *
     * Same argument as [resolveTap], for the contact shape the tap ladder
     * cannot describe. The caller owns the thresholds as well as the rules —
     * they are measured in dp and this overlay has no density to convert with.
     */
    resolveSwipe: (dx: Float, dy: Float) -> TapAction,
    /** Carry out what [resolveTap] or [resolveSwipe] decided. */
    onTapAction: (TapAction) -> Unit,
    /**
     * Whether a stroke chip is currently showing. Not used to decide anything
     * — that is [resolveTap]'s job — but a chip appearing or leaving needs the
     * panel unlocked, and only this side can ask for that.
     */
    chipOpen: Boolean = false,
    /**
     * Whether the pen may draw at all.
     *
     * False whenever a menu, panel or modal is open over the page. The SDK
     * inks at driver level — onto the glass, below Android's framebuffer but
     * visibly on top of whatever is rendered after it — so without this the
     * pen happily draws on the chapter underneath an open dialog. The
     * Add-note overlay is the worst of it: a box that invites you to write,
     * with the writing landing on the page behind it.
     */
    drawingEnabled: Boolean = true,
    /**
     * The chip's own bounds in overlay-local pixels, used to scope the repaint
     * that shows and hides it.
     *
     * Without this the chip was announced by GC-ing a full-width band a
     * quarter of the screen tall — a lot of panel to flash for one small pill,
     * and the last visible blink after the overlay repaint was scoped. Falls
     * back to the band when no chip has been laid out yet.
     */
    chipRect: AndroidRect? = null,
    modifier: Modifier = Modifier,
) {
    val addedRef = rememberUpdatedState(onStrokeAdded)
    val erasedRef = rememberUpdatedState(onStrokeErased)
    val resolveTapRef = rememberUpdatedState(resolveTap)
    val resolveSwipeRef = rememberUpdatedState(resolveSwipe)
    val drawEnabledRef = rememberUpdatedState(drawingEnabled)
    val chipRectRef = rememberUpdatedState(chipRect)
    val tapActionRef = rememberUpdatedState(onTapAction)
    val strokesRef = rememberUpdatedState(pageStrokes)
    val blockTopYRef = rememberUpdatedState(blockTopY)
    val blockVisibleTopRef = rememberUpdatedState(blockVisibleTop)
    val rangeRef = rememberUpdatedState(pageBlockRange)

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val touchHelperRef = remember { mutableStateOf<TouchHelper?>(null) }
    val startTouchRef = remember { mutableStateOf<TouchPoint?>(null) }
    // Screen position of the drawing surface, refreshed at each stroke start.
    val originRef = remember { mutableStateOf(0f to 0f) }
    /** Guards against the list and move callbacks both emitting one stroke. */
    val emittedRef = remember { mutableStateOf(false) }
    /** Whether a finger is on the glass right now, and when one last left it. */
    val fingerDownRef = remember { mutableStateOf(false) }
    // Seeded to "long ago" rather than 0 so the grace-period arithmetic is
    // meaningful before the first touch.
    val fingerUpAtRef = remember { mutableStateOf(0L) }
    /** Whether the pen is currently in contact, per the SDK's own signals. */
    val penActiveRef = remember { mutableStateOf(false) }
    /** Tool type of the most recent MotionEvent — the authoritative pen/finger signal. */
    val lastToolRef = remember { mutableStateOf(MotionEvent.TOOL_TYPE_UNKNOWN) }
    val penContactRef = remember { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()
    val unlockJobRef = remember { mutableStateOf<Job?>(null) }
    /**
     * True from the DOWN of an eraser contact (eraser tip, or barrel button
     * held) to its UP. Set and cleared only by the interop filter below,
     * which has to OWN that contact to see its UP at all.
     */
    val eraseModeRef = remember { mutableStateOf(false) }
    /** Points the eraser has passed over during the current contact. */
    val eraseTrail = remember { mutableListOf<Pair<Float, Float>>() }
    /**
     * Uptime when the last eraser contact lifted. Lets the raw commit path
     * recognise a barrel-button sweep whose SDK end callback lands a few ms
     * after the MotionEvent UP already cleared [eraseModeRef].
     */
    val eraseEndedAtRef = remember { mutableStateOf(0L) }
    /** Set once the SurfaceView is built, so the interop filter can ask for an unlock. */
    val unlockRequestRef = remember { mutableStateOf<((String) -> Unit)?>(null) }
    /**
     * Whether a repaint is owed, tracked separately from the unlock job that
     * asked for it. Unlock jobs supersede one another — each one cancels the
     * last — and a finger tap generates a raw-drawing cycle of its own, so
     * the `draw` unlock that follows a page tap by ~90ms was cancelling the
     * `pageTurn` unlock before it ever ran. Measured: 24 page taps, zero
     * repaints, and no log line either, because a cancelled job logs nothing.
     * The debt outlives the request: whichever unlock finally runs pays it.
     */
    val repaintPendingRef = remember { mutableStateOf(false) }
    /** Whether the driver's ink layer is owed a wipe. See [CLEAR_REASONS]. */
    val clearPendingRef = remember { mutableStateOf(false) }
    /** Overlay size in px, so a repaint can be scoped from outside a gesture. */
    val overlaySizeRef = remember { mutableStateOf(0 to 0) }
    /** Previous value of [chipOpen], to notice the transitions. */
    val chipWasOpenRef = remember { mutableStateOf(false) }

    /**
     * The region to repaint, or null for the whole view. Set by the erase
     * path, which knows exactly which pixels it changed.
     */
    val repaintRectRef = remember { mutableStateOf<AndroidRect?>(null) }

    /**
     * A SECOND region to repaint in the same unlock, kept separate from
     * [repaintRectRef] rather than merged with it.
     *
     * Deleting a stroke from its chip changes two far-apart places — the ink,
     * wherever it was on the page, and the chip pill at the bottom — and the
     * first version unioned them into one rectangle. Their bounding box is
     * everything in between, so a GC over it blinked most of the page to
     * delete one underline: the September lesson exactly, that the flash is
     * the SCOPE and not the mode. Two small rects cost two invalidates and
     * flash only what actually changed.
     *
     * Cleared when consumed, because unlike [repaintRectRef] — whose null
     * case means "repaint everything" and must therefore persist — an
     * absent second region simply means there is not one.
     */
    val extraRepaintRectRef = remember { mutableStateOf<AndroidRect?>(null) }

    // A chip both APPEARING and DISAPPEARING changes the panel, and neither
    // was being paid for. While raw drawing is live the driver owns the
    // panel and Compose's output sits unseen in the framebuffer, so the
    // enclosure chip was drawn and never shown — "almost never an auto chip"
    // — and then, closed by a button or a page turn, it left its pixels
    // behind as a chip-shaped ghost over the next page. The tap-chip path
    // asked for an unlock on open and nothing else did, which is why only
    // that one was reliable.
    //
    // Scoped to the band chips live in: a solid black pill ghosts hard, but
    // repainting the page to put one away is the flash we removed earlier.
    // Hand the pen to the page, or take it away when something opens over it.
    LaunchedEffect(drawingEnabled) {
        if (drawingEnabled) {
            try {
                touchHelperRef.value?.setRawDrawingEnabled(true)
            } catch (t: Throwable) {
                Log.w(TAG, "re-enable drawing failed", t)
            }
        } else {
            // Standing the driver down is not enough on its own: ink already
            // on the glass sits above whatever Compose renders next, so an
            // open dialog would wear the page's underlines across it.
            //
            // But only the INKED pixels can bleed, so only they need
            // repainting. The first version of this asked for the whole panel
            // and that is precisely the blink the erase work removed in
            // September: the flash was never the GC mode, it was the SCOPE.
            // A page with no ink on it — the common case when the format menu
            // opens — now costs nothing at all.
            val region = inkRegion(strokesRef.value, blockTopYRef.value)
            if (region == null) {
                try {
                    touchHelperRef.value?.setRawDrawingEnabled(false)
                } catch (t: Throwable) {
                    Log.w(TAG, "stand-down failed", t)
                }
                Log.d(TAG, "overlay opened over a clean page — no repaint")
            } else {
                repaintRectRef.value = region
                unlockRequestRef.value?.invoke("overlay")
            }
        }
    }

    /**
     * Ink can leave the page without the eraser: the chip's Delete, and now a
     * mark consumed by the question or the note it became.
     *
     * The driver keeps its own copy of every stroke below Android's
     * framebuffer, so removing one from the model changes nothing on the
     * glass — Compose redraws the page without it and the physical black
     * stays. The erase path already pays for this; these other routes did
     * not, and the more of them there are the more often a reader is left
     * looking at ink the app believes it deleted.
     *
     * Watched here rather than announced by the caller because this is the
     * only side that knows where a stroke's pixels are.
     */
    val lastStrokesRef = remember { mutableStateOf<List<Stroke>>(emptyList()) }
    val lastRangeRef = remember { mutableStateOf<IntRange?>(null) }
    LaunchedEffect(pageStrokes, pageBlockRange) {
        // Only within one page. Turning the page changes this list wholesale
        // and the departed strokes belong to blocks that have moved, so their
        // region is meaningless — and a page turn already wipes the layer.
        val samePage = lastRangeRef.value == pageBlockRange
        val gone = if (samePage) lastStrokesRef.value.filter { it !in pageStrokes } else emptyList()
        lastStrokesRef.value = pageStrokes
        lastRangeRef.value = pageBlockRange
        if (gone.isEmpty()) return@LaunchedEffect
        // An erase reaches here too, and has already scoped a repaint over
        // its sweep AND the strokes it took. Doing it again would be a
        // second flash for the same removal.
        if (SystemClock.uptimeMillis() - eraseEndedAtRef.value < ERASE_COMMIT_GRACE_MS) {
            return@LaunchedEffect
        }
        val region = inkRegion(gone, blockTopYRef.value) ?: return@LaunchedEffect
        // The PRIMARY rect when it is free, the second one when it is not.
        //
        // Which matters more than it looks: a null primary means "repaint the
        // whole panel", and the rect is consumed by whichever unlock runs
        // first. Deleting a stroke from its chip schedules two — one for the
        // chip leaving, one for the ink — so leaving the primary null here is
        // how one of them turns into a full-page flash.
        if (repaintRectRef.value == null) {
            repaintRectRef.value = region
        } else {
            extraRepaintRectRef.value = region
        }
        unlockRequestRef.value?.invoke("inkRemoved")
    }

    LaunchedEffect(chipOpen) {
        if (chipWasOpenRef.value != chipOpen) {
            // Let the pill lay out and report its bounds before scoping to
            // it. Without this the FIRST chip of a session repainted the
            // fallback band — a quarter of the screen — because the effect
            // ran before onGloballyPositioned had said where the chip was.
            if (chipOpen) {
                withFrameNanos {}
                withFrameNanos {}
            }
            val (w, h) = overlaySizeRef.value
            val known = chipRectRef.value
            if (known != null) {
                // The pill itself, padded the same as an erase rect.
                repaintRectRef.value = AndroidRect(
                    (known.left - REPAINT_PAD_PX.toInt()).coerceAtLeast(0),
                    (known.top - REPAINT_PAD_PX.toInt()).coerceAtLeast(0),
                    known.right + REPAINT_PAD_PX.toInt(),
                    known.bottom + REPAINT_PAD_PX.toInt(),
                )
            } else if (w > 0 && h > 0) {
                // Nothing measured yet — fall back to the band.
                repaintRectRef.value = AndroidRect(
                    0,
                    (h * (1f - CHIP_BAND_FRACTION)).toInt().coerceAtLeast(0),
                    w,
                    h,
                )
            }
            unlockRequestRef.value?.invoke(if (chipOpen) "chipShow" else "chipClose")
        }
        chipWasOpenRef.value = chipOpen
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlaySizeRef.value = it.size.width to it.size.height }
            // Read the tool type straight off the MotionEvent, the way
            // InkOverlay already does for barrel-button and eraser-tip
            // detection. Compose's PointerType reports this firmware's pen as
            // Touch, and the SDK's callbacks carry no tool type at all — but
            // the MotionEvent does, and it is authoritative. This replaces a
            // timing heuristic that guessed "a stroke arriving near a touch is
            // that touch", which misfired both ways.
            //
            // Returns false: this only observes, it never consumes.
            .pointerInteropFilter { event ->
                val tool = event.getToolType(0)
                lastToolRef.value = tool
                val isStylus = tool == MotionEvent.TOOL_TYPE_STYLUS ||
                    tool == MotionEvent.TOOL_TYPE_ERASER
                if (isStylus) {
                    penContactRef.value = event.actionMasked != MotionEvent.ACTION_UP &&
                        event.actionMasked != MotionEvent.ACTION_CANCEL
                }
                // The Boolean this lambda returns follows View.onTouchEvent
                // rules: answering `false` to a DOWN means "not mine", and
                // Compose then delivers NOTHING more from that contact — no
                // MOVE, no UP. So an eraser contact has to be claimed with
                // `true` on its DOWN, or its path and its release are never
                // seen. (The previous version answered false to everything;
                // its MOVE/UP branches were dead code, and the erase flag it
                // latched on DOWN could never be cleared, which turned every
                // later pen stroke into an erase sweep.) Pen and finger
                // contacts still get `false`, so the raw path and the finger
                // gestures below are untouched.
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // Decided once per contact, from its DOWN. The SDK's
                        // own erase callbacks (onBeginRawErasing and friends)
                        // have never fired on this device, so erase is read
                        // off the MotionEvent — the way InkOverlay does, and
                        // that is proven on this hardware.
                        val erasing = tool == MotionEvent.TOOL_TYPE_ERASER ||
                            (isStylus &&
                                (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0)
                        eraseModeRef.value = erasing
                        eraseTrail.clear()
                        if (erasing) {
                            eraseTrail += event.x to event.y
                            Log.d(TAG, "erase mode armed (tool=$tool button=${event.buttonState})")
                        }
                        erasing
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (eraseModeRef.value) {
                            // Historical samples too: a fast sweep batches
                            // several positions into one MOVE, and skipping
                            // them is how a sweep jumps over a stroke.
                            for (h in 0 until event.historySize) {
                                eraseTrail += event.getHistoricalX(h) to event.getHistoricalY(h)
                            }
                            eraseTrail += event.x to event.y
                        }
                        eraseModeRef.value
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (eraseModeRef.value) {
                            eraseTrail += event.x to event.y
                            val getTop = blockTopYRef.value
                            val doomed = LinkedHashSet<Stroke>()
                            eraseTrail.forEach { (x, y) ->
                                hitTestForErase(
                                    strokesRef.value, x, y, getTop,
                                    blockVisibleTop = blockVisibleTopRef.value,
                                )?.let(doomed::add)
                            }
                            Log.d(TAG, "erase trail pts=${eraseTrail.size} removed=${doomed.size}")
                            // Only these pixels changed: where the eraser
                            // swept (the driver inked that trail too) plus
                            // the strokes it removed. Repainting the region
                            // instead of the view is what keeps an erase from
                            // blinking the whole page black and back.
                            repaintRectRef.value = eraseRegion(eraseTrail, doomed, getTop)
                            eraseTrail.clear()
                            eraseModeRef.value = false
                            eraseEndedAtRef.value = SystemClock.uptimeMillis()
                            if (doomed.isEmpty()) {
                                // A miss still reports, with null, so an open
                                // chip can be dismissed by an eraser tap in
                                // empty space — same contract as InkOverlay.
                                erasedRef.value(null)
                            } else {
                                doomed.forEach(erasedRef.value)
                            }
                            // The model changed, but the panel is frozen while
                            // the driver holds it, and the driver may have
                            // inked the sweep itself. The unlock shows the
                            // former and wipes the latter.
                            unlockRequestRef.value?.invoke("eraseTrail")
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    when (down.type) {
                        PointerType.Stylus, PointerType.Eraser -> {
                            // Consume the DOWN only, so the parent's page-turn
                            // gesture ignores this contact — but let every
                            // MOVE through untouched.
                            //
                            // Swallowing the moves is what limited a stroke to
                            // its begin and end sample: FEATURE_APP_PEN_TOUCH_RENDER
                            // still routes pen motion through normal Android
                            // dispatch, and consuming it here starved the SDK's
                            // move callback. Symptom was backwards-looking — a
                            // quick flick reported 20+ points while a slow,
                            // deliberate line reported 2 — because the longer
                            // the contact, the more moves we ate.
                            down.consume()
                            // Bounded, and it only consumes the RELEASE.
                            //
                            // Consuming the moves starves the SDK (that was
                            // the one-point-per-stroke bug), but the release
                            // has to be consumed or the parent reads the pen
                            // stroke as a tap and turns the page.
                            //
                            // The timeout is the important part: Onyx takes
                            // the pen at driver level, so Compose is not
                            // guaranteed to ever see the release. Without a
                            // bound this loop hung forever and awaitEachGesture
                            // never restarted — which is why tap-on-stroke
                            // worked on a fresh page and then died the moment
                            // anything was drawn.
                            withTimeoutOrNull(PEN_CONTACT_TIMEOUT_MS) {
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull() ?: break
                                    if (!ch.pressed) {
                                        ch.consume()
                                        break
                                    }
                                }
                            }
                        }
                        PointerType.Touch, PointerType.Mouse -> {
                            // This firmware reports the pen as Touch here, so
                            // Compose's type is not trustworthy — consult the
                            // MotionEvent tool type instead. A pen contact must
                            // not be treated as a tap, and must not suppress
                            // its own stroke.
                            val tool = lastToolRef.value
                            if (tool == MotionEvent.TOOL_TYPE_STYLUS ||
                                tool == MotionEvent.TOOL_TYPE_ERASER
                            ) {
                                return@awaitEachGesture
                            }
                            fingerDownRef.value = true
                            fingerUpAtRef.value = System.currentTimeMillis()
                            val at = down.position
                            val hit = hitTestForErase(
                                strokesRef.value, at.x, at.y, blockTopYRef.value,
                                TAP_SLOP_PX,
                                blockVisibleTopRef.value,
                            )
                            Log.d(
                                TAG,
                                "finger down at=(${at.x},${at.y}) strokes=${strokesRef.value.size} hit=${hit != null}",
                            )
                            // try/finally, not plain sequence: a tap that
                            // turns the page recomposes this overlay and
                            // CANCELS the gesture coroutine mid-way. Without
                            // the finally the "finger is down" flag latched on
                            // forever, and every pen stroke after the first
                            // page turn was discarded as if a finger had made
                            // it — the pen appeared to die after one tap.
                            try {
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                // Before the first layout pass there is no
                                // geometry to resolve a zone against.
                                if (w > 0f) {
                                // WATCH the contact; do not decide on its DOWN.
                                //
                                // Deciding on the DOWN is what killed every
                                // swipe on this path: a contact in a turn band
                                // had already turned the page before the finger
                                // had moved far enough to be a drag, so
                                // swipe-to-turn and swipe-up-for-next-chapter
                                // both simply stopped existing the day the SDK
                                // took over the touch stream.
                                //
                                // What the three outcomes mean:
                                //   lifted quickly      — a tap. Resolve by position.
                                //   moved past the slop — a drag. Resolve by direction.
                                //   held, and still down — a press. Ours to leave
                                //     alone: it is a text selection starting, or a
                                //     finger resting on the page, and acting on
                                //     either is how a rested hand turns pages.
                                val dragStartPx = 10.dp.toPx()
                                var dx = 0f
                                var dy = 0f
                                var dragging = false
                                var release: PointerInputChange? = null
                                withTimeoutOrNull(TAP_MAX_MS) {
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull() ?: break
                                        val pc = ch.positionChange()
                                        dx += pc.x
                                        dy += pc.y
                                        if (!ch.pressed) { release = ch; break }
                                        if (kotlin.math.abs(dx) > dragStartPx ||
                                            kotlin.math.abs(dy) > dragStartPx
                                        ) {
                                            dragging = true
                                            break
                                        }
                                    }
                                }
                                if (dragging) {
                                    // A deliberate swipe outlasts the tap
                                    // window, so follow it to its end on a
                                    // bound of its own. If the release never
                                    // arrives the displacement collected so
                                    // far still says what was meant.
                                    withTimeoutOrNull(DRAG_CONTACT_TIMEOUT_MS) {
                                        while (true) {
                                            val ev = awaitPointerEvent()
                                            val ch = ev.changes.firstOrNull() ?: break
                                            val pc = ch.positionChange()
                                            dx += pc.x
                                            dy += pc.y
                                            if (!ch.pressed) { release = ch; break }
                                        }
                                    }
                                }
                                if (!dragging && release == null) {
                                    Log.d(TAG, "finger held past ${TAP_MAX_MS}ms — left for selection")
                                    return@awaitEachGesture
                                }
                                // The RELEASE is what has to be consumed, and
                                // it only became possible to consume it once
                                // this branch waited for it.
                                //
                                // The reader's own detector resolves on the
                                // release and skips a contact a child already
                                // claimed. While this overlay decided on the
                                // DOWN, the two never collided: acting
                                // recomposed the reader, which re-keyed its
                                // pointerInput and cancelled its gesture
                                // before the release arrived. Deciding on the
                                // release removes that accident, and without
                                // this line both detectors would carry out the
                                // same page turn — two pages for one tap.
                                release?.consume()
                                val action = if (dragging) {
                                    resolveSwipeRef.value(dx, dy)
                                } else {
                                    resolveTapRef.value(at.x, at.y, w, h, hit)
                                }
                                Log.d(
                                    TAG,
                                    "finger ${if (dragging) "drag d=($dx,$dy)" else "tap at=(${at.x},${at.y})"} " +
                                        "w=$w h=$h -> $action",
                                )
                                when (action) {
                                    is TapAction.ShowStrokeChip -> {
                                        down.consume()
                                        tapActionRef.value(action)
                                        // The chip is Compose UI, and while raw
                                        // drawing is live the driver owns the
                                        // panel: Compose's output lands in the
                                        // framebuffer and is not shown. Without
                                        // this the chip appeared only at the NEXT
                                        // refresh — a page turn, or the unlock
                                        // after some later stroke — which read as
                                        // "tap does nothing". Any UI a finger
                                        // opens needs this.
                                        unlockRequestRef.value?.invoke("chip")
                                    }
                                    TapAction.DismissChip,
                                    TapAction.ClearSelection -> {
                                        down.consume()
                                        tapActionRef.value(action)
                                        // A chip is a solid black pill, so it
                                        // ghosts harder than ink does — it leaves
                                        // a pale rectangle where it sat. Scope the
                                        // clear to the band it lives in (bottom,
                                        // full width) rather than the whole page:
                                        // same reasoning as the erase rect, since
                                        // a page-wide GC to put one chip away is
                                        // the flash we just spent the morning
                                        // removing.
                                        repaintRectRef.value = chipRectRef.value?.let { r ->
                                            AndroidRect(
                                                (r.left - REPAINT_PAD_PX.toInt()).coerceAtLeast(0),
                                                (r.top - REPAINT_PAD_PX.toInt()).coerceAtLeast(0),
                                                r.right + REPAINT_PAD_PX.toInt(),
                                                r.bottom + REPAINT_PAD_PX.toInt(),
                                            )
                                        } ?: AndroidRect(
                                            0,
                                            (size.height * (1f - CHIP_BAND_FRACTION)).toInt()
                                                .coerceAtLeast(0),
                                            size.width,
                                            size.height,
                                        )
                                        unlockRequestRef.value?.invoke("chipClose")
                                    }
                                    // The dead middle. Still REPORTED — an
                                    // earlier version swallowed it here, which
                                    // is why a centre tap could not dismiss
                                    // anything: nothing downstream ever heard
                                    // about it. What it skips is the unlock,
                                    // because that wipes the driver's ink
                                    // layer and a contact that means nothing
                                    // must not cost the reader ink already on
                                    // the page.
                                    TapAction.Ignore -> tapActionRef.value(action)
                                    else -> {
                                        // BEFORE the action is carried out, not
                                        // after. A page turn replaces everything
                                        // on the panel, including ink the driver
                                        // painted for the page being left;
                                        // nothing else unlocks here, so that ink
                                        // stayed on the glass over the new page —
                                        // the "line from the previous page, cut
                                        // off at the top" report.
                                        //
                                        // Ordering is the whole fix: acting
                                        // recomposes this overlay and cancels
                                        // this gesture coroutine (see the
                                        // try/finally above), so a call placed
                                        // after it never runs. Measured: 24 page
                                        // taps, zero unlocks. scheduleUnlock
                                        // launches in a scope that outlives the
                                        // gesture, and its debounce means the
                                        // repaint lands after the new page is up.
                                        //
                                        // Every non-chip rung shares this
                                        // treatment, which is what the old code
                                        // did too: it unlocked once, here, and
                                        // let the caller sort out menu from
                                        // marker from page turn.
                                        unlockRequestRef.value?.invoke("pageTurn")
                                        tapActionRef.value(action)
                                    }
                                }
                                }
                            } finally {
                                fingerDownRef.value = false
                                fingerUpAtRef.value = System.currentTimeMillis()
                            }
                        }
                        else -> { /* Unknown type — ignore */ }
                    }
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    setZOrderOnTop(true)
                    holder.setFormat(PixelFormat.TRANSPARENT)

                    val surface = this
                    // Onyx reports raw points in SCREEN coordinates, while
                    // everything on our side — block tops, stored stroke
                    // points, the Compose Canvas that redraws them — is in
                    // overlay-local space. Read the offset at the START of
                    // each stroke rather than once at surface creation: the
                    // value captured in surfaceCreated came back as
                    // (670,1054) for a view that sits near (38,200), because
                    // the surface is created before the final layout pass.
                    // Onyx reports raw points in VIEW-LOCAL coordinates, the
                    // same space Compose uses — proved by a pen contact and
                    // the Compose touch it generates reporting identical
                    // numbers, (1087,174), 2ms apart. An earlier reading of
                    // the log had this backwards and subtracted the view
                    // origin, which pushed every stored stroke (38,256) away
                    // from where a finger taps: strokes rendered displaced,
                    // hit-testing never matched, and one stroke logged a
                    // negative y of -205. No conversion is needed.
                    //
                    // Note this is the opposite of setLimitRect, which also
                    // wants view-local — the two are consistent; it was only
                    // the "points are screen space" reading that was wrong.
                    fun originOnScreen(): Pair<Float, Float> = 0f to 0f

                    // Samples collected during one pen-down, in screen space.
                    val sampleBuf = mutableListOf<TouchPoint>()

                    /**
                     * While raw drawing is enabled the driver owns the panel
                     * and ordinary posts are not shown — anything Compose
                     * draws lands in the framebuffer invisibly. Disabling and
                     * re-enabling releases it; re-enabling does not re-freeze
                     * until the next stroke begins.
                     *
                     * One mechanism behind three symptoms that looked
                     * unrelated: driver ink surviving an app restart, the
                     * Compose redraw doubling with the driver's copy, and
                     * erase appearing to do nothing while correctly editing
                     * the model.
                     *
                     * Unlocking before the frame carrying the change is
                     * committed flushes the pre-stroke buffer and makes the
                     * ink flash, hence the debounce and the two frame waits.
                     */
                    /**
                     * Wipe the SurfaceView the driver paints raw ink into.
                     * Only safe with raw drawing disabled — the driver owns
                     * the surface otherwise — so every caller sits between a
                     * `setRawDrawingEnabled(false)` and its matching `(true)`.
                     */
                    fun clearSurface(dirty: AndroidRect?) {
                        val holder = surface.holder
                        val canvas = try {
                            // Scoped to the region that changed where we know
                            // it. Wiping the WHOLE layer took every surviving
                            // stroke on the page with it, leaving them to be
                            // repainted from the Compose copy — which is the
                            // blink that erasing one underline produced.
                            if (dirty != null) holder.lockCanvas(dirty) else holder.lockCanvas()
                        } catch (t: Throwable) {
                            Log.w(TAG, "clearSurface: lockCanvas failed", t)
                            null
                        } ?: return
                        try {
                            canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                        } finally {
                            try {
                                holder.unlockCanvasAndPost(canvas)
                            } catch (t: Throwable) {
                                Log.w(TAG, "clearSurface: post failed", t)
                            }
                        }
                    }

                    fun cancelUnlock() {
                        unlockJobRef.value?.cancel()
                        unlockJobRef.value = null
                    }

                    fun scheduleUnlock(reason: String) {
                        if (reason in REPAINT_REASONS) {
                            repaintPendingRef.value = true
                            Log.d(TAG, "repaint armed($reason)")
                        }
                        if (reason in CLEAR_REASONS) {
                            clearPendingRef.value = true
                            // A page turn replaces the whole page, so the
                            // whole layer goes — never a leftover rect from
                            // some earlier erase.
                            if (reason == "pageTurn") repaintRectRef.value = null
                            Log.d(TAG, "clear armed($reason)")
                        }
                        unlockJobRef.value?.cancel()
                        unlockJobRef.value = uiScope.launch {
                            // Ink needs the debounce — unlocking before the
                            // frame carrying a stroke is committed flushes the
                            // buffer and makes it flash — but a chip is
                            // ordinary UI and only needs its frame.
                            //
                            // Waiting 200ms for one made it unpredictable:
                            // every scheduleUnlock cancels the last, so
                            // starting the next stroke inside that window
                            // cancelled the chip's unlock outright, and the
                            // chip surfaced later, mid-stroke, half-drawn, or
                            // seemingly at random. Two frames is enough, and
                            // leaves almost no window to be cancelled in.
                            if (reason !in IMMEDIATE_REASONS) delay(UNLOCK_DEBOUNCE_MS)
                            withFrameNanos {}
                            withFrameNanos {}
                            try {
                                touchHelperRef.value?.let {
                                    it.setRawDrawingEnabled(false)
                                    // With the driver stood down, wipe the
                                    // layer it drew into. Toggling alone
                                    // never cleared it: the driver keeps its
                                    // own copy of every stroke on the panel,
                                    // below Android's framebuffer — which is
                                    // why a leftover stroke is invisible to
                                    // screencap and cannot be tapped, and why
                                    // it survives a page turn to sit over
                                    // whatever text arrives next.
                                    if (clearPendingRef.value) {
                                        clearPendingRef.value = false
                                        clearSurface(repaintRectRef.value)
                                        extraRepaintRectRef.value?.let(::clearSurface)
                                    }
                                    // Not unconditional: an unlock that
                                    // fires while a dialog is open must not
                                    // hand the pen back to the page beneath it.
                                    if (drawEnabledRef.value) it.setRawDrawingEnabled(true)
                                }
                                Log.d(TAG, "unlock($reason)")
                                // Releasing the panel is not repainting it.
                                // The driver inked those pixels directly into
                                // the framebuffer, so they survive every
                                // redraw that merely posts white over them:
                                // Compose drawing the page WITHOUT an erased
                                // stroke asks the panel to put white where it
                                // already believes white is, and the physical
                                // black stays. Hence ghosts of erased strokes,
                                // and fragments of the previous page's ink
                                // surviving a page turn.
                                //
                                // A full-quality refresh is the only thing
                                // that clears them, and it is only worth its
                                // flash when something was actually REMOVED
                                // from the panel — never after an ordinary
                                // stroke, which would flash the page on every
                                // pen lift.
                                if (repaintPendingRef.value) {
                                    repaintPendingRef.value = false
                                    // Repaint the ROOT view, not this one.
                                    // The SurfaceView is transparent and sits
                                    // z-ordered on top; the page itself is
                                    // drawn by Compose UNDERNEATH it. Asking
                                    // the EPD to refresh this view refreshed
                                    // an empty transparent layer, so every
                                    // repaint was a well-aimed no-op — the
                                    // logs showed GC firing over sane rects
                                    // while the ghosts sat there untouched.
                                    val target = surface.rootView ?: surface
                                    val r = repaintRectRef.value
                                    if (r != null) {
                                        // Overlay-local → root-local: the
                                        // rect was measured against this view.
                                        val loc = IntArray(2)
                                        surface.getLocationInWindow(loc)
                                        val mode = REPAINT_MODE
                                        EpdController.invalidate(
                                            target,
                                            r.left + loc[0], r.top + loc[1],
                                            r.right + loc[0], r.bottom + loc[1],
                                            mode,
                                        )
                                        Log.d(
                                            TAG,
                                            "repaint($reason, $mode, rect=$r +${loc[0]},${loc[1]})",
                                        )
                                        // The second region, if this unlock
                                        // carries one. Its own invalidate, not
                                        // a bounding box with the first.
                                        extraRepaintRectRef.value?.let { x ->
                                            EpdController.invalidate(
                                                target,
                                                x.left + loc[0], x.top + loc[1],
                                                x.right + loc[0], x.bottom + loc[1],
                                                mode,
                                            )
                                            Log.d(TAG, "repaint($reason, $mode, extra=$x)")
                                        }
                                    } else {
                                        // Nothing to scope it to: refresh the
                                        // panel outright. repaintEveryThing
                                        // needs no view at all, so it cannot
                                        // be aimed at the wrong one.
                                        val mode = REPAINT_MODE
                                        EpdController.repaintEveryThing(mode)
                                        Log.d(TAG, "repaint($reason, $mode, everything)")
                                    }
                                    repaintRectRef.value = null
                                    extraRepaintRectRef.value = null
                                }
                            } catch (t: Throwable) {
                                Log.w(TAG, "unlock($reason) failed", t)
                            }
                        }
                    }

                    unlockRequestRef.value = { reason -> scheduleUnlock(reason) }

                    fun emitStroke(pts: List<TouchPoint>, source: String) {
                        if (pts.isEmpty()) return
                        // Ink only from the pen. Tool type comes from the
                        // MotionEvent via pointerInteropFilter — the raw
                        // callbacks carry none, and the SDK's own
                        // enableFingerTouch(false) disables the whole reader
                        // on this firmware (pen included), so it is unusable.
                        val tool = lastToolRef.value
                        if (tool != MotionEvent.TOOL_TYPE_STYLUS &&
                            tool != MotionEvent.TOOL_TYPE_ERASER
                        ) {
                            Log.d(TAG, "emit($source) n=${pts.size} dropped — tool=$tool not a pen")
                            // Unlock anyway: the driver already inked this on
                            // the panel and nothing will redraw over it.
                            scheduleUnlock("fingerStroke")
                            return
                        }
                        // Belt and braces: the primary erase path is the
                        // interop filter's MotionEvent trail; this only has
                        // to keep the eraser sweep from being COMMITTED as
                        // ink. Eraser tip: the tool type says so. Barrel
                        // button: the tool type says stylus, so rely on the
                        // erase flag — plus a short grace, because this runs
                        // on the SDK thread and can land a few ms after the
                        // UP that already cleared the flag.
                        val sinceEraseUp = SystemClock.uptimeMillis() - eraseEndedAtRef.value
                        if (eraseModeRef.value ||
                            lastToolRef.value == MotionEvent.TOOL_TYPE_ERASER ||
                            sinceEraseUp < ERASE_COMMIT_GRACE_MS
                        ) {
                            // Treat the gesture as an eraser sweep: anything
                            // it crosses goes. The driver has already inked the
                            // sweep itself on the panel; the unlock wipes it,
                            // because nothing about it is committed.
                            val getTopErase = blockTopYRef.value
                            val doomed = LinkedHashSet<Stroke>()
                            pts.forEach { p ->
                                hitTestForErase(strokesRef.value, p.x, p.y, getTopErase)
                                    ?.let(doomed::add)
                            }
                            // Why nothing matched, in numbers: where the sweep
                            // actually went versus where the strokes actually
                            // are, in the same coordinate space.
                            val sxs = pts.map { it.x }
                            val sys = pts.map { it.y }
                            Log.d(
                                TAG,
                                "erase sweep n=${pts.size} removed=${doomed.size} " +
                                    "sweepBox=(${sxs.min().toInt()},${sys.min().toInt()})-" +
                                    "(${sxs.max().toInt()},${sys.max().toInt()}) " +
                                    "strokes=${strokesRef.value.size}",
                            )
                            strokesRef.value.take(4).forEach { st ->
                                val top = getTopErase(st.blockIndex)
                                if (top == null) {
                                    Log.d(TAG, "  stroke blk=${st.blockIndex} top=NULL (skipped)")
                                } else {
                                    val xs2 = st.points.map { it.x }
                                    val ys2 = st.points.map { it.y + top }
                                    Log.d(
                                        TAG,
                                        "  stroke blk=${st.blockIndex} top=${top.toInt()} " +
                                            "box=(${xs2.min().toInt()},${ys2.min().toInt()})-" +
                                            "(${xs2.max().toInt()},${ys2.max().toInt()})",
                                    )
                                }
                            }
                            if (doomed.isNotEmpty()) {
                                mainHandler.post { doomed.forEach(erasedRef.value) }
                            }
                            scheduleUnlock("eraseSweep")
                            return
                        }
                        val (ox, oy) = originRef.value
                        val xs = pts.map { it.x }
                        val ys = pts.map { it.y }
                        // Anchor on the stroke's MIDDLE, not where the pen
                        // happened to land. A circle drawn around text on a
                        // paragraph's first line often starts in the gap
                        // ABOVE it — paragraph spacing is ~37px — so the
                        // "greatest top <= y" rule picked the PREVIOUS block,
                        // and every downstream step then worked faithfully on
                        // the wrong paragraph: "Let us start right at the
                        // beginning. What is an AI, really?" came back for a
                        // mark made two lines below it. The start point is
                        // arbitrary for any shape that is not a left-to-right
                        // underline; the vertical centre is inside the marked
                        // text for underlines, circles and margin bars alike.
                        val centreLocalY = ((ys.min() + ys.max()) / 2f) - oy
                        val startLocalY = pts.first().y - oy
                        val getTop = blockTopYRef.value
                        val anchorIdx = findAnchorBlockOnyx(rangeRef.value, getTop, centreLocalY)
                        Log.d(
                            TAG,
                            "emit($source) n=${pts.size} centreY=$centreLocalY startY=$startLocalY " +
                                "anchor=$anchorIdx " +
                                "extent=${(xs.max() - xs.min()).toInt()}x${(ys.max() - ys.min()).toInt()}px",
                        )
                        if (anchorIdx == null) {
                            Log.w(TAG, "emit($source): no anchor for centreY=$centreLocalY — dropped")
                            return
                        }
                        val anchorY = getTop(anchorIdx) ?: 0f
                        // A single sample is a dot, not a failure — and a
                        // degenerate one-point path draws nothing, so give it
                        // a second point to round-cap against.
                        val source2 = if (pts.size == 1) pts + pts else pts
                        val stroke = Stroke(
                            blockIndex = anchorIdx,
                            points = source2.map {
                                InkPoint(
                                    x = it.x - ox,
                                    y = (it.y - oy) - anchorY,
                                    pressure = it.pressure,
                                )
                            },
                            brush = "pen",
                            created = LocalDate.now().toString(),
                        )
                        mainHandler.post { addedRef.value(stroke) }
                    }

                    val callback = object : RawInputCallback() {
                        override fun onBeginRawDrawing(shortcut: Boolean, point: TouchPoint) {
                            cancelUnlock()   // don't refresh mid-writing
                            originRef.value = originOnScreen()
                            Log.d(TAG, "onBeginRawDrawing local=(${point.x},${point.y})")
                            startTouchRef.value = point
                            emittedRef.value = false
                            sampleBuf.clear()
                            sampleBuf += point
                        }

                        override fun onEndRawDrawing(outOfLimit: Boolean, point: TouchPoint) {
                            sampleBuf += point
                            Log.d(
                                TAG,
                                "onEndRawDrawing outOfLimit=$outOfLimit samples=${sampleBuf.size}",
                            )
                            // The list callback is the documented way to get a
                            // finished stroke, but on this firmware it only
                            // ever delivered one point — every line came out a
                            // dot. The per-sample move callback is where the
                            // stroke actually lives, so accumulate there and
                            // emit here. The list callback still runs and wins
                            // if it turns out to carry more.
                            // The list callback already fired for this
                            // stroke on this firmware; emitting again saved
                            // every stroke twice.
                            if (!emittedRef.value) emitStroke(sampleBuf.toList(), "moves")
                            emittedRef.value = false
                            sampleBuf.clear()
                            scheduleUnlock("draw")
                        }

                        override fun onRawDrawingTouchPointMoveReceived(point: TouchPoint) {
                            sampleBuf += point
                        }
                        override fun onRawDrawingTouchPointListReceived(list: TouchPointList) {
                            val pts = list.points
                            Log.d(TAG, "pointList n=${pts?.size ?: 0}")
                            // Only worth using when it actually beats what the
                            // move callback collected.
                            if (pts != null && pts.size > 2) {
                                emittedRef.value = true
                                emitStroke(pts, "list")
                            }
                        }

                        // Never overridden until now. onPenUpRefresh is the
                        // pen-lift signal Compose never delivers — it hands
                        // back the rect the pen just dirtied, which is both
                        // the release we need and the hook for clearing the
                        // EPD ghost. onPenActive fires when the pen comes
                        // into range, before any contact.
                        override fun onPenActive(point: TouchPoint) {
                            penActiveRef.value = true
                            Log.d(TAG, "onPenActive at=(${point.x},${point.y})")
                        }

                        override fun onPenUpRefresh(refreshRect: RectF) {
                            penActiveRef.value = false
                            Log.d(TAG, "onPenUpRefresh rect=$refreshRect")
                        }

                        override fun onBeginRawErasing(shortcut: Boolean, point: TouchPoint) {
                            // Erasing can be the first thing that happens, so
                            // the origin may never have been read yet.
                            originRef.value = originOnScreen()
                            Log.d(TAG, "onBeginRawErasing shortcut=$shortcut origin=${originRef.value}")
                        }
                        override fun onEndRawErasing(outOfLimit: Boolean, point: TouchPoint) {
                            Log.d(TAG, "onEndRawErasing outOfLimit=$outOfLimit")
                            // Erasing edits the model, but nothing redraws
                            // while the panel is frozen — which is exactly why
                            // erase looked like it did nothing at all.
                            scheduleUnlock("erase")
                        }
                        override fun onRawErasingTouchPointMoveReceived(point: TouchPoint) {}
                        override fun onRawErasingTouchPointListReceived(list: TouchPointList) {
                            val pts = list.points
                            Log.d(TAG, "onRawErasing pts=${pts?.size}")
                            if (pts.isNullOrEmpty()) return
                            // Erase whatever the eraser passed over. Points
                            // arrive in screen space like drawing ones, so
                            // they need the same conversion before hit testing.
                            val (ox, oy) = originRef.value
                            val getTop = blockTopYRef.value
                            val doomed = LinkedHashSet<Stroke>()
                            pts.forEach { p ->
                                hitTestForErase(
                                    strokesRef.value, p.x - ox, p.y - oy, getTop,
                                )?.let(doomed::add)
                            }
                            Log.d(TAG, "onRawErasing matched ${doomed.size} stroke(s)")
                            if (doomed.isNotEmpty()) {
                                mainHandler.post { doomed.forEach(erasedRef.value) }
                            }
                        }
                    }

                    // Feature mode = pen-only, app-rendered. Hypothesis: this
                    // routes only stylus events through the raw-drawing hook,
                    // leaving finger touches on the normal Android dispatch
                    // path so Compose can see them (tap-on-stroke chip,
                    // finger page-turn). If this doesn't do it, try the other
                    // FEATURE_ constants — see javap in the aar for the set.
                    val helper = TouchHelper.create(
                        this,
                        ONYX_FEATURE_MODE,
                        callback,
                        ONYX_CREATE_FLAG,
                    )
                    touchHelperRef.value = helper
                    Log.d(
                        TAG,
                        "TouchHelper.create returned=$helper mode=$ONYX_FEATURE_MODE flag=$ONYX_CREATE_FLAG",
                    )
                    // The drawable region has to be given in SCREEN
                    // coordinates. It was being given view-local (0,0,w,h),
                    // which put the live area in the wrong place — the pen
                    // only drew where the two regions happened to overlap.
                    //
                    // Deferred with post{}: getLocationOnScreen called from
                    // surfaceCreated returned (670,1054) for a view that sits
                    // near (38,200), because the surface exists before the
                    // final layout pass has positioned it.
                    fun applyLimitRect(reason: String) {
                        surface.post {
                            try {
                                // VIEW-LOCAL, not screen. Onyx offsets the
                                // rect by the view's own position internally,
                                // so passing screen coordinates double-counts
                                // it: the correct-looking Rect(38,256..) put
                                // the live region in the bottom-right corner.
                                // The offset is still read and logged, because
                                // the raw POINTS do arrive in screen space and
                                // have to be converted the other way.
                                val loc = IntArray(2)
                                surface.getLocationOnScreen(loc)
                                val bounds = AndroidRect(0, 0, surface.width, surface.height)
                                helper.setLimitRect(bounds, emptyList())
                                Log.d(TAG, "  (view origin on screen = ${loc[0]},${loc[1]})")
                                if (drawEnabledRef.value) helper.setRawDrawingEnabled(true)
                                Log.d(TAG, "limitRect($reason) viewLocal=$bounds")
                            } catch (t: Throwable) {
                                Log.w(TAG, "limitRect($reason) failed", t)
                            }
                        }
                    }

                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) {
                            try {
                                helper.setStrokeWidth(3f).openRawDrawing()
                                helper.setStrokeStyle(TouchHelper.STROKE_STYLE_PENCIL)
                                if (drawEnabledRef.value) helper.setRawDrawingEnabled(true)
                                Log.d(TAG, "TouchHelper opened ${width}x$height")
                                applyLimitRect("created")
                            } catch (t: Throwable) {
                                Log.w(TAG, "TouchHelper init failed", t)
                            }
                        }
                        override fun surfaceChanged(
                            h: SurfaceHolder, format: Int, w: Int, ht: Int,
                        ) {
                            applyLimitRect("changed")
                        }
                        override fun surfaceDestroyed(h: SurfaceHolder) {
                            try { helper.setRawDrawingEnabled(false) } catch (_: Throwable) {}
                        }
                    })

                    // A font-size or margin change re-lays out the reader and
                    // moves this surface; the drawable region has to follow it.
                    addOnLayoutChangeListener { _, l, t, _, _, oldL, oldT, _, _ ->
                        if (l != oldL || t != oldT) applyLimitRect("layout")
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // clipToBounds is load-bearing, not tidiness. A stroke is stored
        // relative to its block, and a split paragraph's continuation reports
        // a VIRTUAL top above this overlay — so ink drawn on the last line of
        // the previous page renders at a negative y. Compose does not clip to
        // component bounds on its own, so that ink was painting over the
        // header at the top of the next page, looking like the line had
        // followed the reader across the turn. Untappable too, being outside
        // the overlay's touch area.
        Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
            pageStrokes.forEach { s ->
                val top = blockTopY(s.blockIndex) ?: return@forEach
                if (s.points.size < 2) return@forEach
                if (!belongsToThisPage(s, top, blockVisibleTop)) return@forEach
                val path = Path().apply {
                    val first = s.points.first()
                    moveTo(first.x, first.y + top)
                    for (i in 1 until s.points.size) {
                        val p = s.points[i]
                        lineTo(p.x, p.y + top)
                    }
                }
                drawPath(
                    path = path,
                    color = Color.Black,
                    style = DrawStroke(
                        width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { touchHelperRef.value?.closeRawDrawing() } catch (_: Throwable) {}
            touchHelperRef.value = null
        }
    }
}

private const val TAG = "OnyxInkOverlay"

/**
 * Which TouchHelper feature mode to open with, and the flag on the four-arg
 * `create`. Both are here rather than inline because getting them right is
 * pure trial on a device — the SDK ships no docs and the constant names
 * mislead.
 *
 * The values, from javap on onyxsdk-pen 1.5.4:
 *   FEATURE_APP_TOUCH_RENDER      = 1
 *   FEATURE_SF_TOUCH_RENDER       = 2
 *   FEATURE_ALL_TOUCH_RENDER      = 3
 *   FEATURE_APP_PEN_TOUCH_RENDER  = 4
 *
 * Mode 4 is what finally let finger events reach Compose (modes tried in v1
 * and v2 swallowed them below the view). But "PEN_TOUCH" turns out to mean
 * pen AND touch: with it, every finger tap was captured as a stroke — the
 * page-turn tap drew a dot, then "hit" the dot it had just made, which is why
 * tap, page-turn and erase all appeared dead at once. The stroke count rising
 * by one per finger tap in the log is the fingerprint of it.
 *
 * The four-arg create takes a flag the three-arg forms don't expose; false is
 * the candidate for "pen input only".
 */
private const val ONYX_FEATURE_MODE = TouchHelper.FEATURE_APP_PEN_TOUCH_RENDER
private const val ONYX_CREATE_FLAG = true

/**
 * How long after a finger touches down a stroke is still treated as that
 * finger's. Measured from DOWN rather than UP because the miss case must not
 * hold the gesture open (that broke tap-to-turn-page), so the release time is
 * not observed. Long enough to cover a swipe, which is a finger contact that
 * lasts well beyond a tap.
 * Onyx reports pen and finger through the same raw callbacks with no tool
 * type, so the only way to tell them apart is that Compose *does* see the
 * finger — and anything arriving while a finger is on the glass is not ink.
 */
private const val FINGER_STROKE_GRACE_MS = 800L

/**
 * Ceiling on how long one pen contact is followed before the gesture detector
 * is freed.
 *
 * Deliberately short. Onyx takes the pen at driver level and Compose never
 * observes the lift, so waiting for it achieves nothing — the only reason to
 * wait was to consume the release, and a release that never arrives cannot be
 * consumed. It also cannot make the parent fire a page-turn, because the
 * parent needs that same release to decide a tap happened.
 *
 * So this is just a short window in which a release WOULD be consumed if one
 * did arrive (a finger the digitiser reports as a stylus, say), after which
 * the detector frees up and finger taps work again. At six seconds the pen
 * blocked every tap for six seconds after each stroke.
 */
private const val PEN_CONTACT_TIMEOUT_MS = 350L

/** How long after a stroke ends before the panel is released. */
private const val UNLOCK_DEBOUNCE_MS = 200L

/**
 * How long after an eraser contact lifts a stroke committed by the SDK is
 * still taken to be that eraser sweep rather than ink. Only the sweep itself
 * can end inside this window; nobody flips a pen over in 150 ms.
 */
private const val ERASE_COMMIT_GRACE_MS = 150L

/**
 * Unlock reasons after which the panel must be fully repainted, because ink
 * the driver had painted is now gone from the model. Everything else — a
 * committed stroke, a chip opening — only ADDS to the panel, and a redraw
 * shows that without the flash a GC costs.
 */
/**
 * Unlock reasons that must not wait out [UNLOCK_DEBOUNCE_MS]: UI the reader is
 * waiting to see or to see gone, rather than ink being committed.
 */
private val IMMEDIATE_REASONS =
    setOf("chip", "chipShow", "chipClose", "overlay", "inkRemoved")

private val REPAINT_REASONS =
    setOf("eraseTrail", "eraseSweep", "erase", "chipClose", "chipShow", "overlay", "inkRemoved")

/**
 * Reasons that need the driver's ink layer WIPED — a different question from
 * whether the panel needs refreshing, and the distinction matters.
 *
 * A page turn is the case that separates them. It needs the wipe, because the
 * driver's copy of the old page's ink would otherwise sit over the new text.
 * It does NOT need a panel refresh: every pixel on the page is being replaced
 * anyway, and the ordinary content redraw covers them. Asking for a GC as
 * well meant a full-panel flash on every single turn — measured, and
 * unbearable to read against.
 *
 * A chip needs BOTH, and only the pen reveals why: pressing a chip button
 * with the stylus makes the driver ink the spot it touched, so refreshing the
 * panel left that mark composited on top and the chip appeared to ghost.
 * The same press with a finger leaves no driver ink and no ghost. Scoped to
 * the chip band, so ink elsewhere on the page keeps its low-latency copy.
 */
private val CLEAR_REASONS =
    setOf(
        "eraseTrail", "eraseSweep", "erase", "pageTurn", "chipShow", "chipClose",
        "overlay", "inkRemoved",
    )

/**
 * How the panel is repainted once ink has been removed from it.
 *
 * GC, and it has to be GC: it is the mode that actually clears ghosting.
 * GU is a partial greyscale update — quiet, but it leaves the erased ink
 * faintly on the glass, which is the ghost this whole path exists to remove.
 * Measured both ways on the device: GU is silent and ghosts, GC is clean.
 *
 * What made GC unbearable before was never the mode, it was the SCOPE — a
 * full-panel GC blinked the whole page black and back to delete one
 * underline. Applied to the rectangle the erase actually touched, the flash
 * is a strip most of a line high and the page around it never moves.
 */
private val REPAINT_MODE = UpdateMode.GC

/**
 * Deliberately one mode for every reason.
 *
 * A gentler UpdateMode.GU was tried for chips, on the theory that the blink
 * was the mode. It was not — it was the SCOPE, exactly as the erase work found
 * in September. Once the repaint was scoped to the pill instead of a
 * quarter-screen band the flash went away on its own, and GU would have risked
 * bringing back the chip-shaped ghost that this repaint exists to prevent.
 */

/** Slop around an erased region, so antialiased edges are repainted too. */
private const val REPAINT_PAD_PX = 12f

/** An eraser sweep is placed by a pen, to within about a millimetre. */
private const val ERASE_SLOP_PX = 24f

/**
 * A fingertip is not a pen. 24px is ~2mm on this panel — tighter than a tap
 * meant for a stroke actually lands — which is why the chip opened only
 * sometimes, and felt like it was ignoring taps at random.
 */
private const val TAP_SLOP_PX = 36f

/**
 * Longer than this and the contact is a press, not a tap — it belongs to text
 * selection, not to the stroke chip.
 */
private const val TAP_MAX_MS = 300L

/**
 * How long a drag may go on before the overlay stops following it.
 *
 * Only a backstop: the SDK owns this view's touch stream and Compose is not
 * guaranteed the release, so without a bound the gesture coroutine would hang
 * and never restart — which is the shape of an old bug where tap-on-stroke
 * worked on a fresh page and died the moment anything was drawn.
 */
private const val DRAG_CONTACT_TIMEOUT_MS = 1500L

/**
 * Share of the overlay's height, measured from the bottom, that a stroke chip
 * can occupy. Used to scope the GC that clears the chip's ghost, so putting a
 * chip away never repaints the page above it.
 */
private const val CHIP_BAND_FRACTION = 0.25f

/**
 * How far above a block's visible edge a stroke may start and still count as
 * this page's. Generous enough for ink drawn a little high on its line, well
 * under the ~56px line height that separates one line from the next.
 */
private const val PAGE_OWNERSHIP_SLOP_PX = 24f

private fun findAnchorBlockOnyx(
    range: IntRange,
    blockTopY: (Int) -> Float?,
    y: Float,
): Int? {
    var best: Int? = null
    var bestTop = Float.NEGATIVE_INFINITY
    for (i in range) {
        val t = blockTopY(i) ?: continue
        if (t <= y && t > bestTop) {
            best = i
            bestTop = t
        }
    }
    if (best == null) {
        for (i in range) if (blockTopY(i) != null) return i
    }
    return best
}

private fun hitTestForErase(
    strokes: List<Stroke>,
    atX: Float, atY: Float,
    blockTopY: (Int) -> Float?,
    slop: Float = ERASE_SLOP_PX,
    blockVisibleTop: (Int) -> Float? = { null },
): Stroke? {
    val slopSq = slop * slop
    return strokes.firstOrNull { s ->
        val top = blockTopY(s.blockIndex) ?: return@firstOrNull false
        // Agrees with the renderer: ink that is not drawn on this page cannot
        // be tapped or erased from it either.
        if (!belongsToThisPage(s, top, blockVisibleTop)) return@firstOrNull false
        val pts = s.points
        if (pts.isEmpty()) return@firstOrNull false
        if (distSq(pts[0].x, pts[0].y + top, atX, atY) <= slopSq) {
            return@firstOrNull true
        }
        // Distance to the stroke's SEGMENTS, not to its sample points. A
        // stroke is a line, but this only ever measured the dots it was
        // sampled at: a quick sweep samples sparsely, so a contact landing
        // between two samples of a fast underline matched nothing and
        // reported no hit at all.
        var prevX = pts[0].x
        var prevY = pts[0].y + top
        for (i in 1 until pts.size) {
            val x = pts[i].x
            val y = pts[i].y + top
            if (segmentDistSq(prevX, prevY, x, y, atX, atY) <= slopSq) {
                return@firstOrNull true
            }
            prevX = x
            prevY = y
        }
        false
    }
}

/**
 * Bounding box of one erase gesture — the sweep itself unioned with the
 * strokes it removed — padded, in overlay-local pixels. Null when there is
 * nothing to bound, which asks for a whole-view repaint instead.
 */
/**
 * Bounding box of all the ink on this page, or null when there is none.
 *
 * Shares [eraseRegion]'s geometry because it is the same question — which
 * pixels does this ink occupy — asked with no eraser trail.
 */
private fun inkRegion(
    strokes: List<Stroke>,
    blockTopY: (Int) -> Float?,
): AndroidRect? = eraseRegion(emptyList(), strokes, blockTopY)

private fun eraseRegion(
    trail: List<Pair<Float, Float>>,
    removed: Collection<Stroke>,
    blockTopY: (Int) -> Float?,
): AndroidRect? {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    var any = false

    fun add(x: Float, y: Float) {
        any = true
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
    }

    trail.forEach { (x, y) -> add(x, y) }
    removed.forEach { s ->
        val top = blockTopY(s.blockIndex) ?: return@forEach
        s.points.forEach { p -> add(p.x, p.y + top) }
    }
    if (!any) return null
    return AndroidRect(
        (minX - REPAINT_PAD_PX).toInt().coerceAtLeast(0),
        (minY - REPAINT_PAD_PX).toInt().coerceAtLeast(0),
        (maxX + REPAINT_PAD_PX).toInt(),
        (maxY + REPAINT_PAD_PX).toInt(),
    )
}

/**
 * Whether a stroke was drawn on a line this page is showing.
 *
 * Only ever false for the continuation of a split paragraph, where ink drawn
 * on the earlier part would otherwise re-render here: the stored offset is
 * relative to the block, and an underline sits BELOW its line, so a stroke on
 * the last line of the previous page lands at roughly y=0 on this one —
 * inside the clip, at the very top, looking like the line followed the reader
 * across the turn. Clipping cannot catch that; only the block's real visible
 * edge can.
 */
private fun belongsToThisPage(
    s: Stroke,
    virtualTop: Float,
    blockVisibleTop: (Int) -> Float?,
): Boolean {
    val visibleTop = blockVisibleTop(s.blockIndex) ?: return true
    if (visibleTop <= virtualTop) return true  // not a continuation
    val strokeTop = (s.points.minOfOrNull { it.y } ?: return true) + virtualTop
    return strokeTop >= visibleTop - PAGE_OWNERSHIP_SLOP_PX
}

private fun distSq(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x1 - x2
    val dy = y1 - y2
    return dx * dx + dy * dy
}

/** Squared distance from (px,py) to the segment (ax,ay)–(bx,by). */
private fun segmentDistSq(
    ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float,
): Float {
    val vx = bx - ax
    val vy = by - ay
    val lenSq = vx * vx + vy * vy
    if (lenSq <= 0f) return distSq(ax, ay, px, py)
    // Where the contact projects onto the segment, clamped to its ends.
    val t = (((px - ax) * vx + (py - ay) * vy) / lenSq).coerceIn(0f, 1f)
    return distSq(ax + t * vx, ay + t * vy, px, py)
}
