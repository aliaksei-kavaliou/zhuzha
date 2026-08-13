package com.inkfirst.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutlinePromptTest {

    @Test
    fun `prompt includes topic, minutes, and prior-knowledge`() {
        val prompt = buildOutlinePrompt(
            OutlineRequestInputs(
                topic = "Kotlin for PHP devs",
                dailyMinutes = 45,
                priorKnowledge = "10y PHP, some JS",
                style = StylePresets.HEADFIRST_INSPIRED,
            ),
        )
        assertTrue(prompt.contains("Kotlin for PHP devs"))
        assertTrue(prompt.contains("45"))
        assertTrue(prompt.contains("10y PHP, some JS"))
    }

    @Test
    fun `blank prior-knowledge is rendered as none stated`() {
        val prompt = buildOutlinePrompt(
            OutlineRequestInputs("X", 30, "", StylePresets.TEXTBOOK),
        )
        assertTrue(prompt.contains("(none stated)"))
    }
}
