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
