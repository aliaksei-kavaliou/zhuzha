package com.inkfirst.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Non-secret user preferences for the reader.
 */
class ReaderPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFontScale(): Float = prefs.getFloat(KEY_FONT_SCALE, DEFAULT_FONT_SCALE)

    fun setFontScale(scale: Float) {
        prefs.edit().putFloat(KEY_FONT_SCALE, scale).apply()
    }

    fun getLineSpacing(): Float = prefs.getFloat(KEY_LINE_SPACING, DEFAULT_LINE_SPACING)

    fun setLineSpacing(scale: Float) {
        prefs.edit().putFloat(KEY_LINE_SPACING, scale).apply()
    }

    fun getTextAlign(): TextAlignPref =
        TextAlignPref.fromKey(prefs.getString(KEY_TEXT_ALIGN, null) ?: DEFAULT_ALIGN.key)

    fun setTextAlign(pref: TextAlignPref) {
        prefs.edit().putString(KEY_TEXT_ALIGN, pref.key).apply()
    }

    companion object {
        const val DEFAULT_FONT_SCALE = 1.0f
        const val DEFAULT_LINE_SPACING = 1.0f
        val DEFAULT_ALIGN = TextAlignPref.LEFT

        val FONT_SCALES = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f)
        val LINE_SPACINGS = listOf(0.85f, 1.0f, 1.2f)  // tight / normal / relaxed
        val LINE_SPACING_LABELS = listOf("Tight", "Normal", "Relaxed")

        private const val PREFS_NAME = "inkfirst_reader_prefs"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_LINE_SPACING = "line_spacing"
        private const val KEY_TEXT_ALIGN = "text_align"
    }
}

enum class TextAlignPref(val key: String, val label: String) {
    LEFT("left", "Left"),
    JUSTIFY("justify", "Justify");

    companion object {
        fun fromKey(key: String): TextAlignPref = entries.firstOrNull { it.key == key } ?: LEFT
    }
}
