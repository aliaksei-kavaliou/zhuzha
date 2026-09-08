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

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    when (down.type) {
                        PointerType.Stylus, PointerType.Eraser -> {
                            // Consume the stream so parent page-turn stays
                            // silent. Onyx's driver-level input path is
                            // independent of Compose pointer input.
                            down.consume()
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull() ?: break
                                ch.consume()
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

                    val callback = object : RawInputCallback() {
                        override fun onBeginRawDrawing(shortcut: Boolean, point: TouchPoint) {
                            startTouchRef.value = point
                        }
                        override fun onEndRawDrawing(outOfLimit: Boolean, point: TouchPoint) {}
                        override fun onRawDrawingTouchPointMoveReceived(point: TouchPoint) {}
                        override fun onRawDrawingTouchPointListReceived(list: TouchPointList) {
                            val pts = list.points ?: return
                            if (pts.size < 2) return
                            val start = startTouchRef.value ?: pts.first()
                            val getTop = blockTopYRef.value
                            val range = rangeRef.value
                            val anchorIdx = findAnchorBlockOnyx(range, getTop, start.y) ?: return
                            val anchorY = getTop(anchorIdx) ?: 0f
                            val stroke = Stroke(
                                blockIndex = anchorIdx,
                                points = pts.map {
                                    InkPoint(x = it.x, y = it.y - anchorY, pressure = it.pressure)
                                },
                                brush = "pen",
                                created = LocalDate.now().toString(),
                            )
                            mainHandler.post { addedRef.value(stroke) }
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
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) {
                            try {
                                val bounds = AndroidRect(0, 0, width, height)
                                helper.setStrokeWidth(3f)
                                    .setLimitRect(bounds, emptyList())
                                    .openRawDrawing()
                                helper.setStrokeStyle(TouchHelper.STROKE_STYLE_PENCIL)
                                helper.setRawDrawingEnabled(true)
                                Log.d(TAG, "TouchHelper opened ${width}x$height")
                            } catch (t: Throwable) {
                                Log.w(TAG, "TouchHelper init failed", t)
                            }
                        }
                        override fun surfaceChanged(
                            h: SurfaceHolder, format: Int, w: Int, ht: Int,
                        ) {
                            try {
                                val bounds = AndroidRect(0, 0, w, ht)
                                helper.setLimitRect(bounds, emptyList())
                                helper.setRawDrawingEnabled(true)
                            } catch (_: Throwable) {}
                        }
                        override fun surfaceDestroyed(h: SurfaceHolder) {
                            try { helper.setRawDrawingEnabled(false) } catch (_: Throwable) {}
                        }
                    })
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
