package com.inkfirst.app.ui

import android.graphics.PixelFormat
import android.graphics.Rect as AndroidRect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
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
 * framebuffer — bypassing Compose recomposition entirely and eliminating
 * the "input → compose → canvas → EPD" latency chain that made
 * [InkOverlay] feel slow next to native Boox notebook apps.
 *
 * Layer stack (bottom → top):
 *   1. Compose [Canvas] renders persisted strokes for the current page
 *      (updated at recomposition rate — fine, they aren't being drawn live).
 *   2. Onyx [SurfaceView] with [setZOrderOnTop] = true. TouchHelper draws
 *      live stylus strokes here at native latency.
 *   3. Transparent [Box] on top with [pointerInteropFilter] to catch
 *      FINGER events (tap-on-stroke → chip). Stylus events pass through to
 *      the SurfaceView underneath, where TouchHelper claims them.
 *
 * On non-Boox devices the SDK init in [SurfaceHolder.Callback.surfaceCreated]
 * throws — caught and logged; overlay silently no-ops (drawing won't work
 * but the reader stays usable). For emulator testing, fall back to
 * [InkOverlay] behind a build flag if that becomes a problem.
 *
 * Persistence & anchoring semantics match [InkOverlay]: the caller passes
 * [pageStrokes] (persisted strokes for the current page) and [blockTopY]
 * (block-top → overlay-local Y), we anchor a new stroke to the block
 * containing its start Y, and fire [onStrokeAdded] on the main thread.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OnyxInkOverlay(
    pageStrokes: List<Stroke>,
    blockTopY: (globalIdx: Int) -> Float?,
    pageBlockRange: IntRange,
    onStrokeAdded: (Stroke) -> Unit,
    onStrokeErased: (Stroke) -> Unit,
    onStrokeTapped: (Stroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    val addedRef = rememberUpdatedState(onStrokeAdded)
    val erasedRef = rememberUpdatedState(onStrokeErased)
    val tappedRef = rememberUpdatedState(onStrokeTapped)
    val strokesRef = rememberUpdatedState(pageStrokes)
    val blockTopYRef = rememberUpdatedState(blockTopY)
    val rangeRef = rememberUpdatedState(pageBlockRange)

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val touchHelperRef = remember { mutableStateOf<TouchHelper?>(null) }
    val startTouchRef = remember { mutableStateOf<TouchPoint?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        // Layer 1: persisted strokes.
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

        // Layer 2: Onyx SurfaceView for stylus. TouchHelper attaches at
        // surfaceCreated (view has real dimensions by then).
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    setZOrderOnTop(true)
                    holder.setFormat(PixelFormat.TRANSPARENT)

                    val callback = object : RawInputCallback() {
                        override fun onBeginRawDrawing(shortcut: Boolean, point: TouchPoint) {
                            startTouchRef.value = point
                        }
                        override fun onEndRawDrawing(outOfLimit: Boolean, point: TouchPoint) {
                            // final points arrive in TouchPointListReceived
                        }
                        override fun onRawDrawingTouchPointMoveReceived(point: TouchPoint) {
                            // High-frequency per-point ping; Onyx has already
                            // rendered directly to EPD. No-op.
                        }
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
                                    InkPoint(
                                        x = it.x,
                                        y = it.y - anchorY,
                                        pressure = it.pressure,
                                    )
                                },
                                brush = "pen",
                                created = LocalDate.now().toString(),
                            )
                            // Callback fires off the render thread — marshal
                            // to main so the caller can mutate Compose state.
                            mainHandler.post { addedRef.value(stroke) }
                        }
                        override fun onBeginRawErasing(shortcut: Boolean, point: TouchPoint) {}
                        override fun onEndRawErasing(outOfLimit: Boolean, point: TouchPoint) {}
                        override fun onRawErasingTouchPointMoveReceived(point: TouchPoint) {}
                        override fun onRawErasingTouchPointListReceived(list: TouchPointList) {
                            val at = list.points?.lastOrNull() ?: return
                            mainHandler.post {
                                val hit = hitTestForErase(
                                    strokesRef.value, at.x, at.y, blockTopYRef.value,
                                )
                                if (hit != null) erasedRef.value(hit)
                            }
                        }
                    }

                    val helper = TouchHelper.create(this, callback)
                    touchHelperRef.value = helper
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) {
                            try {
                                val bounds = AndroidRect(0, 0, width, height)
                                helper.setStrokeWidth(3f)
                                    .setLimitRect(bounds, emptyList())
                                    .openRawDrawing()
                                helper.setStrokeStyle(TouchHelper.STROKE_STYLE_PENCIL)
                                helper.setRawDrawingEnabled(true)
                            } catch (t: Throwable) {
                                Log.w(TAG, "TouchHelper init failed (non-Boox device?)", t)
                            }
                        }
                        override fun surfaceChanged(
                            h: SurfaceHolder, format: Int, w: Int, ht: Int,
                        ) {
                            try {
                                val bounds = AndroidRect(0, 0, w, ht)
                                helper.setLimitRect(bounds, emptyList())
                                helper.setRawDrawingEnabled(true)
                            } catch (_: Throwable) { /* swallow — logged at create */ }
                        }
                        override fun surfaceDestroyed(h: SurfaceHolder) {
                            try { helper.setRawDrawingEnabled(false) } catch (_: Throwable) {}
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Layer 3: finger tap-on-stroke handler. Stylus events return
        // false → fall through to SurfaceView underneath, where Onyx claims
        // them. Finger events with a hit → consume + fire onStrokeTapped.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    val tool = event.getToolType(0)
                    if (tool == MotionEvent.TOOL_TYPE_STYLUS ||
                        tool == MotionEvent.TOOL_TYPE_ERASER
                    ) {
                        return@pointerInteropFilter false
                    }
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            val hit = hitTestForErase(
                                strokesRef.value, event.x, event.y, blockTopYRef.value,
                            )
                            if (hit != null) {
                                tappedRef.value(hit)
                                true
                            } else false
                        }
                        else -> false
                    }
                },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            try { touchHelperRef.value?.closeRawDrawing() } catch (_: Throwable) {}
            touchHelperRef.value = null
        }
    }
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

