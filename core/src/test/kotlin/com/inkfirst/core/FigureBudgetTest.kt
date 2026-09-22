package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FigureBudgetTest {

    @Test
    fun `an unknown or missing policy reads as the default, not as unlimited`() {
        // A course.json written before this setting existed has no `figures`
        // field at all, and must keep behaving the way it already did.
        assertEquals("as-needed", figurePolicyFor(null).key)
        assertEquals("as-needed", figurePolicyFor("").key)
        assertEquals("as-needed", figurePolicyFor("lavish").key)
    }

    @Test
    fun `a stored key is matched whatever its case or spacing`() {
        assertEquals("generous", figurePolicyFor(" Generous ").key)
    }

    @Test
    fun `no policy draws without a ceiling`() {
        FIGURE_POLICIES.forEach { policy ->
            assertTrue(policy.maxPerChapter <= 10, "${policy.key} has no usable ceiling")
        }
    }

    @Test
    fun `only the automatic policies spend without being asked`() {
        assertTrue(figurePolicyFor("as-needed").automatic)
        assertTrue(figurePolicyFor("generous").automatic)
        assertFalse(figurePolicyFor("on-request").automatic)
        assertFalse(figurePolicyFor("none").automatic)
    }

    @Test
    fun `a book with no pictures still writes chapters, it just draws nothing`() {
        // "None" is a drawing rule, not a content rule — the prompt tells the
        // model to carry in prose what a picture would have carried.
        val none = figurePolicyFor("none")
        assertEquals(0, none.maxPerChapter)
        assertTrue(none.rule.contains("prose"))
    }

    @Test
    fun `prices are pennies until they are not`() {
        assertEquals("~3¢", pictureCost(1))
        assertEquals("~12¢", pictureCost(4))
        assertEquals("~90¢", pictureCost(30))
        assertEquals("~$1.20", pictureCost(40))
        assertEquals("~0¢", pictureCost(0))
    }

    @Test
    fun `a book's estimate is a range, because the count is the model's call`() {
        val estimate = bookPictureEstimate(chapters = 12, policy = figurePolicyFor("as-needed"))

        assertTrue(estimate.contains("24–72 pictures"), estimate)
        assertTrue(estimate.contains("~72¢"), estimate)
        assertTrue(estimate.contains("~$2.16"), estimate)
    }

    @Test
    fun `an on-request book quotes one picture, not a book's worth`() {
        val estimate = bookPictureEstimate(chapters = 12, policy = figurePolicyFor("on-request"))

        assertTrue(estimate.contains("until you ask"), estimate)
        assertTrue(estimate.contains("~3¢"), estimate)
    }

    @Test
    fun `a book with no pictures is quoted as costing nothing`() {
        assertTrue(bookPictureEstimate(12, figurePolicyFor("none")).contains("nothing to pay"))
    }
}
