package com.inkfirst.app.ui

import com.inkfirst.core.BOOK_CHROME_STRINGS
import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * No screen that IS the book may print a hardcoded English string.
 *
 * This defect has now shipped three times, always the same way and always
 * silently: a label is added to a book screen, nobody adds it to
 * [BOOK_CHROME_STRINGS], and it stays English in every translated book for
 * ever. It cost "TRY THIS" in every Russian exercise (2026-09-25), nine more
 * strings the same day, and nine again on 2026-10-02 — "Delete" on the pen
 * chip, the whole compile prompt, and every button on the note and Ask
 * overlays.
 *
 * [BOOK_CHROME_STRINGS]'s own doc says deriving the LIST from the source is
 * not worth a build-time scan, and that stands. This is the cheaper half:
 * not deriving the list, just refusing to let the two drift apart.
 *
 * Scope is the book's own screens. The Library, Settings, New course and
 * Backup belong to the person rather than to any one book and are deliberately
 * NOT translated — the same line already drawn for `language` and `units`.
 */
class BookChromeLiteralsTest {

    /** Screens and overlays that belong to a BOOK. */
    private val bookScreens = listOf(
        "ReaderScreen.kt",
        "CourseDetailScreen.kt",
        "AddNoteOverlay.kt",
        "NoteViewOverlay.kt",
        "ChatPanel.kt",
        "ReaderNotesScreen.kt",
        "AskTextToolbar.kt",
    )

    /**
     * Literals that are not words and so cannot be translated: typographic
     * symbols and the like. Keep this SHORT — anything a reader would read as
     * language belongs in BOOK_CHROME_STRINGS instead.
     */
    private val notLanguage = setOf("A−", "A+")

    private val literalCall = Regex("""Text\(\s*"([^"]+)"""")

    @Test
    fun `every string a book screen prints is declared for translation`() {
        val dir = File("src/main/java/com/inkfirst/app/ui")
        assertTrue(dir.isDirectory, "expected to run from the app module: ${dir.absolutePath}")

        val undeclared = mutableListOf<String>()
        bookScreens.forEach { name ->
            val file = File(dir, name)
            if (!file.isFile) return@forEach
            file.readLines().forEachIndexed { i, line ->
                val code = line.substringBefore("//")
                literalCall.findAll(code).forEach { m ->
                    val text = m.groupValues[1]
                    // A leading glyph with the real word in t() is fine:
                    // `Text("← " + t("Back"))`.
                    if (text.isBlank()) return@forEach
                    if (text in notLanguage) return@forEach
                    if (text.none { it.isLetter() }) return@forEach
                    if (text in BOOK_CHROME_STRINGS) return@forEach
                    undeclared += "$name:${i + 1}  \"$text\""
                }
            }
        }

        assertTrue(
            undeclared.isEmpty(),
            "These book-screen strings are printed in English and never translated.\n" +
                "Add each to BOOK_CHROME_STRINGS and print it through t(…):\n" +
                undeclared.joinToString("\n"),
        )
    }
}
