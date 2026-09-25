package com.inkfirst.core

/**
 * Turning parsed chapter [Block]s into the XHTML an EPUB is made of.
 *
 * Pure, and kept apart from [EpubExport]'s zip plumbing so every rendering
 * decision below is a unit test rather than something found by opening a
 * broken file in Calibre. XHTML is XML, not HTML: an unescaped `&` in a
 * chapter makes the whole book fail to open rather than rendering oddly, and
 * a chapter about "R&D" or "a < b" is not exotic. Hence [esc], applied at
 * every text boundary.
 */

/**
 * A chapter, rendered. [images] is the source names it actually used.
 *
 * [answersXhtml] is a SECOND document when the chapter has answers, written
 * beside it in the spine. It is a separate file rather than a section at the
 * bottom of this one because that is the only page break an EPUB reader
 * cannot ignore: `page-break-before` is CSS, and a reader showing the book in
 * its own typeface — which is what happens the moment someone changes the
 * font size — may not apply the publisher's stylesheet at all. Reported from
 * PocketBook, where the break was simply not honoured and the jump still
 * landed mid-chapter.
 */
internal data class ChapterDoc(
    val fileName: String,
    val title: String,
    val xhtml: String,
    val images: Set<String>,
    val answersFileName: String? = null,
    val answersXhtml: String? = null,
)

/** One reader annotation as the export carries it — a passage and what was said about it. */
data class ExportedNote(val quote: String, val note: String)

/** One question and its answer, with the passage it was asked about. */
data class ExportedQa(val passage: String, val question: String, val answer: String)

/**
 * Everything one chapter contributes, gathered by the caller so this file
 * touches no disk.
 *
 * [coverSrc] and [tailSrc] are the opening and closing pictures. They are
 * resolved by name rather than read from the Markdown because that is how the
 * reader does it too — nothing in a chapter's text references them (see
 * design.md §1), so the file name is the key.
 */
internal data class ChapterSource(
    val n: Int,
    val title: String,
    val markdown: String,
    val coverSrc: String? = null,
    val tailSrc: String? = null,
    val notes: List<ExportedNote> = emptyList(),
    val qa: List<ExportedQa> = emptyList(),
)

/**
 * The word a block of this kind calls itself in the exported book.
 *
 * Only the kinds the chapter model actually writes are named. An unknown
 * fence gets no label rather than a guess — a wrong word on the page is worse
 * than none, and a new kind would otherwise silently inherit one.
 */
internal fun blockLabel(kind: String): String? = when (kind) {
    "recap" -> "Recap"
    "exercise" -> "Exercise"
    else -> null
}

/**
 * A question reduced to one line, to reprint above its answer. Long ones are
 * cut at a word so the cue stays a cue — the full question is a tap away
 * through the number beside it.
 */
internal fun oneLine(text: String, limit: Int = 160): String {
    val flat = text.replace(Regex("\\s+"), " ").trim()
    if (flat.length <= limit) return flat
    val cut = flat.take(limit)
    val lastSpace = cut.lastIndexOf(' ')
    return (if (lastSpace > limit / 2) cut.take(lastSpace) else cut).trimEnd(',', '.', ';') + "…"
}

