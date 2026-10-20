package com.inkfirst.app.ui

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import com.inkfirst.core.InkPoint
import com.inkfirst.core.Stroke
import java.time.LocalDate

/**
 * Freehand-ink capture + render layer that sits ABOVE the paginated text
 * inside the reader's content Box and BELOW the FormatMenu / chip overlays.
 *
 * Contract with the caller:
 *   - [pageStrokes] is the subset of the chapter's persisted strokes whose
 *     block anchors fall on the currently-visible page.
 *   - [blockTopY] returns each on-page block's top edge in this overlay's
 *     local coordinate space (which is the content Box's, since we
 *     fillMaxSize inside it). Return null for blocks not on this page.
 *   - [pageBlockRange] is the global block-index range covered by this page —
 *     used to decide which block a new stroke anchors to (the containing
 *     block at the stroke's start Y).
 *   - [allowFingerDraw] is a dev flag; the emulator has no stylus, so we
 *     accept finger input under this toggle. On the Boox, keep it false so
 *     finger stays available for page-turn / text selection.
 *
 * Input handling uses [pointerInteropFilter] rather than Compose's
 * awaitPointerEvent flavor because we need raw MotionEvent access — the
 * stylus barrel button (BUTTON_STYLUS_PRIMARY) is not surfaced by
 * PointerEvent.buttons, and PointerEvent.motionEvent is @internal.
 *
 * Rendering: strokes are drawn in solid black on a Canvas. On e-ink only
 * pure black + white render cleanly, so we don't lean on anti-alias hints.
 *
 * Persistence: this composable is stateless w.r.t. saved strokes — every
 * stroke-end and stroke-erase fires the corresponding callback and the
 * caller updates its store and re-passes the new list.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InkOverlay(
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
    /** Where this block's slice on THIS page stops. Mirrors the Onyx path. */
    blockVisibleBottom: (globalIdx: Int) -> Float? = { null },
    pageBlockRange: IntRange,
    /** See the note on OnyxInkOverlay: strokes are stored relative to the TEXT. */
    textInsetPx: Float = 0f,
    allowFingerDraw: Boolean,
    /**
     * False while anything is open over the chapter — the reader menu, a
     * dialog, the chat panel.
     *
     * [OnyxInkOverlay] has taken this since it was written, because the pen
     * driver sits below Android and would otherwise draw straight through a
     * modal. This overlay never did, and with [allowFingerDraw] on it claims
     * the pointer stream at ACTION_DOWN like any other contact — so a tap on
     * the page with the menu up was swallowed here and never reached the
     * detector that dismisses it. The menu could not be closed by tapping the
     * page, which is its only dismiss target.
     *
     * Reported 2026-10-20. It needs no stylus, so it is a phone defect: a
     * reader who turns finger-draw on to use the working room is exactly the
     * reader who meets it.
     */
    drawingEnabled: Boolean = true,
    onStrokeAdded: (Stroke) -> Unit,
    /**
     * Fires on every barrel-button-tap end, whether or not it landed on a
     * stroke. Argument is the hit stroke, or null if the tap missed. The
     * caller decides: usually "remove hit stroke" when idle, or "dismiss
     * the open chip" when a chip is showing.
     */
    onStrokeErased: (Stroke?) -> Unit,
    onStrokeTapped: (Stroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Live in-progress stroke in overlay-local px. mutableStateListOf so
    // reads inside Canvas subscribe and redraw on each new point.
    val current = remember { mutableStateListOf<Offset>() }
    var isErasing by remember { mutableStateOf(false) }
    var startX by remember { mutableStateOf(0f) }
    var startY by remember { mutableStateOf(0f) }
    var maxDelta by remember { mutableStateOf(0f) }
    // True while we own the current pointer stream. When we don't own it,
    // subsequent MOVE/UP events must be ignored — otherwise a finger event
    // that we didn't claim at DOWN can still reach us via the same modifier
    // and corrupt state.
    var ownsStream by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInteropFilter { event ->
                val toolType = event.getToolType(0)
                val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS ||
                    toolType == MotionEvent.TOOL_TYPE_ERASER
                val shouldHandle = isStylus || allowFingerDraw

                // Nothing is ours while something is open over the page —
                // not a new stroke, and not a tap on an existing one either.
                // Returning false leaves the whole gesture to the detector
                // underneath, which is what closes the menu.
                if (!drawingEnabled) {
                    ownsStream = false
                    return@pointerInteropFilter false
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val at = Offset(event.x, event.y)
                        // Claim the pointer stream if either (a) we're in a
                        // mode that captures strokes (stylus or finger-draws
                        // dev flag), OR (b) the down lands on an existing
                        // stroke — so finger tap on a drawing opens the chip
                        // instead of turning the page via the parent's tap
                        // gesture. Trade-off: swipes that start ON a drawing
                        // won't page-turn — drawings are small, so this is
                        // rare in practice.
                        val hitOnDown = hitTestStroke(pageStrokes, at.copy(x = at.x - textInsetPx), blockTopY)
                        if (!shouldHandle && hitOnDown == null) {
                            ownsStream = false
                            return@pointerInteropFilter false
                        }
                        ownsStream = true
                        val eraserTip = toolType == MotionEvent.TOOL_TYPE_ERASER
                        val buttonHeld = isStylus &&
                            (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                        isErasing = eraserTip || buttonHeld
                        current.clear()
                        if (shouldHandle) current.add(at)
                        startX = event.x
                        startY = event.y
                        maxDelta = 0f
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!ownsStream) return@pointerInteropFilter false
                        if (!isErasing) {
                            // Android batches touch events at ~60Hz; the current
                            // event carries any samples it couldn't dispatch as
                            // individual MOVEs in history. Reading them makes
                            // fast pen strokes smooth instead of polylined.
                            for (h in 0 until event.historySize) {
                                val hx = event.getHistoricalX(0, h)
                                val hy = event.getHistoricalY(0, h)
                                if (shouldHandle) current.add(Offset(hx, hy))
                                val d = maxOf(kotlin.math.abs(hx - startX), kotlin.math.abs(hy - startY))
                                if (d > maxDelta) maxDelta = d
                            }
                            if (shouldHandle) current.add(Offset(event.x, event.y))
                            val dNow = maxOf(kotlin.math.abs(event.x - startX), kotlin.math.abs(event.y - startY))
                            if (dNow > maxDelta) maxDelta = dNow
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!ownsStream) return@pointerInteropFilter false
                        val at = Offset(event.x, event.y)
                        val movedLikeATap = maxDelta < TAP_SLOP_PX
                        if (isErasing) {
                            // Fire even on miss so the caller can dismiss
                            // an open chip via barrel-button-tap-anywhere.
                            val hit = hitTestStroke(pageStrokes, at.copy(x = at.x - textInsetPx), blockTopY)
                            onStrokeErased(hit)
                        } else if (movedLikeATap) {
                            // Tap on (or very near) a stroke → chip. Works
                            // for finger too since we claimed the down above.
                            val hit = hitTestStroke(pageStrokes, at.copy(x = at.x - textInsetPx), blockTopY)
                            if (hit != null) onStrokeTapped(hit)
                        } else if (shouldHandle && current.size >= 2) {
                            // New stroke — draw mode only; a finger drag over
                            // a stroke without allowFingerDraw ends here silently.
                            val anchorIdx = findAnchorBlock(pageBlockRange, blockTopY, startY)
                            if (anchorIdx != null) {
                                val anchorY = blockTopY(anchorIdx) ?: 0f
                                val stroke = Stroke(
                                    blockIndex = anchorIdx,
                                    points = current.map { p ->
                                        InkPoint(x = p.x - textInsetPx, y = p.y - anchorY)
                                    },
                                    brush = "pen",
                                    created = LocalDate.now().toString(),
                                )
                                onStrokeAdded(stroke)
                            }
                        }
                        current.clear()
                        isErasing = false
                        maxDelta = 0f
                        ownsStream = false
                        true
                    }
                    else -> ownsStream
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            pageStrokes.forEach { s ->
                val top = blockTopY(s.blockIndex) ?: return@forEach
                if (s.points.size < 2) return@forEach
                val path = Path().apply {
                    val first = s.points.first()
                    moveTo(first.x + textInsetPx, first.y + top)
                    for (i in 1 until s.points.size) {
                        val p = s.points[i]
                        lineTo(p.x + textInsetPx, p.y + top)
                    }
                }
                drawPath(
                    path = path,
                    color = Color.Black,
                    style = DrawStroke(width = STROKE_WIDTH_PX, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            if (current.size >= 2) {
                val path = Path().apply {
                    moveTo(current[0].x, current[0].y)
                    for (i in 1 until current.size) lineTo(current[i].x, current[i].y)
                }
                drawPath(
                    path = path,
                    color = Color.Black,
                    style = DrawStroke(width = STROKE_WIDTH_PX, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

// Stroke width in device pixels. Matches Boox notebook default weight
// closely enough; 2px was too thin at 300 DPI. Tunable — could be user
// pref later if we add multiple brushes.
private const val STROKE_WIDTH_PX = 3.5f

// Max touch-travel (px) below which a stylus-down/up is a tap, not a
// stroke. Real hand-held taps wobble more than a lab reading suggests —
// 8 was too tight and taps were misfiring as tiny strokes. 20 lets the
// hand settle a bit. If a stroke actually was intended, the user will
// draw far more than 20 px.
private const val TAP_SLOP_PX = 20f

/**
 * Picks the block whose top edge is at or above [y] and closest to it. This
 * "starts in block N" rule keeps a stroke that trails into the next block
 * anchored to where it began — matches how you'd write a marginal note on
 * paper (the stroke belongs to the passage you started under, even if your
 * arrow flows down into the next).
 */
private fun findAnchorBlock(
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
    // Fallback: point sits above every block's top (rare — e.g. above the
    // first block's padding). Snap to the first known block on the page.
    if (best == null) {
        for (i in range) {
            if (blockTopY(i) != null) return i
        }
    }
    return best
}

/**
 * Returns the first stroke on the page whose polyline passes within
 * ~24 pixels of [at] (page-local coords). Cheap point-distance test —
 * generous slop so tapping "near" a drawing (user intent = "that
 * drawing") still hits. Erase uses the same test; if two strokes are
 * within slop, first-in-list wins — an acceptable minor ambiguity given
 * how rarely two drawings overlap on the same page.
 */
private fun hitTestStroke(
    strokes: List<Stroke>,
    at: Offset,
    blockTopY: (Int) -> Float?,
): Stroke? {
    val slop = 24f
    val slopSq = slop * slop
    return strokes.firstOrNull { s ->
        val top = blockTopY(s.blockIndex) ?: return@firstOrNull false
        s.points.any { p ->
            val dx = p.x - at.x
            val dy = (p.y + top) - at.y
            dx * dx + dy * dy <= slopSq
        }
    }
}
