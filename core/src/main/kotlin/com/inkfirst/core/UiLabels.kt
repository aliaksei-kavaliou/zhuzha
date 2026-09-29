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
 * Every string the book's own furniture shows, and therefore exactly what
 * gets sent for translation.
 *
 * Keeping the list here rather than scraping the UI means a new label has to
 * be added in two places. The alternative — deriving it from the code — was
 * not worth a build-time source scan for a list this size, and the failure
 * mode of forgetting is mild: the label stays English.
 *
 * Counted strings are written so that a translation can avoid inflecting.
 * Russian needs three plural forms for "2 pages" and the app has no way to
 * choose between them, so the prompt asks for phrasings that work for any
 * number — the way e-readers write "Стр. 12 / 28" rather than a sentence.
 */
val BOOK_CHROME_STRINGS: List<String> = listOf(
    // Where you are
    "Contents",
    "Library",
    "Search",
    "Page {n} of {total}",
    "Ch {n}/{total}",
    "You are here",
    "Day {n}",
    "Day {from}–{to}",
    "~{n} min",
    "Chapter {n}",
    "unread",
    "reading",
    "done",
    // The book's front matter
    "{chapters} chapters · {minutes} min/day · ~{days} days",
    "Search this book",
    "chapters, notes and questions",
    "How the reading has gone",
    "nothing recorded yet",
    "CONTENTS",
    "Notes",
    "Questions",
    "Bookmarks",
    "no question yet",
    "tap ← / → edges to turn",
    // The menu over the page
    "Go to",
    "Reading",
    "Ask",
    "Mark",
    "Text",
    "Font",
    "Spacing",
    "Align",
    "Margin",
    "Chapter",
    "Figures",
    "Opening",
    "Closing",
    "Badges",
    "How it has gone",
    "About this chapter",
    "Bookmark this page",
    "Remove bookmark",
    "Tight",
    "Normal",
    "Relaxed",
    "Left",
    "Justify",
    "Narrow",
    "Wide",
    "Notebook",
    "Rewrite this chapter",
    "Size, spacing, margins →",
    "Pictures and rewrite →",
    // The row's gutter already says WHAT is being drawn ("Figures", "Badges"),
    // so the action needs no noun — which also means no plural to get wrong.
    "Draw {n}",
    "Draw opening picture",
    "Draw closing picture",
    "Redraw",
    "Redraw this picture?",
    "Which picture?",
    "The one you have is replaced. This cannot be undone.",
    "picture drawn",
    "needs the chapter picture first",
    // A bordered block that ran past the foot of a page and carries on over
    // the leaf. A placeholder rather than a bare "(continued)" so a translator
    // can put it wherever their language wants it.
    "{label} (continued)",
    // The closing picture is the cover's scene resolved, and it is briefed
    // from the cover's own words — so redrawing the cover leaves it showing
    // the previous metaphor. Said on the row rather than left to be noticed.
    "no longer matches the opening",
    "all drawn",
    "all {n} drawn",
    // Asking about a passage, and writing a note
    "Ask about this",
    "Add note",
    "Cancel",
    "Save",
    "Show answer",
    // The labels printed on the blocks themselves. These were hardcoded
    // English for the life of the app, so every exercise in a Russian book
    // said "TRY THIS" — the one piece of book chrome nobody thought of as
    // chrome, because it is drawn as part of the page rather than as a
    // control. Kept SHORT: they are set in small capitals in a narrow
    // gutter, and a long word there wraps.
    "TRY THIS",
    "RECAP",
    "FIGURE",
    "Delete this note",
    // The exported book — see [EpubExport]. An EPUB is still the book, so it
    // speaks the book's language like every other piece of its furniture.
    "Export this book",
    "an EPUB for any e-reader",
    "Answers",
    // The reader's footer, when a chapter is still missing pictures. Counted
    // with the number AFTER the noun so no language has to inflect it.
    "still to draw: {what}",
    // "×{n}" rather than "{n} pictures", because a count in front of a noun
    // inflects it in most languages and neither this app nor the reader can.
    // It also has to JOIN cleanly: "still to draw: figures: 1" was the first
    // attempt and reads as two labels stuck together.
    "figures ×{n}",
    "badges ×{n}",
    "chapter picture",
    "closing picture",
    // The exported book names its blocks in words. On screen a badge carries
    // that meaning; an EPUB has no badge, and a recap and an exercise are
    // otherwise the same bordered paragraph. "{n}" must survive translation —
    // see keepUsable — and must read correctly for ANY number, since neither
    // this app nor the reader can inflect.
    "Exercise",
    "Recap",
    "Answer {n}",
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
