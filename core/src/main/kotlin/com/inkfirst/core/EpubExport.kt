package com.inkfirst.core

import java.io.File
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports a course as an EPUB 3 file.
 *
 * Why EPUB and not PDF: an EPUB is reflowable, so it reads at any font size on
 * any device, and structurally it is already what this app stores — text,
 * pictures, and a manifest. A PDF is fixed-layout, which means owning
 * pagination, fonts and image placement against Android's bare `PdfDocument`
 * canvas; the reader's own paginator does not transfer, because it paginates
 * to a SCREEN with screen typography. If a PDF is ever wanted, converting this
 * file is the honest route.
 *
 * What is exported is THE BOOK: chapters, pictures, exercises and their
 * answers. Reader annotations are opt-in ([EpubOptions.includeAnnotations])
 * and default to off, because export is irreversible — a file handed to
 * someone takes whatever is in it, and "I don't understand what a variable is"
 * is not something anyone meant to publish.
 *
 * Three kinds of user content are excluded whatever the flag says. Ink,
 * because strokes are anchored to block indices and an EPUB reflows, so they
 * would land correctly at exactly one font size and wrongly at every other.
 * Bookmarks, which are app state. And `reader.md`, which is an input to
 * generation rather than part of the book.
 */
class EpubExport(private val store: CourseStore) {

