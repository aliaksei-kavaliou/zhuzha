package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

private const val WHEN = "2026-09-24T10:00:00Z"

class EpubExportTest {

    private val outline = CourseOutline(
        title = "How Bread Rises",
        chapters = listOf(
            ChapterSpec(1, "01-yeast", "What Yeast Is Doing", listOf("name the gas"), 30),
            ChapterSpec(2, "02-gluten", "Gluten & Structure", listOf("explain the web"), 30),
        ),
    )

    private fun store(tmp: File): CourseStore = CourseStore(tmp).also {
        it.create(
            outline = outline,
            proposedSlug = "bread",
            topicPrompt = "bread",
            priorKnowledge = "",
            dailyMinutes = 30,
            style = "headfirst-inspired",
            model = "gemini-3.7-flash",
            created = "2026-09-01",
        )
    }

    private val chapterOne = """
        # Chapter 1: What Yeast Is Doing

        Yeast eats sugar and breathes out gas. That gas is what lifts a loaf.

        - it is alive
        - it is hungry

        ---

        ## The gas

        Warm dough works faster than cold dough.

        ```figure alt="a yeast cell blowing up a balloon" src="01-yeast-1.jpg"
        ```

        ```exercise
        Why does a cold kitchen slow a loaf down?
        answer: The yeast is less active when cold, so it makes gas more slowly.
        ```

        ```recap
        Yeast makes gas. Gas makes holes.
        ```
    """.trimIndent()

