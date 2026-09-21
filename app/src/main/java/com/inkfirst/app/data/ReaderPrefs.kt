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

    // Dev-only escape hatch for testing ink capture without a stylus (the
    // emulator has no stylus, and the Boox is the only device with one).
    // When true, finger touches also draw. On the Boox this should stay off
    // so finger remains available for page-turn and text selection.
    fun getFingerDraws(): Boolean = prefs.getBoolean(KEY_FINGER_DRAWS, false)

    fun setFingerDraws(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FINGER_DRAWS, enabled).apply()
    }

    // Reading-column horizontal padding in dp — symmetric left/right. Wider
    // margins double as marginalia space for stylus notes ("notebook mode").
    // Kept out of the FONT_SCALES/LINE_SPACINGS pattern deliberately since
    // margin changes reflow the whole chapter (paginator reads content width).
    fun getHorizontalPadding(): Float = prefs.getFloat(KEY_HPAD, DEFAULT_HORIZONTAL_PADDING)

    fun setHorizontalPadding(padding: Float) {
        prefs.edit().putFloat(KEY_HPAD, padding).apply()
    }

    /**
     * How many pages the reader has turned, ever. Only used to retire the
     * "tap ← / center / →" hint once the gesture is learned — it is
     * instructional chrome, and chrome that never stops instructing is just
     * noise on a book page. Counts across courses on purpose: the gesture is
     * the same everywhere, so learning it once is enough.
     */
    fun getPageTurns(): Int = prefs.getInt(KEY_PAGE_TURNS, 0)

    fun notePageTurn() {
        val seen = getPageTurns()
        // Stop writing once the hint is retired — no reason to touch disk on
        // every page turn for the rest of the book's life.
        if (seen <= HINT_RETIRES_AFTER) {
            prefs.edit().putInt(KEY_PAGE_TURNS, seen + 1).apply()
        }
    }

    companion object {
        /** Page turns after which the tap hint stops being drawn. */
        const val HINT_RETIRES_AFTER = 12

        const val DEFAULT_FONT_SCALE = 1.0f
        const val DEFAULT_LINE_SPACING = 1.0f
        const val DEFAULT_HORIZONTAL_PADDING = 40f
        val DEFAULT_ALIGN = TextAlignPref.LEFT

        val FONT_SCALES = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f)
        val LINE_SPACINGS = listOf(0.85f, 1.0f, 1.2f)  // tight / normal / relaxed
        val LINE_SPACING_LABELS = listOf("Tight", "Normal", "Relaxed")
        // Symmetric side padding, in dp. The whole scale was raised on
        // 2026-09-21: the old one topped out at 40dp, which on the Go 7's
        // 300dpi panel is 6.4mm — about a third of the 12-18mm a printed
        // page this size carries, and far too narrow to write in. Since the
        // margins are where the pen is meant to go, the top of the scale has
        // to be a margin you can actually put a word in.
        //
        // At 300dpi (density 1.875) on a 107mm-wide page:
        //   Narrow   16dp =  2.7mm  — text column 102mm
        //   Normal   40dp =  6.4mm  — text column  94mm   (the old "Wide")
        //   Wide     72dp = 11.4mm  — text column  84mm
        //   Notebook 112dp = 17.8mm — text column  71mm, a writable column
        //
        // 40dp is kept as a step, and is the new default, so a reader already
        // on the old "Wide" keeps exactly the layout they had and still finds
        // their setting selected in the picker.
        val HORIZONTAL_PADDINGS = listOf(16f, 40f, 72f, 112f)
        val HORIZONTAL_PADDING_LABELS = listOf("Narrow", "Normal", "Wide", "Notebook")

        private const val PREFS_NAME = "inkfirst_reader_prefs"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_LINE_SPACING = "line_spacing"
        private const val KEY_TEXT_ALIGN = "text_align"
        private const val KEY_FINGER_DRAWS = "finger_draws"
        private const val KEY_HPAD = "horizontal_padding"
        private const val KEY_PAGE_TURNS = "page_turns"
    }
}

enum class TextAlignPref(val key: String, val label: String) {
    LEFT("left", "Left"),
    JUSTIFY("justify", "Justify");

    companion object {
        fun fromKey(key: String): TextAlignPref = entries.firstOrNull { it.key == key } ?: LEFT
    }
}
