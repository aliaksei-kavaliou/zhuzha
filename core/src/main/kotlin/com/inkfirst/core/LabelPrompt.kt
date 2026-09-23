package com.inkfirst.core

/**
 * The brief for translating the book's furniture.
 *
 * Most of it is about SHAPE rather than meaning, because the model is good at
 * meaning and blind to the screen: these strings sit on rows a few
 * centimetres wide, next to each other, on a device that cannot inflect a
 * noun to match a number.
 */
internal fun buildLabelPrompt(language: String, strings: List<String>): String = buildString {
    appendLine("Translate the interface text of an e-reader app into $language.")
    appendLine()
    appendLine("The app compiles study books and shows them on a small e-ink screen. These")
    appendLine("strings are its furniture — a page header, a row in a menu, a button — and they")
    appendLine("sit around prose that is already written in $language.")
    appendLine()
    appendLine("RULES")
    appendLine("- Use the words a published e-reader or a printed book uses in $language, not a")
    appendLine("  literal translation. \"Contents\" is what a book's contents page is called there.")
    appendLine("- Keep it SHORT. Each of these goes on one row of a narrow screen; a translation")
    appendLine("  much longer than the English will be cut off. Prefer the shorter of two")
    appendLine("  correct words, and abbreviate the way that language's e-readers abbreviate.")
    appendLine("- Keep every {placeholder} exactly as it is written, spelled the same. You may")
    appendLine("  move it within the sentence. Never add or remove one.")
    appendLine("- A number in a placeholder can be ANY number, and this app cannot change a")
    appendLine("  noun's ending to agree with it. So phrase counted strings so that they are")
    appendLine("  correct whatever the number turns out to be — put the number after a colon,")
    appendLine("  or use the abbreviation an e-reader would use, rather than a sentence that")
    appendLine("  only reads correctly for one number.")
    appendLine("- Keep the capitalisation style of the source: a heading given in CAPITALS stays")
    appendLine("  capitals, a lowercase fragment stays lowercase.")
    appendLine("- Some of these are fragments that follow a number or a title (\"unread\",")
    appendLine("  \"reading\", \"done\"). Translate them as fragments, not as sentences.")
    appendLine("- If a word is genuinely used in English by readers of $language, leave it.")
    appendLine()
    appendLine("Return every string below, each with its source spelled exactly as given here.")
    appendLine("Some carry a note in brackets saying where they appear — that note is context")
    appendLine("for you and must not be translated or repeated in your answer.")
    appendLine()
    appendLine("STRINGS")
    strings.forEach { source ->
        val note = STRING_CONTEXT[source]
        appendLine(if (note == null) "- $source" else "- $source   [$note]")
    }
}

/**
 * Where a string appears, for the handful whose English is ambiguous out of
 * context.
 *
 * The English string is the key, which keeps the code simple and means new UI
 * falls back to English rather than to a blank — but it hands the translator
 * no context at all, and "Draw" is both a verb and a noun. The first Russian
 * run came back with "Рисунок 4" ("Drawing 4") where the row means "draw four
 * of them". A translator would have asked; this is the answer to the question
 * they would have asked.
 */
private val STRING_CONTEXT: Map<String, String> = mapOf(
    "Draw {n}" to "a button that draws {n} pictures — the verb, not the noun",
    "Ask" to "a menu heading, above a button that asks the AI a question",
    "Mark" to "a menu heading, above a button that bookmarks the page",
    "Text" to "a menu heading, above controls for text size, spacing and margins",
    "Reading" to "a menu heading, above a link to the reader's own notes",
    "Chapter" to "a menu heading, above actions that apply to this chapter",
    "done" to "a chapter's state: finished reading",
    "reading" to "a chapter's state: started but not finished",
    "unread" to "a chapter's state: not started",
    "Left" to "text alignment, the opposite of justified",
    "Normal" to "the middle option of three or four sizes",
    "Contents" to "a book's table of contents",
    "CONTENTS" to "a book's table of contents, as a heading in capitals",
)