    /** Unzips to a name → bytes map, preserving entry order in a second list. */
    private fun unzip(bytes: ByteArray): Pair<Map<String, ByteArray>, List<ZipEntry>> {
        val files = linkedMapOf<String, ByteArray>()
        val entries = mutableListOf<ZipEntry>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                entries += e
                files[e.name] = zin.readBytes()
            }
        }
        return files to entries
    }

    private fun exportOf(
        tmp: File,
        options: EpubOptions = EpubOptions(),
        prepare: (CourseStore) -> Unit = {},
    ): Pair<Map<String, ByteArray>, List<ZipEntry>> {
        val s = store(tmp)
        s.saveChapter("bread", "01-yeast", chapterOne)
        s.saveChapter("bread", "02-gluten", "# Gluten & Structure\n\nProtein makes a web.")
        prepare(s)
        val out = ByteArrayOutputStream()
        EpubExport(s).write("bread", out, options, modified = WHEN)
        return unzip(out.toByteArray())
    }

    private fun text(files: Map<String, ByteArray>, name: String): String =
        String(files.getValue(name), Charsets.UTF_8)

    /** Parses as XML — the check that matters, since XHTML is XML, not HTML. */
    private fun parseXml(bytes: ByteArray) {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        // The doctype references no external DTD we want fetched during a test.
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        f.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    // --- the shape of the container ----------------------------------------

    /**
     * The single most common way a hand-built EPUB fails to open: the mimetype
     * entry must come FIRST and must be stored uncompressed. A reader that
     * cannot find it there rejects the file with no useful error.
     */
    @Test
    fun `mimetype is the first entry and is stored uncompressed`(@TempDir tmp: File) {
        val (files, entries) = exportOf(tmp)

        assertEquals("mimetype", entries.first().name)
        assertEquals(ZipEntry.STORED, entries.first().method)
        assertEquals("application/epub+zip", text(files, "mimetype"))
    }

    @Test
    fun `the container points at a manifest that is actually there`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp)

        assertTrue(text(files, "META-INF/container.xml").contains("OEBPS/content.opf"))
        assertTrue(files.containsKey("OEBPS/content.opf"))
        assertTrue(files.containsKey("OEBPS/nav.xhtml"))
        assertTrue(files.containsKey("OEBPS/toc.ncx"))
    }

    /**
     * Every document in the book has to be well-formed XML. This is the test
     * that would have caught an unescaped ampersand — which is not exotic
     * input: "Gluten & Structure" is a chapter title in this fixture for
     * exactly that reason.
     */
    @Test
    fun `every document in the book parses as XML`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp, EpubOptions(includeAnnotations = true)) { s ->
            s.addAnnotation(
                "bread", "01-yeast", 3,
                quote = "a < b & c", note = "\"quotes\" & angles <here>",
                created = "2026-09-10", kind = "text", at = "2026-09-10T20:00:00",
            )
        }

        files.filterKeys { it.endsWith(".xhtml") || it.endsWith(".opf") || it.endsWith(".ncx") || it.endsWith(".xml") }
            .forEach { (name, bytes) ->
                try {
                    parseXml(bytes)
                } catch (e: Exception) {
                    throw AssertionError("$name is not well-formed XML: ${e.message}")
                }
            }
    }

    @Test
    fun `every spine and manifest href resolves to a file in the zip`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp) { s ->
            File(tmp, "bread/figures").mkdirs()
            File(tmp, "bread/figures/01-yeast-1.jpg").writeBytes(byteArrayOf(1, 2, 3))
        }
        val opf = text(files, "OEBPS/content.opf")

        val hrefs = Regex("""href="([^"]+)"""").findAll(opf).map { it.groupValues[1] }.toList()
        assertTrue(hrefs.isNotEmpty())
        hrefs.forEach {
            assertTrue(files.containsKey("OEBPS/$it"), "manifest names $it, which is not in the file")
        }
    }

    // --- what the book contains --------------------------------------------

    @Test
    fun `a chapter that opens with its own title is not titled twice`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp)
        val ch = text(files, "OEBPS/ch01.xhtml")

        assertEquals(1, Regex("<h1>").findAll(ch).count())
        // And the model's "Chapter 1:" prefix does not survive into the title.
        assertTrue(ch.contains("<h1>What Yeast Is Doing</h1>"))
    }

    @Test
    fun `list items are gathered into one list rather than left bare`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp)
        val ch = text(files, "OEBPS/ch01.xhtml")

        assertEquals(1, Regex("<ul>").findAll(ch).count())
        assertEquals(2, Regex("<li>").findAll(ch).count())
    }

    /**
     * The decision from the design discussion: an EPUB reflows, so "put the
     * answer on the next page" is not available. It goes to the back of the
     * chapter, the way a printed textbook does it, linked both ways — which is
     * the one thing paper cannot do.
     */
    @Test
    fun `an exercise answer moves to the back of the chapter and links both ways`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp)
        val ch = text(files, "OEBPS/ch01.xhtml")

        val exerciseAt = ch.indexOf("id=\"c1-ex1\"")
        val answerAt = ch.indexOf("id=\"c1-ans1\"")
        assertTrue(exerciseAt in 1 until answerAt, "the answer must come after its exercise")
        assertTrue(ch.contains("href=\"#c1-ans1\""), "the exercise must link forward to its answer")
        assertTrue(ch.contains("href=\"#c1-ex1\""), "the answer must link back to its exercise")
        // And the answer text is not sitting in the exercise itself.
        assertFalse(ch.substring(exerciseAt, answerAt).contains("less active when cold"))
    }

    @Test
    fun `a figure whose picture was never drawn falls back to its alt text`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp) // no figure file written
        val ch = text(files, "OEBPS/ch01.xhtml")

        assertFalse(ch.contains("<img"))
        assertTrue(ch.contains("a yeast cell blowing up a balloon"))
    }

    @Test
    fun `a drawn figure is embedded and manifested with its real media type`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp) { _ ->
            File(tmp, "bread/figures").mkdirs()
            File(tmp, "bread/figures/01-yeast-1.jpg").writeBytes(byteArrayOf(9, 9, 9))
        }

        assertTrue(files.containsKey("OEBPS/images/01-yeast-1.jpg"))
        assertTrue(text(files, "OEBPS/content.opf").contains("media-type=\"image/jpeg\""))
        assertTrue(text(files, "OEBPS/ch01.xhtml").contains("src=\"images/01-yeast-1.jpg\""))
    }

    /**
     * Only pictures a chapter actually uses. A course accumulates figures
     * across rewrites, and shipping the orphans would quietly inflate the file
     * with pictures no page references.
     */
    @Test
    fun `a figure file nothing references is not shipped`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp) { _ ->
            File(tmp, "bread/figures").mkdirs()
            File(tmp, "bread/figures/01-yeast-1.jpg").writeBytes(byteArrayOf(1))
            File(tmp, "bread/figures/orphan-from-a-rewrite.jpg").writeBytes(byteArrayOf(1))
        }

        assertTrue(files.containsKey("OEBPS/images/01-yeast-1.jpg"))
        assertFalse(files.containsKey("OEBPS/images/orphan-from-a-rewrite.jpg"))
    }

    // --- the annotations toggle ---------------------------------------------

    private fun withReaderContent(s: CourseStore) {
        s.addAnnotation(
            "bread", "01-yeast", 3,
            quote = "That gas is what lifts a loaf", note = "so it is CO2 then",
            created = "2026-09-10", kind = "text", at = "2026-09-10T20:00:00",
        )
        s.appendQa(
            courseSlug = "bread",
            chapterSlug = "01-yeast",
            selection = "Yeast eats sugar",
            question = "What kind of sugar?",
            answer = "The flour's own starch, broken down by enzymes.",
            created = "2026-09-10",
        )
    }

    /**
     * The default, and the reason for it: an export is irreversible. A file
     * handed to someone carries whatever is in it, and a reader's questions
     * are the most private thing this app stores.
     */
    @Test
    fun `annotations are left out unless asked for`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp, EpubOptions(), ::withReaderContent)
        val ch = text(files, "OEBPS/ch01.xhtml")

        assertFalse(ch.contains("so it is CO2 then"))
        assertFalse(ch.contains("What kind of sugar?"))
        // The book itself is all there.
        assertTrue(ch.contains("Yeast eats sugar and breathes out gas"))
    }

    @Test
    fun `annotations are carried when asked for, and the copy says so`(@TempDir tmp: File) {
        val (files, _) = exportOf(
            tmp, EpubOptions(includeAnnotations = true), ::withReaderContent,
        )
        val ch = text(files, "OEBPS/ch01.xhtml")

        assertTrue(ch.contains("That gas is what lifts a loaf"))
        assertTrue(ch.contains("so it is CO2 then"))
        assertTrue(ch.contains("What kind of sugar?"))
        assertTrue(ch.contains("broken down by enzymes"))
        // Whoever opens this next may not be the person who chose to include them.
        assertTrue(text(files, "OEBPS/title.xhtml").contains("includes your notes"))
    }

    /**
     * An "ask" annotation is the mark that puts a ? in the margin; it carries
     * no note text. Exporting it as a note would print an empty note under a
     * quote — the same defect the Contents screen was fixed for on 2026-09-21.
     */
    @Test
    fun `a bare ask mark is not exported as an empty note`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp, EpubOptions(includeAnnotations = true)) { s ->
            s.addAnnotation(
                "bread", "01-yeast", 3,
                quote = "Warm dough works faster", note = "",
                created = "2026-09-10", kind = "ask", at = "2026-09-10T20:00:00",
            )
        }

        assertFalse(text(files, "OEBPS/ch01.xhtml").contains("<section class=\"reader\">"))
    }

    // --- edges --------------------------------------------------------------

    @Test
    fun `a chapter that was never generated is skipped, not exported empty`(@TempDir tmp: File) {
        val s = store(tmp)
        s.saveChapter("bread", "01-yeast", chapterOne)
        val out = ByteArrayOutputStream()
        val result = EpubExport(s).write("bread", out, modified = WHEN)

        assertEquals(1, result.chapters)
        val (files, _) = unzip(out.toByteArray())
        assertTrue(files.containsKey("OEBPS/ch01.xhtml"))
        assertFalse(files.containsKey("OEBPS/ch02.xhtml"))
    }

    @Test
    fun `a book with nothing written yet refuses rather than making an empty file`(@TempDir tmp: File) {
        val s = store(tmp)
        assertThrows(IllegalArgumentException::class.java) {
            EpubExport(s).write("bread", ByteArrayOutputStream(), modified = WHEN)
        }
    }

    /**
     * The id is derived, not random, so re-exporting a book produces the same
     * identifier — a reader that has seen it before recognises the new file as
     * the same book instead of shelving a duplicate beside it.
     */
    @Test
    fun `the book id is stable across exports`(@TempDir tmp: File) {
        val (a, _) = exportOf(tmp)
        val second = File(tmp.parentFile, tmp.name + "-2").apply { mkdirs() }
        val (b, _) = exportOf(second)

        val id = Regex("""<dc:identifier[^>]*>([^<]+)""")
        assertEquals(
            id.find(text(a, "OEBPS/content.opf"))!!.groupValues[1],
            id.find(text(b, "OEBPS/content.opf"))!!.groupValues[1],
        )
    }

    @Test
    fun `an anchor is unique across the whole book, not just its own file`(@TempDir tmp: File) {
        // Every chapter restarts the exercise counter, so an un-namespaced
        // `ex1` exists once per chapter. Per spec each XHTML file is its own
        // document and that resolves correctly — but a reader that flattens
        // the spine into one document resolves the fragment globally, and
        // then every "show answer" in the book jumps to chapter one.
        val (files, _) = exportOf(tmp)
        val ids = files.keys
            .filter { it.endsWith(".xhtml") }
            .flatMap { name ->
                Regex("""id="([^"]+)"""").findAll(text(files, name)).map { m -> m.groupValues[1] }
            }

        assertEquals(ids.size, ids.toSet().size, "an id appears in more than one file: $ids")
    }

    @Test
    fun `the answers start on their own page`(@TempDir tmp: File) {
        // Without the break the section follows the last exercise after a
        // paragraph or two, so jumping to answer 1 lands on a page whose
        // visible top is a DIFFERENT exercise's question. Reported from a
        // real reader as the link going to the wrong place — and it was not:
        // the anchor was right and the landing was unreadable.
        val (files, _) = exportOf(tmp)

        assertTrue(
            text(files, "OEBPS/style.css").contains("section.answers { page-break-before: always;"),
        )
    }

    @Test
    fun `an answer reprints the question it belongs to`(@TempDir tmp: File) {
        // The reader arrived from another page and cannot see what was asked,
        // so a bare number does not tell them this is the answer they wanted.
        val (files, _) = exportOf(tmp)
        val ch = text(files, "OEBPS/ch01.xhtml")
        val answers = ch.substring(ch.indexOf("<section class=\"answers\""))

        assertTrue(answers.contains("class=\"answer-q\""), answers)
        assertTrue(answers.contains("cold kitchen"), "the question itself must be reprinted")
    }

    @Test
    fun `a recap and an exercise say which they are`(@TempDir tmp: File) {
        // They were distinguished only by a CSS border style, which most
        // readers render alike and neither of which names the block.
        val (files, _) = exportOf(tmp)
        val ch = text(files, "OEBPS/ch01.xhtml")

        assertTrue(ch.contains("<p class=\"block-label\">Exercise 1</p>"), ch)
        assertTrue(ch.contains("<p class=\"block-label\">Recap</p>"), ch)
    }

}

