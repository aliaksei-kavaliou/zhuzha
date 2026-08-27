package com.inkfirst.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Non-secret user preferences for the reader — separate from ApiKeyStore
 * because these don't need Keystore-backed encryption and the semantic
 * boundary is clearer (credentials vs. reading preferences).
 */
class ReaderPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFontScale(): Float = prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE)

    fun setFontScale(scale: Float) {
        prefs.edit().putFloat(KEY_FONT_SCALE, scale).apply()
    }

    companion object {
        const val DEFAULT_FONT_SCALE = 1.0f
        val FONT_SCALES = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f)

        private const val PREFS_NAME = "inkfirst_reader_prefs"
        private const val KEY_FONT_SCALE = "font_scale"
    }
}
