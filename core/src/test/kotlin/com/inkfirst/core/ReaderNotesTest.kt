package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The file the reader is allowed to argue with.
 *
 * Everything here is really one property: editing removes what was asked for
 * and what that leaves meaningless, and touches nothing else. The file is
 * hand-editable by design, so a round trip that reformats it is a bug.
 */
class ReaderNotesTest {

    /** Exactly the shape ReaderSignals + CourseStore.appendReaderNotes write. */
    private val sample = """
        ## Chapter 1 — how the reading went
        <!-- What a neural network actually is -->

        Asked 2 questions:
        - On "a gradient is just the slope" — asked: slope of what?
        - Asked: why does it need so many layers?

        Stopped at these passages without asking anything:
        - "the loss surface is not a bowl"

        ## Chapter 2 — how the reading went
        <!-- Training loops -->

        Wrote 1 note:
        - On "one pass over the data" — an epoch, then

    """.trimIndent()

    @Test
    fun `sections carry their chapter and title`() {
        val out = ReaderNotes.parse(sample)
        assertEquals(2, out.size)
        assertEquals(1, out[0].chapterN)
        assertEquals("What a neural network actually is", out[0].title)
        assertEquals(2, out[1].chapterN)
    }

    @Test
    fun `bullets are grouped under the line that leads them in`() {
        val ch1 = ReaderNotes.parse(sample).first()
        assertEquals(2, ch1.groups.size)
        assertEquals("Asked 2 questions:", ch1.groups[0].label)
        assertEquals(2, ch1.groups[0].observations.size)
        assertEquals(
            "On \"a gradient is just the slope\" — asked: slope of what?",
            ch1.groups[0].observations[0].text,
        )
        assertEquals(1, ch1.groups[1].observations.size)
    }

    @Test
    fun `striking one bullet leaves everything else byte for byte`() {
        val target = ReaderNotes.parse(sample).first().groups[0].observations[1]
        val out = ReaderNotes.removeObservations(sample, setOf(target.lineIndex))
        assertTrue("why does it need so many layers?" !in out)
        assertTrue("slope of what?" in out)
        assertTrue("Asked 2 questions:" in out)
        assertTrue("## Chapter 2 — how the reading went" in out)
    }

    @Test
    fun `a lead-in goes with the last bullet under it`() {
        // "Stopped at these passages without asking anything:" followed by
        // nothing is noise, not a record.
        val group = ReaderNotes.parse(sample).first().groups[1]
        val out = ReaderNotes.removeObservations(sample, setOf(group.observations[0].lineIndex))
        assertTrue("Stopped at these passages" !in out)
        assertTrue("Asked 2 questions:" in out)
    }

    @Test
    fun `emptying a chapter takes its heading too`() {
        val ch1 = ReaderNotes.parse(sample).first()
        val out = ReaderNotes.removeObservations(
            sample,
            ch1.observations.map { it.lineIndex }.toSet(),
        )
        assertTrue("## Chapter 1" !in out)
        assertTrue("What a neural network actually is" !in out)
        assertTrue("## Chapter 2" in out)
        assertEquals(1, ReaderNotes.parse(out).size)
    }

    @Test
    fun `crossing out everything leaves nothing, not an empty shell`() {
        val all = ReaderNotes.parse(sample).flatMap { it.observations }.map { it.lineIndex }.toSet()
        assertEquals("", ReaderNotes.removeObservations(sample, all))
    }

    @Test
    fun `a sentence the reader wrote themselves is not a lead-in`() {
        // No bullets under it, so nothing empties it and nothing may remove
        // it. This is the hand-editing promise.
        val md = """
            ## Chapter 3 — how the reading went

            I understood this fine by the end, ignore the questions.
            Asked 1 question:
            - Asked: what is a tensor?
        """.trimIndent()
        val parsed = ReaderNotes.parse(md).first()
        val question = parsed.groups.last().observations.single()
        val out = ReaderNotes.removeObservations(md, setOf(question.lineIndex))
        assertTrue("I understood this fine by the end" in out)
        assertTrue("Asked 1 question:" !in out)
    }

    @Test
    fun `nothing to parse is no sections, not a blank one`() {
        assertEquals(emptyList<ReaderNotes.Section>(), ReaderNotes.parse(""))
        assertEquals(emptyList<ReaderNotes.Section>(), ReaderNotes.parse("   \n\n  "))
    }
}
