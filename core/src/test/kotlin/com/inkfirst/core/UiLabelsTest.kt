package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class UiLabelsTest {

    @Test
    fun `an untranslated string comes back as written`() {
        val labels = UiLabels(mapOf("Contents" to "Оглавление"))

        assertEquals("Оглавление", labels.t("Contents"))
        // Shipped after the file was written: English, never blank.
        assertEquals("Bookmark this page", labels.t("Bookmark this page"))
    }

    @Test
    fun `placeholders are filled after translation, so a translation may move them`() {
        val labels = UiLabels(mapOf("Page {n} of {total}" to "Стр. {n} / {total}"))

        assertEquals("Стр. 12 / 28", labels.t("Page {n} of {total}", "n" to 12, "total" to 28))
    }

    @Test
    fun `English needs none of this`() {
        assertFalse(needsTranslation("English"))
        assertFalse(needsTranslation(" english "))
        assertFalse(needsTranslation(""))
        assertTrue(needsTranslation("Russian"))
    }

    @Test
    fun `a language becomes a filename a filesystem will accept`() {
        assertEquals("russian.json", labelsFileName("Russian"))
        assertEquals("chinese-simplified.json", labelsFileName("Chinese (Simplified)"))
        assertEquals("unknown.json", labelsFileName("   "))
    }

    // --- what the model hands back ----------------------------------------

    private fun pairs(vararg p: Pair<String, String>) = p.map { LabelPair(it.first, it.second) }

    @Test
    fun `a translation that lost a placeholder is dropped`() {
        // The only failure here that renders as nonsense rather than English:
        // "Page  of" with a hole in it.
        val kept = keepUsable(
            pairs("Page {n} of {total}" to "Страница из"),
            listOf("Page {n} of {total}"),
        )

        assertTrue(kept.isEmpty())
    }

    @Test
    fun `a translation that kept its placeholders is kept`() {
        val kept = keepUsable(
            pairs("Page {n} of {total}" to "Стр. {n} / {total}"),
            listOf("Page {n} of {total}"),
        )

        assertEquals("Стр. {n} / {total}", kept["Page {n} of {total}"])
    }

    @Test
    fun `a sentence where a label was asked for is dropped`() {
        // Furniture has to fit the row it sits on.
        val essay = "Раздел, в котором перечислены все главы этой книги по порядку"
        val kept = keepUsable(pairs("Contents" to essay), listOf("Contents"))

        assertTrue(kept.isEmpty())
    }

    @Test
    fun `a string nobody asked for is not stored`() {
        val kept = keepUsable(pairs("Delete everything" to "Удалить всё"), listOf("Contents"))

        assertTrue(kept.isEmpty())
    }

    @Test
    fun `a word that did not need translating is not stored either`() {
        val kept = keepUsable(pairs("Ask" to "Ask"), listOf("Ask"))

        assertTrue(kept.isEmpty())
    }

    // --- on disk ----------------------------------------------------------

    @Test
    fun `labels round trip, and belong to the language rather than the book`(@TempDir tmp: File) {
        val store = CourseStore(tmp)
        assertFalse(store.hasLabels("Russian"))

        store.writeLabels("Russian", mapOf("Contents" to "Оглавление"))

        assertTrue(store.hasLabels("Russian"))
        assertEquals("Оглавление", store.readLabels("Russian").t("Contents"))
        // One file, shared by every Russian book on the shelf.
        assertTrue(File(tmp, "labels/russian.json").isFile)
    }

    @Test
    fun `a language never translated reads as English rather than as empty`(@TempDir tmp: File) {
        assertEquals("Contents", CourseStore(tmp).readLabels("Polish").t("Contents"))
    }

    @Test
    fun `a hand-edit that broke the file costs the translation, not the book`(@TempDir tmp: File) {
        File(tmp, "labels").mkdirs()
        File(tmp, "labels/russian.json").writeText("{ this is not json")

        assertEquals("Contents", CourseStore(tmp).readLabels("Russian").t("Contents"))
    }

    @Test
    fun `a stray letter from a lookalike alphabet is put back`() {
        // From a real Russian EPUB: the answers heading came back as "Οтветы"
        // whose first letter is GREEK CAPITAL OMICRON. Invisible on the page,
        // which is how it survived — and it breaks in-book search for the
        // word and anything reading the book aloud.
        val greekO = "\u039F"
        val kept = keepUsable(
            listOf(LabelPair("Answers", greekO + "тветы")),
            listOf("Answers"),
        )

        assertEquals("\u041Eтветы", kept["Answers"])
    }

    @Test
    fun `a word written in one alphabet is left exactly alone`() {
        val kept = keepUsable(
            listOf(LabelPair("Answers", "Ответы"), LabelPair("Save", "Сохранить")),
            listOf("Answers", "Save"),
        )

        assertEquals("Ответы", kept["Answers"])
        assertEquals("Сохранить", kept["Save"])
    }

    @Test
    fun `separate words in different alphabets are not a homoglyph`() {
        // "EPUB" is Latin and the rest is Cyrillic, but they are separate
        // words — this is ordinary, correct translation.
        val kept = keepUsable(
            listOf(LabelPair("an EPUB for any e-reader", "EPUB для читалок")),
            listOf("an EPUB for any e-reader"),
        )

        assertEquals("EPUB для читалок", kept["an EPUB for any e-reader"])
    }

    @Test
    fun `a script that mixes inside a word on purpose is untouched`() {
        // Japanese writes kanji beside kana within one word as a matter of
        // course. None of those characters are lookalikes, so the rule must
        // not fire.
        val japanese = "\u7B54\u3048"
        val kept = keepUsable(listOf(LabelPair("Answers", japanese)), listOf("Answers"))

        assertEquals(japanese, kept["Answers"])
    }

    @Test
    fun `a word with no majority alphabet is refused rather than guessed at`() {
        // Half and half is not a typo to repair; dropping it falls back to
        // English, which is visibly wrong and therefore fixable by a person.
        val kept = keepUsable(
            listOf(LabelPair("OK", "\u041E\u039A")),
            listOf("OK"),
        )

        assertEquals(null, kept["OK"])
    }


    @Test
    fun `a label already stored with a lookalike letter is healed on read`() {
        // keepUsable only sees what the translator has just said, and
        // ensureLabels only asks for MISSING strings — so a label stored by
        // an older version is never re-examined and would stay broken for
        // the life of the book.
        val labels = UiLabels(mapOf("Answers" to "\u039Fтветы"))

        assertEquals("\u041Eтветы", labels.t("Answers"))
    }

}