    /**
     * Writes the book to [out], which is closed by the caller.
     *
     * @param modified an ISO-8601 instant for the EPUB's required
     *   `dcterms:modified`. Passed in rather than read from the clock so the
     *   output is byte-reproducible and therefore testable.
     */
    fun write(
        slug: String,
        out: OutputStream,
        options: EpubOptions = EpubOptions(),
        modified: String,
    ): EpubResult {
        val course = store.load(slug) ?: throw IllegalArgumentException("No course '$slug'")
        val labels = store.readLabels(course.language)

        val sources = course.chapters.mapNotNull { chapter ->
            val markdown = store.readChapter(slug, chapter.slug) ?: return@mapNotNull null
            if (markdown.isBlank()) return@mapNotNull null
            ChapterSource(
                n = chapter.n,
                title = chapter.title,
                markdown = markdown,
                coverSrc = store.coverFile(slug, chapter.slug)?.name,
                tailSrc = store.tailFile(slug, chapter.slug)?.name,
                notes = if (options.includeAnnotations) notesFor(course, chapter.slug) else emptyList(),
                qa = if (options.includeAnnotations) qaFor(slug, chapter.slug) else emptyList(),
            )
        }
        require(sources.isNotEmpty()) { "Nothing to export: no chapter has been written yet" }

        // Every picture the book could use, by the name a chapter refers to
        // it by. Built up front so a chapter that names a picture which was
        // never drawn simply renders its alt text instead of failing.
        val pictures = collectPictures(slug, sources)
        val hrefs = pictures.keys.associateWith { "images/$it" }

        val docs = sources.map {
            renderChapter(it, hrefs, labels, options.includeAnnotations, course.language)
        }
        // The book's cover is the FIRST chapter's opening picture, and it is
        // now printed on a cover PAGE as well as at the head of that chapter.
        // Added to the used set in its own right: a chapter that somehow does
        // not draw its own cover must still ship the file the cover page
        // names, or the first page of the book is a broken image.
        val bookCoverSrc = sources.firstOrNull()?.coverSrc?.takeIf { hrefs.containsKey(it) }
        val usedPictures = docs.flatMap { it.images }.toSet() + listOfNotNull(bookCoverSrc)

        ZipOutputStream(out).use { zip ->
            // The mimetype entry MUST be first and MUST be STORED, not
            // deflated — it is how a reader identifies the file at all, and a
            // compressed one is rejected by strict readers with no useful
            // error. This is the single most common way a hand-built EPUB
            // fails to open.
            zip.putNextEntry(storedEntry("mimetype", EPUB_MIMETYPE.toByteArray()))
            zip.write(EPUB_MIMETYPE.toByteArray())
            zip.closeEntry()

            zip.text("META-INF/container.xml", CONTAINER_XML)
            zip.text("OEBPS/style.css", EPUB_CSS)
            zip.text(
                "OEBPS/cover.xhtml",
                coverPage(course, docs, options, labels, bookCoverSrc?.let { hrefs.getValue(it) }),
            )
            docs.forEach {
                zip.text("OEBPS/${it.fileName}", it.xhtml)
                // The answers travel as their own document — see [ChapterDoc].
                if (it.answersFileName != null && it.answersXhtml != null) {
                    zip.text("OEBPS/${it.answersFileName}", it.answersXhtml)
                }
            }
            zip.text("OEBPS/nav.xhtml", navDocument(course, docs, labels))
            // toc.ncx is EPUB 2 and formally superseded, but it costs a few
            // lines and it is what older readers — including some shipped on
            // e-ink hardware — actually navigate by.
            zip.text("OEBPS/toc.ncx", ncxDocument(course, docs))
            zip.text(
                "OEBPS/content.opf",
                opfDocument(
                    course, docs, usedPictures, hrefs, modified,
                    // Without this the OPF never says which image is the
                    // cover, so a reader falls back to its own placeholder —
                    // reported 2026-10-20 after importing an export: "it has
                    // reader random default cover".
                    coverPicture = bookCoverSrc,
                ),
            )
            usedPictures.sorted().forEach { name ->
                val file = pictures.getValue(name)
                zip.putNextEntry(ZipEntry("OEBPS/images/$name"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }

        return EpubResult(
            chapters = docs.size,
            pictures = usedPictures.size,
            annotated = options.includeAnnotations,
        )
    }

    /** Notes the reader typed. An "ask" mark carries no note text and is not one. */
    private fun notesFor(course: Course, chapterSlug: String): List<ExportedNote> =
        course.annotations
            .filter { it.chapterSlug == chapterSlug && it.note.isNotBlank() }
            .sortedWith(compareBy({ it.at.ifEmpty { it.created } }, { it.blockIndex }))
            .map { ExportedNote(quote = it.quote.trim(), note = it.note.trim()) }

    private fun qaFor(slug: String, chapterSlug: String): List<ExportedQa> =
        QaTranscript.parse(store.readQa(slug, chapterSlug))
            .filter { it.question.isNotBlank() && it.answer.isNotBlank() }
            .map {
                ExportedQa(
                    passage = it.passage?.trim().orEmpty(),
                    question = it.question.trim(),
                    answer = it.answer.trim(),
                )
            }

    /**
     * Every picture file this export could need, keyed by the name a chapter
     * refers to it by.
     *
     * Figures are named in the Markdown (`src=`); covers and closing pictures
     * are found by chapter slug, because nothing in the text references them.
     * Badges are deliberately absent: they are a ~120px mark tuned for an
     * e-ink block heading, and in a reflowable book at an unknown size they
     * are a smudge beside a paragraph. The block still reads as a block —
     * CSS gives it a rule and an indent, which is what the badge was standing
     * in for.
     */
    private fun collectPictures(slug: String, sources: List<ChapterSource>): Map<String, File> {
        val found = linkedMapOf<String, File>()
        sources.forEach { source ->
            listOfNotNull(source.coverSrc, source.tailSrc).forEach { name ->
                store.figureFile(slug, name)?.let { found[name] = it }
            }
            findFigureSlots(source.markdown).forEach { slot ->
                val src = slot.src ?: return@forEach
                store.figureFile(slug, src)?.let { found[src] = it }
            }
        }
        return found
    }
}

/** What an export includes beyond the book itself. */
data class EpubOptions(val includeAnnotations: Boolean = false)

/** What an export produced, for telling the reader what they got. */
data class EpubResult(val chapters: Int, val pictures: Int, val annotated: Boolean)

const val EPUB_MIMETYPE: String = "application/epub+zip"

/** The extension belongs to the file name AND the mime type — see design.md §15. */
const val EPUB_EXTENSION: String = "epub"

private fun ZipOutputStream.text(path: String, content: String) {
    putNextEntry(ZipEntry(path))
    write(content.toByteArray(Charsets.UTF_8))
    closeEntry()
}

/**
 * A STORED (uncompressed) zip entry. Storing requires the size and CRC to be
 * known before the data is written, which is why this exists at all.
 */
private fun storedEntry(name: String, bytes: ByteArray): ZipEntry =
    ZipEntry(name).apply {
        method = ZipEntry.STORED
        size = bytes.size.toLong()
        compressedSize = bytes.size.toLong()
        crc = CRC32().apply { update(bytes) }.value
    }

private val CONTAINER_XML = """
<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
""".trimStart()

/**
 * Deliberately restrained. An EPUB is read on hardware this app knows nothing
 * about, at a font size the reader chose, so anything opinionated about size
 * or family fights the person reading it. What is styled is STRUCTURE — a
 * block has to still read as a block once its badge is gone.
 *
 * **No block is marked with a SIDE border.** CSS paints a left or right
 * border on every fragment of a box the reader breaks across pages, so such
 * a rule runs to the foot of one page and starts hard against the top of the
 * next; a top or bottom border is painted only at the box's real edges and
 * splits cleanly. A block is therefore a bordered label band plus the
 * [BLOCK_END] rule, which is what the reader screen arrived at in October
 * for the same reason — and a quotation is marked the way print marks one,
 * by indenting it.
 */
private val EPUB_CSS = """
body { margin: 0 1em; line-height: 1.5; }
h1 { margin: 1em 0 0.2em; line-height: 1.25; }
section.cover-page { text-align: center; }
section.cover-page h1 { margin-top: 1.6em; }
figure.cover-art { margin: 1.2em 0 1em; }
figure.cover-art img { max-height: 65vh; }
p.byline { margin: 0 0 1.5em; font-style: italic; }
p { margin: 0 0 0.8em; text-align: justify; }
figure { margin: 1.4em 0; text-align: center; page-break-inside: avoid; }
figure img { max-width: 100%; height: auto; }
p.undrawn { font-style: italic; text-align: center; margin: 1.4em 2em; }
hr { border: 0; border-top: 1px solid currentColor; opacity: 0.25; margin: 1.6em 20%; }
pre { white-space: pre-wrap; font-size: 0.85em; padding: 0.6em 0; border-top: 1px solid currentColor; border-bottom: 1px solid currentColor; }
aside { margin: 1.4em 0; }
aside > p { font-style: italic; }
p.block-label { font-style: normal; font-weight: bold; font-size: 0.8em; letter-spacing: 0.08em; text-transform: uppercase; border: 1px solid currentColor; padding: 0.3em 0.6em; margin: 0 0 0.8em; }
hr.block-end { border: 0; border-top: 1px solid currentColor; opacity: 1; margin: 0.2em 0 1.4em; }
p.answer-link { font-style: italic; font-size: 0.9em; margin-bottom: 0.2em; }
section.answers, section.reader { margin-top: 2.5em; border-top: 1px solid currentColor; }
div.answer, div.note, div.qa { margin: 1.2em 0; }
p.answer-n { font-weight: bold; margin-bottom: 0.2em; }
blockquote { margin: 0 0 0.5em; padding-left: 1.2em; font-style: italic; }
p.q { font-weight: bold; margin-bottom: 0.3em; }
""".trimStart()

/**
 * The first page of the book: its name, its picture, and what it carries.
 *
 * It used to be the title alone, with the picture declared in the OPF as
 * `cover-image` and nowhere else. That gets a thumbnail onto a reader's shelf
 * and nothing else — opening the book landed on a page with a title on it and
 * no cover anywhere, or, in a reader that makes a page out of the declared
 * image by itself, on a bare picture with no book name. Reported from
 * PocketBook 2026-10-21: "Cover is just an image now and it has a non cover
 * format. Expected would be image and a book name".
 *
 * So the cover is a DOCUMENT, the way the answers became one in §18.1 and for
 * the same reason: what a reader shows cannot be left to what a reader infers.
 * `cover-image` stays declared as well — that is the shelf thumbnail, a
 * different job.
 *
 * The title is printed ABOVE the picture on purpose. A reader that does not
 * scale a 1024px illustration down to its viewport pushes everything under it
 * onto the next page, and a cover whose book name is on page two is not a
 * cover. Order is the one piece of layout no stylesheet can be dropped from.
 */
private fun coverPage(
    course: Course,
    docs: List<ChapterDoc>,
    options: EpubOptions,
    labels: UiLabels,
    coverHref: String?,
): String {
    val body = buildString {
        append("<section class=\"cover-page\" epub:type=\"cover\">\n")
        append("<h1>${esc(course.title)}</h1>\n")
        if (coverHref != null) {
            append(
                "<figure class=\"cover-art\"><img src=\"${esc(coverHref)}\" " +
                    "alt=\"${esc(course.title)}\"/></figure>\n",
            )
        }
        append("<p class=\"byline\">${esc(labels.t("Contents"))}: ${docs.size}</p>\n")
        if (options.includeAnnotations) {
            // Said on the page, not just in a dialog. Whoever opens this file
            // next may not be the person who chose to put their notes in it.
            // <em> for the same reason the undrawn brief carries one: the
            // class only sets it apart in a reader that honours our CSS.
            append(
                "<p class=\"undrawn\"><em>" +
                    esc(labels.t("This copy includes your notes and questions.")) +
                    "</em></p>\n",
            )
        }
        append("</section>\n")
    }
    return xhtmlPage(course.title, body, course.language)
}

private fun navDocument(course: Course, docs: List<ChapterDoc>, labels: UiLabels): String {
    val items = docs.joinToString("") {
        "<li><a href=\"${it.fileName}\">${esc(it.title)}</a></li>\n"
    }
    val body = "<nav epub:type=\"toc\" id=\"toc\">\n<h1>${esc(labels.t("Contents"))}</h1>\n" +
        "<ol>\n$items</ol>\n</nav>\n"
    return xhtmlPage(labels.t("Contents"), body, course.language)
}

private fun ncxDocument(course: Course, docs: List<ChapterDoc>): String {
    val points = docs.mapIndexed { i, doc ->
        """  <navPoint id="np${i + 1}" playOrder="${i + 1}">
    <navLabel><text>${esc(doc.title)}</text></navLabel>
    <content src="${doc.fileName}"/>
  </navPoint>"""
    }.joinToString("\n")
    return """
<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head><meta name="dtb:uid" content="${esc(bookId(course))}"/></head>
  <docTitle><text>${esc(course.title)}</text></docTitle>
  <navMap>
$points
  </navMap>
</ncx>
""".trimStart()
}

private fun opfDocument(
    course: Course,
    docs: List<ChapterDoc>,
    pictures: Set<String>,
    hrefs: Map<String, String>,
    modified: String,
    /** The picture to declare as the book's cover, if the book has one. */
    coverPicture: String? = null,
): String {
    val sortedPictures = pictures.sorted()
    // Declared TWICE on purpose. `properties="cover-image"` is the EPUB 3
    // form; `<meta name="cover">` is EPUB 2's and is formally superseded,
    // which has never stopped readers from being the only thing they look
    // for — the same reason toc.ncx is still written below. Both cost a line.
    val coverId = coverPicture
        ?.let { sortedPictures.indexOf(it) }
        ?.takeIf { it >= 0 }
        ?.let { "img${it + 1}" }
    val manifest = buildString {
        append("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n")
        append("    <item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n")
        append("    <item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>\n")
        append(
            "    <item id=\"cover\" href=\"cover.xhtml\" " +
                "media-type=\"application/xhtml+xml\"/>\n",
        )
        docs.forEachIndexed { i, doc ->
            append("    <item id=\"ch${i + 1}\" href=\"${esc(doc.fileName)}\" media-type=\"application/xhtml+xml\"/>\n")
            doc.answersFileName?.let {
                append("    <item id=\"ans${i + 1}\" href=\"${esc(it)}\" media-type=\"application/xhtml+xml\"/>\n")
            }
        }
        sortedPictures.forEachIndexed { i, name ->
            val id = "img${i + 1}"
            val properties = if (id == coverId) " properties=\"cover-image\"" else ""
            append(
                "    <item id=\"$id\" href=\"${esc(hrefs.getValue(name))}\" " +
                    "media-type=\"${imageMediaType(name)}\"$properties/>\n",
            )
        }
    }
    val spine = buildString {
        append("    <itemref idref=\"cover\"/>\n")
        append("    <itemref idref=\"nav\"/>\n")
        // Each chapter is followed in the spine by its answers, so paging
        // forward reaches them and a link into them lands at the top of a
        // document rather than mid-page.
        docs.forEachIndexed { i, doc ->
            append("    <itemref idref=\"ch${i + 1}\"/>\n")
            if (doc.answersFileName != null) append("    <itemref idref=\"ans${i + 1}\"/>\n")
        }
    }
    return """
<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="bookid">${esc(bookId(course))}</dc:identifier>
    <dc:title>${esc(course.title)}</dc:title>
    <dc:language>${esc(langCode(course.language))}</dc:language>
    <dc:creator>Zhuzha</dc:creator>
    <dc:date>${esc(course.created)}</dc:date>
    <meta property="dcterms:modified">${esc(modified)}</meta>
${coverId?.let { "    <meta name=\"cover\" content=\"$it\"/>\n" } ?: ""}  </metadata>
  <manifest>
$manifest  </manifest>
  <spine toc="ncx">
$spine  </spine>
  <!--
    EPUB 2's <guide>, written for exactly the reason toc.ncx is: it is
    deprecated and it is what an older reader actually looks at to decide
    which document is the cover. Three lines.
  -->
  <guide>
    <reference type="cover" title="${esc(course.title)}" href="cover.xhtml"/>
  </guide>
</package>
""".trimStart()
}

/**
 * A stable identifier for the book. Derived rather than random so exporting
 * the same course twice produces the same id — a reader that has seen this
 * book before should recognise the new file as the same one rather than
 * shelving a duplicate beside it.
 */
private fun bookId(course: Course): String = "urn:inkfirst:${course.slug}:${course.created}"

internal fun imageMediaType(name: String): String =
    when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }
