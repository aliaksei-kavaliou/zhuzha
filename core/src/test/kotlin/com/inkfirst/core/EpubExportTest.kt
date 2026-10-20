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

        val ans = text(files, "OEBPS/ch01-answers.xhtml")

        assertTrue(ch.contains("id=\"c1-ex1\""))
        assertTrue(ans.contains("id=\"c1-ans1\""))
        assertTrue(
            ch.contains("href=\"ch01-answers.xhtml#c1-ans1\""),
            "the exercise must link forward into the answers document",
        )
        assertTrue(
            ans.contains("href=\"ch01.xhtml#c1-ex1\""),
            "the answer must link back into the chapter",
        )
        // And the answer text is nowhere in the chapter itself.
        assertFalse(ch.contains("less active when cold"), "the answer leaked into the chapter")
        assertTrue(ans.contains("less active when cold"))
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
     * The alt text is the drawing BRIEF — the words the illustrator was
     * handed. It belongs in alt=, which a reader only meets when the picture
     * cannot be shown; printed as a caption it shows every reader the prompt.
     * Reported from PocketBook and NeoReader, 2026-10-16.
     */
    @Test
    fun `a drawn figure carries its brief in alt, never as a caption`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp) { _ ->
            File(tmp, "bread/figures").mkdirs()
            File(tmp, "bread/figures/01-yeast-1.jpg").writeBytes(byteArrayOf(9, 9, 9))
        }
        val ch = text(files, "OEBPS/ch01.xhtml")

        assertFalse(ch.contains("<figcaption"))
        assertTrue(ch.contains("alt=\"a yeast cell blowing up a balloon\""))
        // And the brief appears exactly once — in the attribute.
        assertEquals(1, ch.split("a yeast cell blowing up a balloon").size - 1)
    }

    /**
     * A reflowable reader breaks a long block across pages, and CSS paints a
     * SIDE border on every fragment — so a left rule runs to the foot of one
     * page and starts hard against the top of the next. A top or bottom
     * border is painted only at the box's real edges. Reported from
     * PocketBook, 2026-10-16: "the vertical line ... goes to the page end and
     * started from very top, that doesn't look really native".
     */
    @Test
    fun `no block is marked with a side border`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp)
        val css = text(files, "OEBPS/style.css")

        assertFalse(css.contains("border-left"), css)
        assertFalse(css.contains("border-right"), css)
    }

    /** What marks a block instead: a label band at the top, a rule at the end. */
    @Test
    fun `an exercise and a recap each close with a rule`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp)
        val ch = text(files, "OEBPS/ch01.xhtml")

        // One per block, and inside the block rather than after it.
        assertEquals(2, ch.split("class=\"block-end\"").size - 1)
        val exercise = ch.substringAfter("<aside class=\"exercise\"").substringBefore("</aside>")
        val recap = ch.substringAfter("<aside class=\"recap\"").substringBefore("</aside>")
        assertTrue(exercise.contains("class=\"block-end\""), exercise)
        assertTrue(recap.contains("class=\"block-end\""), recap)
        // The exercise's rule closes the block, so it follows the answer link.
        assertTrue(
            exercise.indexOf("class=\"block-end\"") > exercise.indexOf("class=\"answer-link\""),
            exercise,
        )
    }

    /**
     * Without this the OPF never names a cover, so a reader shows its own
     * placeholder. Reported 2026-10-20 after importing an export: "it has
     * reader random default cover".
     *
     * Declared twice deliberately — `properties="cover-image"` is EPUB 3,
     * `<meta name="cover">` is EPUB 2 and is what several readers actually
     * look for.
     */
    @Test
    fun `the first chapter's opening picture is declared as the book cover`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp) { s ->
            File(tmp, "bread/figures").mkdirs()
            File(tmp, "bread/figures/01-yeast-cover.jpg").writeBytes(byteArrayOf(7, 7, 7))
            File(tmp, "bread/figures/01-yeast-1.jpg").writeBytes(byteArrayOf(9, 9, 9))
        }
        val opf = text(files, "OEBPS/content.opf")

        val coverItem = Regex("""<item id="(\w+)" href="images/01-yeast-cover\.jpg"[^>]*>""")
            .find(opf)
        assertNotNull(coverItem, opf)
        val id = coverItem!!.groupValues[1]
        assertTrue(coverItem.value.contains("""properties="cover-image""""), coverItem.value)
        assertTrue(opf.contains("""<meta name="cover" content="$id"/>"""), opf)
    }

    /** A book whose first chapter has no picture must still produce valid XML. */
    @Test
    fun `no cover is declared when there is no opening picture`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp)
        val opf = text(files, "OEBPS/content.opf")

        assertFalse(opf.contains("cover-image"), opf)
        assertFalse(opf.contains("""<meta name="cover""""), opf)
    }

    /** The whole book, not just the chapter that happens to be tested. */
    @Test
    fun `no document in the book prints a caption`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp) { _ ->
            File(tmp, "bread/figures").mkdirs()
            File(tmp, "bread/figures/01-yeast-1.jpg").writeBytes(byteArrayOf(9, 9, 9))
            File(tmp, "bread/figures/01-yeast-cover.jpg").writeBytes(byteArrayOf(8, 8, 8))
        }

        files.keys.filter { it.endsWith(".xhtml") || it.endsWith(".css") }.forEach { name ->
            assertFalse(text(files, name).contains("figcaption"), name)
        }
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
    fun `the answers are their own document in the spine`(@TempDir tmp: File) {
        // A CSS page break was the first attempt and it did not survive
        // contact with a real reader: PocketBook renders in its own typeface
        // once the font is changed and need not apply the publisher's
        // stylesheet at all, so the jump still landed mid-chapter. A separate
        // file is a page boundary nothing can ignore.
        val (files, _) = exportOf(tmp)
        val opf = text(files, "OEBPS/content.opf")

        assertTrue(files.containsKey("OEBPS/ch01-answers.xhtml"))
        assertTrue(opf.contains("""<item id="ans1" href="ch01-answers.xhtml""""), opf)
        // And it is reachable by paging, directly after its own chapter.
        val spine = Regex("""idref="([^"]+)"""").findAll(opf).map { it.groupValues[1] }.toList()
        assertEquals(listOf("title", "nav", "ch1", "ans1", "ch2"), spine)
    }

    @Test
    fun `a chapter with no exercises gets no answers document`(@TempDir tmp: File) {
        val (files, _) = exportOf(tmp)

        assertFalse(files.containsKey("OEBPS/ch02-answers.xhtml"))
    }

    @Test
    fun `an answer is its number and the answer, nothing else`(@TempDir tmp: File) {
        // Reprinting the question above each answer was tried and read as
        // bloat: the landing is already a page headed "Answers" with the
        // numbered answer at the top and a link back to the question, so the
        // reprint was solving a problem the separate document had solved.
        val (files, _) = exportOf(tmp)
        val answers = text(files, "OEBPS/ch01-answers.xhtml")

        assertFalse(answers.contains("cold kitchen"), "the question was reprinted")
        assertTrue(answers.contains("less active when cold"))
        assertTrue(answers.contains("""<a href="ch01.xhtml#c1-ex1">"""), "back-link is the tie")
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
