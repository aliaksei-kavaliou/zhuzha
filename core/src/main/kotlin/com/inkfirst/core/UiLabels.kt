package com.inkfirst.core

/**
 * The words the app says AROUND a book, in the book's own language.
 *
 * A course written in Russian used to sit inside English furniture: "≡
 * Contents", "Page 12 of 28", "Bookmark this page". The prose was the
 * reader's language and everything holding it was not.
 *
 * Two decisions shape this:
 *
 * **The English string is the key.** There is no registry of `contents_title`
 * identifiers to invent and keep in step — a site says `t("Bookmark this
 * page")` and the stored file maps that sentence to its translation. New UI
 * therefore appears in English until the labels are regenerated, which is the
 * right failure: one English row in a Russian book, never a blank one.
 *
 * **Labels belong to the LANGUAGE, not the book.** They live at
 * `<courses-root>/labels/<language>.json`, so the second Russian book reuses
 * the first one's words rather than paying for its own — and, more
 * importantly, agrees with it. Two Russian books using different words for
 * "Contents" would be worse than leaving both in English. The file is plain
 * JSON and meant to be edited by hand when the model picks a clumsy word.
 *
 * What is NOT translated: the Library, Settings, New course, Backup. Those
 * belong to the person using the app rather than to any one book, the same
 * line already drawn for [Course.language] and [Course.units] — per course,
 * not per device.
 */
class UiLabels(translations: Map<String, String>) {

    /**
     * Repaired on the way IN, not only on the way out of the translator.
     *
     * [keepUsable] checks what the model has just said, which does nothing
     * for a label already sitting in `labels/<language>.json` from an older
     * version — and `ensureLabels` only asks for strings that are MISSING, so
     * a stored label is never re-examined. A real Russian book had its
     * answers heading stored as "Οтветы" with a Greek omicron and would have
     * kept it for ever. The repair is pure and deterministic, so running it
     * on every read costs nothing and heals the file the next time it is
     * written.
     */
    private val translations: Map<String, String> =
        translations.mapNotNull { (k, v) -> healHomoglyphs(v)?.let { k to it } }.toMap()

    val isEmpty: Boolean get() = translations.isEmpty()

    /** This string in the book's language, or the string itself. */
    fun t(english: String): String =
        translations[english]?.takeIf { it.isNotBlank() } ?: english

    /**
     * The same, with `{name}` placeholders filled in. Substitution happens
     * AFTER translation, so a translation may move the placeholders around —
     * which is most of the reason they are named rather than positional.
     */
    fun t(english: String, vararg values: Pair<String, Any>): String {
        var out = t(english)
        values.forEach { (name, value) -> out = out.replace("{$name}", value.toString()) }
        return out
    }

    companion object {
        /** No translations: every string comes back as written. */
        val ENGLISH = UiLabels(emptyMap())
    }
}

/**
 * Every string that is part of a BOOK, and therefore translated per course.
 *
 * THE LINE IS WHAT GETS TYPESET, NOT WHAT IS A CONTROL. A string belongs here
 * when a printed copy would carry it, or when the exported EPUB does — there is
 * no app around an EPUB, so whatever it prints has to be the book's own
 * language. Block labels, the chapter byline and the exported words qualify.
 *
 * Everything else — the menu, the chip, the overlays, the page counter, the
 * Library — belongs to the PERSON using the app and lives in
 * `app/src/main/res/values/strings.xml`, following the device language the way
 * every other reader does. A Russian novel on an English Kindle does not turn
 * the menus Russian, and neither do we (2026-10-02; before that date this list
 * held 98 strings and dragged the whole interface along with the book).
 *
 * Translated at RUNTIME by the model, unlike the app's own words, and that
 * asymmetry is the point: the set of UI languages is ours to choose and ship,
 * while the set of BOOK languages is open-ended — someone will ask for a
 * Spanish book tomorrow. The cost of runtime translation (non-determinism, a
 * call, no review before it ships) is worth paying for an open set and not for
 * a closed one.
 *
 * Keeping the list here rather than scraping the UI means a new label has to be
 * added in two places; `BookChromeLiteralsTest` is what stops the two drifting.
 */
val BOOK_CHROME_STRINGS: List<String> = listOf(
    // The block labels, set into the page itself. These were hardcoded English
    // for the life of the app, so every exercise in a Russian book said "TRY
    // THIS" — the one piece of the book nobody thought of as the book, because
    // it is drawn as part of the page rather than as a control. Kept SHORT:
    // they are set in small capitals in a narrow gutter, and a long word wraps.
    "TRY THIS",
    "RECAP",
    "FIGURE",
    // No "(continued)" any more. Exercise and recap lost their frames on
    // 2026-10-06 and flow like prose, so a piece that carries on over the
    // leaf announces nothing — there is no box to reintroduce. Books that
    // already have the string translated keep it on disk, unused.
    // Inside the exercise box, so it is part of the block rather than a
    // control beside it — and the EPUB prints its own form of the same thing.
    "Show answer",
    // The chapter byline: "Chapter 5 · ~15 min · Day 3". Typeset at the head of
    // the chapter like a subtitle, and "Chapter {n}" travels into the EPUB.
    // Counted strings are written so a translation can avoid inflecting:
    // Russian needs three plural forms for "2 pages" and the app cannot choose
    // between them, so the prompt asks for phrasings that work for any number.
    "Chapter {n}",
    "~{n} min",
    "Day {n}",
    "Day {from}–{to}",
    // The exported book. An EPUB is the book with no app around it at all —
    // whatever is printed in it has to be the book's language, because there is
    // nothing else to fall back on. See [EpubExport].
    "Contents",
    "Exercise",
    "Recap",
    "Answers",
    "Answer {n}",
    // The reader's own revealed answer, printed on the page above whatever
    // the reader wrote by hand. It was a hardcoded "Answer: " literal until
    // 2026-10-06, so a Russian book revealed its answers in English while its
    // own EPUB printed them in Russian. The colon is inside the string: a
    // French translator wants a space before it, and some scripts want none.
    "Answer:",
    "Your notes",
    "Your questions",
    "This copy includes your notes and questions.",
)


/** Whether a book's language needs any of this at all. */
fun needsTranslation(language: String): Boolean =
    language.isNotBlank() && !language.trim().equals("English", ignoreCase = true)

/**
 * The file name a language's labels are stored under — lower-cased and
 * stripped to something a filesystem is happy with, so "Chinese
 * (Simplified)" becomes "chinese-simplified".
 */
fun labelsFileName(language: String): String {
    val slug = language.trim().lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .trim('-')
        .replace(Regex("-+"), "-")
    return "${slug.ifEmpty { "unknown" }}.json"
}