/** XML-escapes text. `"` is escaped too so the same function serves attributes. */
internal fun esc(s: String): String = buildString(s.length) {
    s.forEach { c ->
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}

private fun renderInline(inlines: List<Inline>): String = inlines.joinToString("") {
    when (it) {
        is Inline.Text -> esc(it.text)
        is Inline.Bold -> "<strong>${esc(it.text)}</strong>"
        is Inline.Italic -> "<em>${esc(it.text)}</em>"
        is Inline.Code -> "<code>${esc(it.text)}</code>"
    }
}

/**
 * The one rule the reader and the exporter must agree on: whether a chapter
 * carries its own title or needs the outline's.
 *
 * Extracted rather than reimplemented because getting it wrong in either
 * direction is visible — read it as "no title" and the chapter is titled
 * twice, once with the model's "Chapter 6:" prefix still attached. Level 1 or
 * 2, because the model writes a chapter title as `##` often enough; level 3
 * and deeper are section headings, and a chapter opening straight into one
 * genuinely has no title of its own.
 */
fun openingTitleHeading(blocks: List<Block>): Block.Heading? =
    (blocks.firstOrNull() as? Block.Heading)?.takeIf { it.level <= 2 }

/**
 * Splits an exercise fence into its question and its answer.
 *
 * The chapter prompt asks for the answer inside the fence on a line starting
 * `answer:`, so this is the only thing that knows where one ends and the
 * other begins. It lives here rather than in the reader because BOTH need it
 * now, and a second copy would drift — the reader hides the answer behind a
 * tap, the export moves it to the back of the chapter.
 */
fun splitExerciseBody(body: String): Pair<String, String> {
    val lines = body.lines()
    val answerIdx = lines.indexOfFirst { it.trim().lowercase().startsWith("answer:") }
    if (answerIdx == -1) return body.trim() to ""
    val question = lines.take(answerIdx).joinToString("\n").trim()
    val answerLine = lines[answerIdx].substringAfter(":").trim()
    val extra = lines.drop(answerIdx + 1).joinToString("\n").trim()
    val answer = if (extra.isEmpty()) answerLine else "$answerLine\n$extra"
    return question to answer
}

/** Fence kinds that are code rather than a block with a voice — see [badgeKindFor]. */
private fun isCodeFence(kind: String): Boolean =
    badgeKindFor(kind) == "code" || kind.isBlank()

/**
 * Renders one chapter.
 *
 * [images] maps a picture's source name to its path inside the EPUB. A name
 * that is absent is simply not drawn: an undrawn figure slot renders its alt
 * text instead, exactly as it does in the reader, because a book missing a
 * picture is still a book.
 */
internal fun renderChapter(
    source: ChapterSource,
    images: Map<String, String>,
    labels: UiLabels,
    includeAnnotations: Boolean,
    language: String,
): ChapterDoc {
    val used = linkedSetOf<String>()
    val body = StringBuilder()
    // Answers are collected as the chapter is walked and printed at the end,
    // because an EPUB has no pages to put them on. See [EpubExport].
    // The question travels with the answer. Landing on an answer two pages
    // from its question and being shown a bare number is the defect this
    // whole section exists to avoid — see the answers block below.
    val answers = mutableListOf<Triple<Int, String, String>>()
    var exerciseN = 0
    // Anchors carry the chapter, because the counter restarts in every
    // chapter and `ex1` would otherwise exist once per chapter in the book.
    // Per spec each XHTML file is its own document, so duplicates resolve
    // correctly — but a reader that flattens the spine into one document
    // resolves them globally, and then every "show answer" in the book lands
    // in chapter one.
    val anchor = "c${source.n}"
    val chapterFile = "ch%02d.xhtml".format(source.n)
    val answersFile = "ch%02d-answers.xhtml".format(source.n)

    fun img(src: String?, alt: String, cls: String) {
        val href = src?.let { images[it] } ?: return
        used += src
        body.append("<figure class=\"$cls\"><img src=\"${esc(href)}\" alt=\"${esc(alt)}\"/></figure>\n")
    }

    val blocks = parseMarkdown(source.markdown)
    val ownTitle = openingTitleHeading(blocks)

    body.append("<h1>${esc(source.title)}</h1>\n")
    // "Chapter N" and nothing else. The reader's byline also carries the
    // estimated minutes, the day in the schedule and the chapter's status —
    // all three are properties of READING this book on this device, and none
    // of them means anything in a file someone else opens.
    body.append("<p class=\"byline\">${esc(labels.t("Chapter {n}", "n" to source.n))}</p>\n")
    img(source.coverSrc, source.title, "cover")

    // Consecutive list items become one list. The parser emits them flat,
    // which is right for a paginator that measures block by block and wrong
    // for a document format where a bare <li> is invalid.
    var openList: String? = null
    fun closeList() {
        openList?.let { body.append("</$it>\n") }
        openList = null
    }
    fun openList(tag: String) {
        if (openList != tag) {
            closeList()
            body.append("<$tag>\n")
            openList = tag
        }
    }

    blocks.forEachIndexed { i, block ->
        // The model's own title heading was printed above as <h1>; printing
        // it again is the "chapter titled twice" defect in export form.
        if (i == 0 && block === ownTitle) return@forEachIndexed
        if (block !is Block.BulletItem && block !is Block.OrderedItem) closeList()

        when (block) {
            is Block.Heading -> {
                // Everything under the chapter title shifts down one level:
                // the <h1> above is the chapter, so the model's own h1
                // sections become h2 and the document keeps one root.
                val level = (block.level + 1).coerceIn(2, 6)
                body.append("<h$level>${renderInline(block.inlines)}</h$level>\n")
                img(block.coverSrc, source.title, "cover")
            }
            is Block.Paragraph -> body.append("<p>${renderInline(block.inlines)}</p>\n")
            is Block.BulletItem -> {
                openList("ul")
                body.append("<li>${renderInline(block.inlines)}</li>\n")
            }
            is Block.OrderedItem -> {
                openList("ol")
                body.append("<li>${renderInline(block.inlines)}</li>\n")
            }
            Block.Rule -> body.append("<hr/>\n")
            is Block.CustomFence -> when {
                block.kind == "figure" -> {
                    val alt = block.params["alt"] ?: block.body.trim()
                    val src = block.params["src"]
                    if (src != null && images.containsKey(src)) {
                        used += src
                        body.append(
                            "<figure><img src=\"${esc(images.getValue(src))}\" " +
                                "alt=\"${esc(alt)}\"/><figcaption>${esc(alt)}</figcaption></figure>\n",
                        )
                    } else if (alt.isNotBlank()) {
                        // A slot the budget never drew. The alt text is the
                        // picture's brief and reads as a caption on its own.
                        body.append("<p class=\"undrawn\">${esc(alt)}</p>\n")
                    }
                }
                block.kind == "tail" -> img(block.params["src"], source.title, "tail")
                block.kind == "meta" -> Unit // the reader's byline; replaced above
                block.kind == "exercise" -> {
                    val (question, answer) = splitExerciseBody(block.body)
                    exerciseN++
                    body.append("<aside class=\"exercise\" id=\"$anchor-ex$exerciseN\">\n")
                    body.append(
                        "<p class=\"block-label\">" +
                            "${esc(labels.t("Exercise"))} $exerciseN</p>\n",
                    )
                    body.append(paragraphs(question))
                    if (answer.isNotBlank()) {
                        answers += Triple(exerciseN, question, answer)
                        body.append(
                            "<p class=\"answer-link\"><a href=\"$answersFile#$anchor-ans$exerciseN\">" +
                                "${esc(labels.t("Show answer"))}</a></p>\n",
                        )
                    }
                    body.append("</aside>\n")
                }
                isCodeFence(block.kind) ->
                    body.append("<pre><code>${esc(block.body.trimEnd())}</code></pre>\n")
                else -> {
                    body.append("<aside class=\"${esc(block.kind)}\">\n")
                    // A recap and an exercise were distinguished ONLY by a
                    // CSS border style, which most readers render alike and
                    // none of which says what the block is. In the app the
                    // badge carries that meaning; an EPUB has no badge, so
                    // the block has to say it in words.
                    blockLabel(block.kind)?.let {
                        body.append("<p class=\"block-label\">${esc(labels.t(it))}</p>\n")
                    }
                    body.append(paragraphs(block.body))
                    body.append("</aside>\n")
                }
            }
        }
    }
    closeList()

    // ANSWERS AT THE BACK, the way a printed textbook does it — an EPUB
    // reflows, so "the next page" does not exist as a place to hide one, and
    // printing the answer under its exercise means you cannot not read it.
    // Linked both ways, which is the one thing paper cannot do.
    // THE ANSWERS ARE THEIR OWN DOCUMENT. As a section at the foot of the
    // chapter they began a paragraph or two after the last exercise, so
    // jumping to answer 1 landed on a page whose visible top was a DIFFERENT
    // exercise's question — reported from a real reader as the link going to
    // the wrong place. A CSS page break did not fix it, because a reader
    // rendering the book in its own typeface need not apply the publisher's
    // stylesheet at all. A separate file is a page boundary no reader can
    // ignore.
    val answersBody = if (answers.isEmpty()) null else buildString {
        append("<h1>${esc(labels.t("Answers"))}</h1>\n")
        append("<p class=\"byline\">${esc(source.title)}</p>\n")
        append("<section class=\"answers\" id=\"$anchor-answers\">\n")
        answers.forEach { (n, question, answer) ->
            append("<div class=\"answer\" id=\"$anchor-ans$n\">\n")
            append(
                "<p class=\"answer-n\"><a href=\"$chapterFile#$anchor-ex$n\">" +
                    "${esc(labels.t("Answer {n}").replace("{n}", n.toString()))}</a></p>\n",
            )
            // The question again. The reader arrived from another document
            // and cannot see what was asked; a bare number does not tell
            // them whether this is the answer they wanted.
            append("<p class=\"answer-q\">${esc(oneLine(question))}</p>\n")
            append(paragraphs(answer))
            append("</div>\n")
        }
        append("</section>\n")
    }

    if (includeAnnotations && (source.notes.isNotEmpty() || source.qa.isNotEmpty())) {
        body.append("<section class=\"reader\">\n")
        if (source.notes.isNotEmpty()) {
            body.append("<h2>${esc(labels.t("Your notes"))}</h2>\n")
            source.notes.forEach { n ->
                body.append("<div class=\"note\">\n")
                body.append("<blockquote>${esc(n.quote)}</blockquote>\n")
                body.append(paragraphs(n.note))
                body.append("</div>\n")
            }
        }
        if (source.qa.isNotEmpty()) {
            body.append("<h2>${esc(labels.t("Your questions"))}</h2>\n")
            source.qa.forEach { q ->
                body.append("<div class=\"qa\">\n")
                if (q.passage.isNotBlank()) {
                    body.append("<blockquote>${esc(q.passage)}</blockquote>\n")
                }
                body.append("<p class=\"q\">${esc(q.question)}</p>\n")
                body.append(paragraphs(q.answer))
                body.append("</div>\n")
            }
        }
        body.append("</section>\n")
    }

    return ChapterDoc(
        fileName = chapterFile,
        title = source.title,
        xhtml = xhtmlPage(source.title, body.toString(), language),
        images = used,
        answersFileName = answersBody?.let { answersFile },
        answersXhtml = answersBody?.let {
            xhtmlPage("${labels.t("Answers")} — ${source.title}", it, language)
        },
    )
}

/** Blank-line-separated prose into paragraphs. Fence bodies are plain text. */
private fun paragraphs(text: String): String = text.trim()
    .split(Regex("\n\\s*\n"))
    .filter { it.isNotBlank() }
    .joinToString("") { "<p>${renderInline(parseInlines(it.trim()))}</p>\n" }

/** The XHTML wrapper every document in the book shares. */
internal fun xhtmlPage(title: String, body: String, language: String): String = """
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="${esc(langCode(language))}" lang="${esc(langCode(language))}">
<head>
<meta charset="utf-8"/>
<title>${esc(title)}</title>
<link rel="stylesheet" type="text/css" href="style.css"/>
</head>
<body>
$body</body>
</html>
""".trimStart()

/**
 * A BCP-47 code for the handful of languages a book is likely to be in.
 *
 * `language` in course.json is a human-readable NAME, free text, so exotic
 * choices work — which means there is no code to look up for most of them.
 * Unknown names fall back to "en" rather than being passed through: a
 * `xml:lang` of "Belarusian" is invalid and some readers reject the file
 * outright, where a wrong-but-valid code only costs hyphenation.
 */
internal fun langCode(language: String): String = when (language.trim().lowercase()) {
    "english" -> "en"
    "russian", "русский" -> "ru"
    "belarusian", "беларуская" -> "be"
    "ukrainian" -> "uk"
    "polish", "polski" -> "pl"
    "german", "deutsch" -> "de"
    "french", "français" -> "fr"
    "spanish", "español" -> "es"
    "italian", "italiano" -> "it"
    "portuguese", "português" -> "pt"
    "dutch", "nederlands" -> "nl"
    "czech" -> "cs"
    "lithuanian" -> "lt"
    "latvian" -> "lv"
    "estonian" -> "et"
    "swedish" -> "sv"
    "norwegian" -> "no"
    "finnish" -> "fi"
    "danish" -> "da"
    "turkish" -> "tr"
    "japanese" -> "ja"
    "korean" -> "ko"
    "chinese", "chinese (simplified)" -> "zh"
    "arabic" -> "ar"
    "hebrew" -> "he"
    "hindi" -> "hi"
    else -> "en"
}
