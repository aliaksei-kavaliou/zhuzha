package com.inkfirst.app

import android.app.Application
import android.os.Build
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
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }
}
