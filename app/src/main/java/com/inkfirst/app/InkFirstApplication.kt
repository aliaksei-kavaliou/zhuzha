package com.inkfirst.app

import android.app.Application
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Custom Application solely so we can install the hidden-API bypass Onyx's
 * pen SDK needs on Android 11+ (SDK_INT >= R). The SDK reaches into
 * internal graphics + display APIs that Google restricted for third-party
 * apps; without the exemption `com.onyx.android.sdk.pen.TouchHelper`
 * throws at init.
 *
 * Passing an empty string exempts ALL hidden APIs — Onyx's SDK touches
 * several. Safe here because the app is single-purpose and sideloaded on
 * a personal device; a Play-Store distribution would need to be more
 * surgical.
 */
class InkFirstApplication : Application() {

    /**
     * For work that must outlive the screen that started it.
     *
     * Compiling a chapter is text generation, a safety check, figures, badges
     * and a memory update — minutes of wall clock and REAL MONEY, several API
     * calls deep. Tied to `rememberCoroutineScope()` it died whenever the
     * reader was navigated away from or the Activity was destroyed in the
     * background, which on Onyx firmware is eager rather than rare: the reader
     * paid for half a chapter and got nothing.
     *
     * SupervisorJob so one failed compile does not cancel another. This scope
     * is never cancelled — it belongs to the process, and the process ending
     * is the only thing that should stop a compile.
     *
     * It does NOT survive process death. The work completes and the chapter is
     * saved, but a screen returning afterwards will not reattach to a compile
     * in flight — it sees the finished chapter instead. That is a far better
     * failure than losing the work, and the fix for the rest is WorkManager,
     * which is not worth its weight here.
     */
    val longRunning = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }
}
