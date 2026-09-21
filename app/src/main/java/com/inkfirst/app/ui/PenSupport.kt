package com.inkfirst.app.ui

import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether this device can take the Onyx raw-ink path, or has to read on the
 * ordinary Android one.
 *
 * Everything that makes this app what it is — generation, pagination,
 * typography, notes, search, the selection-anchored chat — is plain Compose
 * and runs anywhere. Exactly two capabilities are Onyx-bound, and both live
 * in `OnyxInkOverlay`: the low-latency raw pen (`TouchHelper`), and EPD
 * refresh control (`EpdController`). Off an Onyx device the fallback
 * `InkOverlay` gives working stylus input over ordinary MotionEvents — it
 * lags visibly, which is the whole reason the raw path exists, but every
 * gesture around a stroke works and has done for longer.
 *
 * This used to be `private const val USE_ONYX_PEN = true` in `ReaderScreen`,
 * a constant to flip and reinstall, because the only device that mattered
 * was the Boox on the desk. That is fine until someone else installs it, at
 * which point an unconditional Onyx path is the difference between "runs on
 * your phone" and "the reading view is broken".
 *
 * The gate is deliberately two-layered, because each half fails differently:
 *
 *  - [isOnyxDevice] is a manufacturer check, which is cheap and right almost
 *    always, but goes stale the moment Onyx ships a brand string nobody here
 *    has seen.
 *  - [rawPenUnavailable] is the latch the overlay pulls when the SDK
 *    actually throws. On its own it would be too permissive: a single
 *    hiccup on a real Boox would drop that reader onto the slow path for
 *    good.
 *
 * Together they fail in the right direction — the check keeps non-Onyx
 * devices off the path entirely, and the latch catches an Onyx device whose
 * SDK does not behave.
 */
object PenSupport {

    /**
     * An Onyx-built device. Boox firmware reports the manufacturer as
     * "ONYX"; brand and model are checked too, since Onyx has shipped
     * devices under several names and the cost of a false negative is only
     * the slower ink path.
     */
    val isOnyxDevice: Boolean by lazy {
        val fields = listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL)
            .map { it.orEmpty().lowercase() }
        val onyx = fields.any { it.contains("onyx") || it.contains("boox") }
        Log.i(
            TAG,
            "device manufacturer=${Build.MANUFACTURER} brand=${Build.BRAND} " +
                "model=${Build.MODEL} -> onyx=$onyx",
        )
        onyx
    }

    /**
     * Set once the SDK has failed, and never cleared for the life of the
     * process. Compose state rather than a plain flag so the reader
     * recomposes onto the fallback overlay the moment it flips, instead of
     * showing a dead ink layer until the next page turn.
     */
    private var rawPenBroken by mutableStateOf(false)

    /** Whether the reader should mount the Onyx overlay. */
    val useRawPen: Boolean get() = isOnyxDevice && !rawPenBroken

    /**
     * Called by the overlay when the Onyx SDK throws at init. One way only:
     * a device that could not start raw drawing once is not going to start
     * it on the next recomposition, and retrying would flicker between two
     * ink layers.
     */
    fun rawPenUnavailable(cause: Throwable) {
        if (rawPenBroken) return
        Log.w(TAG, "Onyx raw pen unavailable — falling back to the MotionEvent path", cause)
        rawPenBroken = true
    }

    private const val TAG = "PenSupport"
}
