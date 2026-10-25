package com.inkfirst.core

/**
 * Arithmetic a chapter states about itself, checked.
 *
 * A real chapter said: "the gap between the guess (400) and the truth (150) —
 * which is 255 grams". It is 250. Nothing downstream looks at a number, so a
 * slip like that reaches the reader inside an ANSWER, where it is worst: the
 * reader does the sum, gets a different figure, and has no way to tell which
 * of them is wrong. The fix belongs upstream: repair how the model WRITES the
 * sum, not the result after the fact.
 *
 * Two halves, the pattern this file has used before. The chapter prompt asks
 * for the operation to be WRITTEN OUT ("400 − 150 = 250") rather than stated
 * as a bare result, because an explicit sum is one a model is far less likely
 * to get wrong and — the point of this file — one that can be CHECKED without
 * understanding the sentence around it.
 *
 * It only ever reports. Correcting the text would be easy for the operands
 * given, and wrong: a study book legitimately prints arithmetic that is
 * WRONG ON PURPOSE — "the network guessed 2 + 2 = 5, and the loss function
 * measures how badly" — and no rule here can tell that from a slip. The
 * author decides; this finds the candidates.
 */
object ArithmeticCheck {

    /** A stated sum that does not add up. */
    data class Mismatch(
        val statement: String,
        val stated: Double,
        val correct: Double,
    ) {
        override fun toString(): String = "$statement (should be ${format(correct)})"
    }

    /**
     * Every `A op B = C` in [text] whose C is wrong.
     *
     * Deliberately narrow. It reads only fully explicit arithmetic — both
     * operands, the operator and the result, all written down — because that
     * is the form the prompt asks for and the only one whose intent is
     * unambiguous. Prose like "a 255-gram gap" is not arithmetic to this
     * function, and should not be: there is no operation in it to check.
     *
     * Code fences are skipped. A listing is not a claim about the world, and
     * `x = a - b` inside one is a program rather than a sum.
     */
    fun check(text: String): List<Mismatch> {
        val out = mutableListOf<Mismatch>()
        for (line in outsideCodeFences(text)) {
            for (m in STATEMENT.findAll(line)) {
                val a = number(m.groupValues[1]) ?: continue
                val b = number(m.groupValues[3]) ?: continue
                val stated = number(m.groupValues[4]) ?: continue
                val correct = when (m.groupValues[2]) {
                    "+" -> a + b
                    "-", "−", "–", "—" -> a - b
                    "*", "×", "x", "X" -> a * b
                    "/", "÷" -> if (b == 0.0) continue else a / b
                    else -> continue
                }
                // A tolerance, not an equality: the text may legitimately
                // round ("0.333" for a third), and a rounding is not an
                // error. Half a unit of the last digit shown is generous
                // enough for that and far tighter than any real slip.
                if (kotlin.math.abs(stated - correct) > tolerance(m.groupValues[4])) {
                    out += Mismatch(m.value.trim(), stated, correct)
                }
            }
        }
        return out
    }

    private fun outsideCodeFences(text: String): List<String> {
        var inFence = false
        val kept = mutableListOf<String>()
        for (line in text.lines()) {
            if (line.trimStart().startsWith("```")) {
                // An exercise or recap fence is prose and is checked; a code
                // fence is not. The kind sits right after the backticks.
                val kind = line.trimStart().removePrefix("```").trim().substringBefore(' ')
                if (!inFence) inFence = kind.isNotEmpty() && badgeKindFor(kind) == "code"
                else inFence = false
                continue
            }
            if (!inFence) kept += line
        }
        return kept
    }

    /** Half a unit of the least significant digit the result was written to. */
    private fun tolerance(stated: String): Double {
        val decimals = stated.substringAfter('.', "").count { it.isDigit() }
        return 0.5 * Math.pow(10.0, -decimals.toDouble()) + 1e-9
    }

    private fun number(raw: String): Double? =
        raw.replace(",", "").replace(" ", "").replace(" ", "").toDoubleOrNull()

    private fun format(v: Double): String =
        if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()

    /**
     * `1,250 − 300 = 950`, in any of the dashes and times signs a model
     * writes, with optional thousands separators and decimals.
     */
    private val STATEMENT = Regex(
        """(\d[\d,  ]*(?:\.\d+)?)\s*([+\-−–—*×xX/÷])\s*""" +
            """(\d[\d,  ]*(?:\.\d+)?)\s*=\s*(\d[\d,  ]*(?:\.\d+)?)""",
    )
}