/**
 * Reading back the raw map, which is what makes topping-up possible.
 *
 * `hasLabels` answers "was this language ever translated" — the wrong
 * question once BOOK_CHROME_STRINGS has grown, because it reports true for a
 * file that is missing every string added since it was written.
 */
class LabelMapTest {

    @org.junit.jupiter.api.Test
    fun `a stored language reports exactly the strings it holds`(
        @org.junit.jupiter.api.io.TempDir tmp: java.io.File,
    ) {
        val store = CourseStore(tmp)
        store.writeLabels("Russian", mapOf("Contents" to "Содержание"))

        val stored = store.readLabelMap("Russian")
        assertEquals(mapOf("Contents" to "Содержание"), stored)
        // The gap this exists to expose: translated, and yet missing a string.
        assertTrue(store.hasLabels("Russian"))
        assertTrue(BOOK_CHROME_STRINGS.any { it !in stored })
    }

    @org.junit.jupiter.api.Test
    fun `an untranslated language reports an empty map, not a broken one`(
        @org.junit.jupiter.api.io.TempDir tmp: java.io.File,
    ) {
        assertEquals(emptyMap<String, String>(), CourseStore(tmp).readLabelMap("Russian"))
    }

    @org.junit.jupiter.api.Test
    fun `a hand-edit that broke the JSON costs the translation, not the book`(
        @org.junit.jupiter.api.io.TempDir tmp: java.io.File,
    ) {
        java.io.File(tmp, "labels").mkdirs()
        java.io.File(tmp, "labels/russian.json").writeText("{ not json")
        assertEquals(emptyMap<String, String>(), CourseStore(tmp).readLabelMap("Russian"))
    }
}
