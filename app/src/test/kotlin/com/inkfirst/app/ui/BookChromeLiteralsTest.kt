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
    fun `no book screen prints a bare English literal`() {
        val dir = File("src/main/java/com/inkfirst/app/ui")
        assertTrue(dir.isDirectory, "expected to run from the app module: ${dir.absolutePath}")

        val stray = mutableListOf<String>()
        bookScreens.forEach { name ->
            val file = File(dir, name)
            if (!file.isFile) return@forEach
            file.readLines().forEachIndexed { i, line ->
                val code = line.substringBefore("//")
                literalCall.findAll(code).forEach { m ->
                    val text = m.groupValues[1]
                    if (text.isBlank()) return@forEach
                    if (text in notLanguage) return@forEach
                    if (text.none { it.isLetter() }) return@forEach
                    stray += "$name:${i + 1}  \"$text\""
                }
            }
        }

        assertTrue(
            stray.isEmpty(),
            "These strings are printed as English literals and will never be " +
                "translated.\nEach belongs in ONE of two places:\n" +
                "  • typeset into the book (block label, byline, anything the EPUB " +
                "carries) -> BOOK_CHROME_STRINGS, printed with t(\"…\")\n" +
                "  • the app's own words (menu, chip, overlay, counter) -> " +
                "res/values/strings.xml, printed with stringResource(R.string.…)\n" +
                stray.joinToString("\n"),
        )
    }

    /** Nothing may be declared for per-book translation that no book prints. */
    @Test
    fun `every book string is one the book actually typesets`() {
        // A guard on the 2026-10-02 split: ~80 strings moved OUT of this list
        // into resources, and anything left behind would go on costing a
        // translation call per language for a string nobody renders.
        val src = File("../core/src/main/kotlin/com/inkfirst/core")
        if (!src.isDirectory) return
        val code = src.walkTopDown().filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() } +
            File("src/main/java/com/inkfirst/app/ui").walkTopDown()
                .filter { it.extension == "kt" }.joinToString("\n") { it.readText() }

        val unused = BOOK_CHROME_STRINGS.filterNot { s ->
            code.contains("t(\"$s\"") || code.contains("blockLabel")
        }
        assertTrue(
            unused.isEmpty(),
            "Declared for translation but never printed: $unused",
        )
    }

    /**
     * Every translation carries every string, with the same placeholders.
     *
     * A missing one falls back to English SILENTLY — which is the same failure
     * the runtime translator had, and moving to resources does not fix it by
     * itself. A placeholder that changes arity is worse: it throws at format
     * time, on a device, in a language the author may not read.
     */
    @Test
    fun `each translation matches the English resources`() {
        val values = File("src/main/res/values/strings.xml")
        if (!values.isFile) return
        val entry = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val placeholder = Regex("%\\d\\\$s")
        fun read(f: File) = entry.findAll(f.readText()).associate { it.groupValues[1] to it.groupValues[2] }

        val english = read(values)
        File("src/main/res").listFiles { f: File -> f.name.startsWith("values-") }
            ?.forEach { dir ->
                val file = File(dir, "strings.xml")
                if (!file.isFile) return@forEach
                val other = read(file)
                // app_name is a product name and is deliberately not translated.
                val missing = english.keys - other.keys - "app_name"
                assertTrue(missing.isEmpty(), "${dir.name} is missing: $missing")
                val stale = other.keys - english.keys
                assertTrue(stale.isEmpty(), "${dir.name} has strings English no longer has: $stale")
                val mismatched = other.keys.filter { k ->
                    english[k]?.let {
                        placeholder.findAll(it).map { m -> m.value }.sorted().toList() !=
                            placeholder.findAll(other.getValue(k)).map { m -> m.value }.sorted().toList()
                    } == true
                }
                assertTrue(mismatched.isEmpty(), "${dir.name} placeholder mismatch: $mismatched")
            }
    }
}
