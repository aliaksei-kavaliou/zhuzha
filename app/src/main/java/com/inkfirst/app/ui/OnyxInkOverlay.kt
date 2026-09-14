package com.inkfirst.app.ui

import android.graphics.PixelFormat
import android.graphics.Rect as AndroidRect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import com.inkfirst.core.InkPoint
import com.inkfirst.core.Stroke
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
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
 *   - Finger DOWN that hits an existing stroke → fire onStrokeTapped and
 *     consume the stream (opens the tap-chip; parent page-turn skipped).
 *   - Finger DOWN that misses → do NOT consume, letting the parent's
 *     page-turn gesture handle it normally.
 *
 * Erase (barrel-button) currently DOES NOT fire — Onyx's onRawErasing
 * callbacks weren't triggering on the Boox Go 7 pen we tested. Erase
 * remains available via the tap-chip's "Delete" button. Fixing the
 * barrel-button path is queued as a follow-up on task 10.
 *
 * Phantom strokes (EPD-drawn stroke staying visible while the Compose
 * Canvas below also draws the newly-persisted stroke) are a known issue.
 * The proper fix is the bitmap+blit pattern from [onyx-pen-sdk] memory;
 * not yet implemented.
 *
 * On non-Boox devices [TouchHelper] init throws — caught and logged.
 * The overlay is then non-functional but the app doesn't crash.
 */
@Composable
fun OnyxInkOverlay(
    pageStrokes: List<Stroke>,
    blockTopY: (globalIdx: Int) -> Float?,
    pageBlockRange: IntRange,
    onStrokeAdded: (Stroke) -> Unit,
    onStrokeErased: (Stroke?) -> Unit,
    onStrokeTapped: (Stroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    val addedRef = rememberUpdatedState(onStrokeAdded)
    val tappedRef = rememberUpdatedState(onStrokeTapped)
    val strokesRef = rememberUpdatedState(pageStrokes)
    val blockTopYRef = rememberUpdatedState(blockTopY)
    val rangeRef = rememberUpdatedState(pageBlockRange)

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val touchHelperRef = remember { mutableStateOf<TouchHelper?>(null) }
    val startTouchRef = remember { mutableStateOf<TouchPoint?>(null) }
    // Screen position of the drawing surface, refreshed at each stroke start.
    val originRef = remember { mutableStateOf(0f to 0f) }
    /** Guards against the list and move callbacks both emitting one stroke. */
    val emittedRef = remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
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
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull() ?: break
                                if (!ch.pressed) break
                            }
                        }
                        PointerType.Touch, PointerType.Mouse -> {
                            val at = down.position
                            val hit = hitTestForErase(
                                strokesRef.value, at.x, at.y, blockTopYRef.value,
                            )
                            if (hit != null) {
                                down.consume()
                                tappedRef.value(hit)
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull() ?: break
                                    ch.consume()
                                    if (!ch.pressed) break
                                }
                            }
                            // else: don't consume → parent handles page-turn
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
                    fun originOnScreen(): Pair<Float, Float> {
                        val loc = IntArray(2)
                        surface.getLocationOnScreen(loc)
                        return loc[0].toFloat() to loc[1].toFloat()
                    }

                    // Samples collected during one pen-down, in screen space.
                    val sampleBuf = mutableListOf<TouchPoint>()

                    fun emitStroke(pts: List<TouchPoint>, source: String) {
                        if (pts.isEmpty()) return
                        val (ox, oy) = originRef.value
                        val start = pts.first()
                        val startLocalY = start.y - oy
                        val getTop = blockTopYRef.value
                        val anchorIdx = findAnchorBlockOnyx(rangeRef.value, getTop, startLocalY)
                        val xs = pts.map { it.x }
                        val ys = pts.map { it.y }
                        Log.d(
                            TAG,
                            "emit($source) n=${pts.size} localY=$startLocalY anchor=$anchorIdx " +
                                "extent=${(xs.max() - xs.min()).toInt()}x${(ys.max() - ys.min()).toInt()}px",
                        )
                        if (anchorIdx == null) {
                            Log.w(TAG, "emit($source): no anchor for localY=$startLocalY — dropped")
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
                            val (ox, oy) = originOnScreen()
                            originRef.value = ox to oy
                            Log.d(
                                TAG,
                                "onBeginRawDrawing screen=(${point.x},${point.y}) " +
                                    "origin=($ox,$oy) local=(${point.x - ox},${point.y - oy})",
                            )
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

                        override fun onBeginRawErasing(shortcut: Boolean, point: TouchPoint) {
                            Log.d(TAG, "onBeginRawErasing shortcut=$shortcut")
                        }
                        override fun onEndRawErasing(outOfLimit: Boolean, point: TouchPoint) {
                            Log.d(TAG, "onEndRawErasing outOfLimit=$outOfLimit")
                        }
                        override fun onRawErasingTouchPointMoveReceived(point: TouchPoint) {}
                        override fun onRawErasingTouchPointListReceived(list: TouchPointList) {
                            Log.d(TAG, "onRawErasingTouchPointListReceived pts=${list.points?.size}")
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
                        TouchHelper.FEATURE_APP_PEN_TOUCH_RENDER,
                        callback,
                    )
                    touchHelperRef.value = helper
                    Log.d(TAG, "TouchHelper.create returned=$helper mode=FEATURE_APP_PEN_TOUCH_RENDER")
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
                                helper.setRawDrawingEnabled(true)
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
                                helper.setRawDrawingEnabled(true)
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

        Canvas(modifier = Modifier.fillMaxSize()) {
            pageStrokes.forEach { s ->
                val top = blockTopY(s.blockIndex) ?: return@forEach
                if (s.points.size < 2) return@forEach
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
    // onStrokeErased is not wired: barrel-button events weren't reaching the
    // Onyx erase callbacks in the first attempt. Erase stays available via
    // the chip's Delete button; we'll revisit once we have the raw MotionEvent
    // path snooped for barrel-button state.
    @Suppress("UNUSED_EXPRESSION") onStrokeErased
}

private const val TAG = "OnyxInkOverlay"

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
): Stroke? {
    val slopSq = 24f * 24f
    return strokes.firstOrNull { s ->
        val top = blockTopY(s.blockIndex) ?: return@firstOrNull false
        s.points.any { p ->
            val dx = p.x - atX
            val dy = (p.y + top) - atY
            dx * dx + dy * dy <= slopSq
        }
    }
}
