package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutlinePromptTest {

    @Test
    fun `prompt includes topic, minutes, prior knowledge, and expectations`() {
        val prompt = buildOutlinePrompt(
            OutlineRequestInputs(
                topic = "Kotlin for PHP devs",
                dailyMinutes = 45,
                priorKnowledge = "10y PHP, some JS",
                expectations = "review PRs confidently in a month",
                style = StylePresets.HEADFIRST_INSPIRED,
            ),
        )
        assertTrue(prompt.contains("Kotlin for PHP devs"))
        assertTrue(prompt.contains("45"))
        assertTrue(prompt.contains("10y PHP, some JS"))
        assertTrue(prompt.contains("review PRs confidently in a month"))
    }

    @Test
    fun `blank prior-knowledge and expectations render as none stated`() {
        val prompt = buildOutlinePrompt(
            OutlineRequestInputs("X", 30, "", "", StylePresets.TEXTBOOK),
        )
        // Both optional inputs share the same fallback string.
        assertTrue(prompt.split("(none stated)").size - 1 == 2)
    }

    @Test
    fun `prompt tells the model which language to write outline in`() {
        val prompt = buildOutlinePrompt(
            OutlineRequestInputs("X", 30, "", "", StylePresets.TEXTBOOK, language = "Belarusian"),
        )
        assertTrue(prompt.contains("in Belarusian"))
    }
}
