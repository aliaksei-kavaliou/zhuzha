package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArithmeticCheckTest {

    @Test
    fun `the apple answer that started this`() {
        val found = ArithmeticCheck.check(
            "answer: The loss function takes the gap between the guess and the truth " +
                "— 400 - 150 = 255 grams — and turns it into an error score.",
        )
        assertEquals(1, found.size)
        assertEquals(250.0, found[0].correct)
    }

    @Test
    fun `sums that are right are not reported`() {
        assertTrue(
            ArithmeticCheck.check(
                "500,000 - 50,000 = 450,000 euros, and 2 × 3 = 6, and 10 / 4 = 2.5.",
            ).isEmpty(),
        )
    }

    @Test
    fun `every operator and every dash a model writes`() {
        for (dash in listOf("-", "−", "–", "—")) {
            assertEquals(1, ArithmeticCheck.check("9 $dash 3 = 7").size, "dash $dash")
        }
        for (times in listOf("*", "×", "x")) {
            assertEquals(1, ArithmeticCheck.check("4 $times 4 = 15").size, "times $times")
        }
        assertEquals(1, ArithmeticCheck.check("9 + 3 = 11").size)
        assertEquals(1, ArithmeticCheck.check("9 / 3 = 4").size)
    }

    @Test
    fun `rounding is not an error`() {
        // A third written to three places is right, not wrong.
        assertTrue(ArithmeticCheck.check("1 / 3 = 0.333").isEmpty())
        // But a figure that is off by more than its own last digit is.
        assertEquals(1, ArithmeticCheck.check("1 / 3 = 0.5").size)
    }

    @Test
    fun `a code listing is a program, not a claim`() {
        assertTrue(
            ArithmeticCheck.check(
                """
                Here is the loop:

                ```python
                total = 5 - 2 = 9
                ```
                """.trimIndent(),
            ).isEmpty(),
        )
    }

    @Test
    fun `an exercise fence is prose and is checked`() {
        val found = ArithmeticCheck.check(
            """
            ```exercise
            The guess was 400 and the truth 150.

            answer: 400 - 150 = 255 grams.
            ```
            """.trimIndent(),
        )
        assertEquals(1, found.size)
    }

    @Test
    fun `prose with no operation in it is not arithmetic`() {
        // There is nothing to check in "a 255-gram gap" — no operands, no
        // operator. Reporting it would be guessing at the sentence's meaning.
        assertTrue(ArithmeticCheck.check("The gap of 255 grams is large.").isEmpty())
    }

    @Test
    fun `division by zero is left alone`() {
        assertTrue(ArithmeticCheck.check("5 / 0 = 0").isEmpty())
    }
}