class EpubDetailTest {

    @Test
    fun `an unknown language name becomes a valid code rather than being passed through`() {
        assertEquals("ru", langCode("Russian"))
        assertEquals("be", langCode("Belarusian"))
        // A xml:lang of "Klingon" is invalid and some readers reject the file.
        assertEquals("en", langCode("Klingon"))
        assertEquals("en", langCode(""))
    }

    @Test
    fun `media type follows the extension, not a guess`() {
        assertEquals("image/png", imageMediaType("a.png"))
        assertEquals("image/jpeg", imageMediaType("a.jpg"))
        assertEquals("image/jpeg", imageMediaType("a"))
    }

    @Test
    fun `the exercise splitter finds an answer wherever it sits`() {
        val (q, a) = splitExerciseBody("Do the thing.\nanswer: Like this.\nAnd also this.")
        assertEquals("Do the thing.", q)
        assertEquals("Like this.\nAnd also this.", a)

        val (q2, a2) = splitExerciseBody("Just a question.")
        assertEquals("Just a question.", q2)
        assertEquals("", a2)
    }

    @Test
    fun `a chapter with no heading of its own still gets a title`() {
        assertNotNull(openingTitleHeading(parseMarkdown("## A Title\n\nText.")))
        // Level 3 is a section heading — a chapter opening into one has no title.
        assertEquals(null, openingTitleHeading(parseMarkdown("### A Section\n\nText.")))
        assertEquals(null, openingTitleHeading(parseMarkdown("Just prose.")))
    }
}
