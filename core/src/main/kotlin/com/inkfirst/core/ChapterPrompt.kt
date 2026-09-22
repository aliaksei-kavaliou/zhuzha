package com.inkfirst.core

/**
 * Builds the user-turn prompt for a single chapter generation.
 * The style preset's systemPrompt goes through the system instruction channel.
 * The running memory.md is what makes cross-chapter continuity work — see
 * docs/design.md §3.
 *
 * [readerNotes] is reader.md: what the READING looked like, where memory.md
 * is what the book TAUGHT (docs/design.md §13). Blank until a chapter has
 * been read and left some trace, and blank is the normal case for chapter 1.
 *
 * [rewriteFeedback] is non-blank only when this chapter is being written a
 * second time because the first attempt did not land. The old chapter is
 * deliberately NOT included: the point is a fresh attempt at the same
 * objectives, and a model shown its own previous draft edits it instead of
 * rethinking it. Blank feedback is legitimate — it means "just try again",
 * which is a real request of a stochastic model.
 */
internal fun buildChapterPrompt(
    course: Course,
    chapter: PersistedChapter,
    memory: String,
    readerNotes: String = "",
    rewriteFeedback: String = "",
    isRewrite: Boolean = false,
): String = buildString {
    appendLine("Write chapter ${chapter.n} of this course.")
    appendLine()
    appendLine("COURSE CONTEXT")
    appendLine("Topic: ${course.topicPrompt}")
    appendLine("Prior knowledge: ${course.priorKnowledge.ifBlank { "(none stated)" }}")
    appendLine("Learner goal: ${course.expectations.ifBlank { "(none stated)" }}")
    appendLine("Session length: ${course.dailyMinutes} minutes/day")
    appendLine()
    appendLine("FULL OUTLINE")
    course.chapters.forEach { c ->
        appendLine("${c.n}. ${c.title} — ${c.objectives.joinToString("; ")}")
    }
    appendLine()
    appendLine("WHAT WAS TAUGHT SO FAR")
    appendLine(memory.ifBlank { "(nothing yet — this is the first chapter)" })
    appendLine()
    if (readerNotes.isNotBlank()) {
        appendLine("HOW THE READING HAS GONE")
        appendLine(readerNotes.trim())
        appendLine()
    }
    appendLine("CURRENT CHAPTER")
    appendLine("Chapter ${chapter.n}: ${chapter.title}")
    appendLine("Estimated length: ${chapter.estMinutes} minutes of slow reading")
    appendLine("Objectives:")
    chapter.objectives.forEach { appendLine("- $it") }
    appendLine()
    if (isRewrite) {
        appendLine("THIS IS A REWRITE")
        appendLine("A previous version of this chapter was written and the reader rejected it.")
        if (rewriteFeedback.isNotBlank()) {
            appendLine("What they said about it:")
            appendLine(rewriteFeedback.trim())
        } else {
            appendLine("They gave no reason — so the previous attempt simply did not work.")
        }
        appendLine("Write a genuinely different chapter, not a polish of the last one. Same")
        appendLine("objectives, different route to them: change the metaphors, the examples,")
        appendLine("the order things are introduced in. Do not mention that this is a rewrite,")
        appendLine("do not apologise, and do not refer to a previous version — as far as the")
        appendLine("reader is concerned this is simply the chapter.")
        appendLine()
    }
    appendLine("FORMAT RULES")
    appendLine("- Return Markdown. Do NOT wrap the whole output in a code fence.")
    appendLine("- Open with the chapter title as a level-1 heading — a single \"#\". Do not put \"Chapter ${chapter.n}\" in it; the app prints the number itself. Use \"##\" and below for sections within the chapter.")
    appendLine("- ${measurementRule(course.units)}")
    appendLine("- Write the entire chapter in ${course.language}. All headings, prose, exercise text, recap text, and figure alt-text must be in ${course.language}. Technical terms may stay in English if that's how the field uses them.")
    appendLine("- Aim for content that fills ~${chapter.estMinutes} minutes of slow, e-ink reading. Redundancy is welcome if it's pedagogical.")
    appendLine("- Use custom fenced blocks where they fit:")
    appendLine("  - ```exercise … ``` for practice; put the answer inside on a line starting with \"answer: \".")
    appendLine("  - ```recap … ``` for end-of-section wrap-ups that restate the key idea differently.")
    if (course.figurePolicy.maxPerChapter == 0) {
        appendLine("  - ${course.figurePolicy.rule}")
    } else {
    appendLine("  - ```figure alt=\"…\" ``` for figure slots. The alt text is the drawing brief: an artist reads it and nothing else. Every figure is a loose hand-drawn ink sketch — never ask for a chart, a graph, a screenshot or a labelled schematic. There are TWO kinds, and choosing the right one matters more than the wording:")
    appendLine("    - DEFAULT (no kind param) — a picture that makes the page feel alive. Describe a concrete SCENE with one vivid subject. \"A very tired robot trying to sort a mountain of socks\" works. Words in the picture are welcome but must be few, short and large — a speech bubble of three or four short everyday words (a character reacting is very welcome) or a single big label. It should raise a smile as well as make the point.")
    appendLine("    - ```figure kind=\"show\" alt=\"…\" ``` — use this when the reader has to SEE the thing to do it, and words alone would leave them guessing: a hand position, how a tool is held, how something is physically arranged, what a described object looks like. Here describe the THING ITSELF and the relationship that matters — \"a hand holding a pencil well back from the tip, resting on the middle joint of the middle finger, thumb and index finger holding it lightly, seen close up from the side\" — not a scene, not a character, no joke. Say which parts touch and from what angle it should be seen. A concrete object standing in for an abstract idea belongs here too: if the chapter says a value is \"like a knob you turn\", a picture of that knob being turned is a show figure.")
    appendLine("    Use whichever fits each moment; a chapter can mix them freely.")
    // What the panel can physically carry, said to the model that writes the
    // brief rather than only to the one that draws it. Until this line
    // existed the medium constraint reached the illustrator and stopped
    // there, so a cooking chapter could brief "the finished risotto, creamy
    // and golden" — impossible on a small grey screen, and the same defect
    // as the capybara: not the image model's fault, the brief could not be
    // filled.
    appendLine("    WHAT A PICTURE HERE CAN CARRY: this book is read on a small greyscale e-ink screen, and every picture is black ink lines on white. A picture can show shape, arrangement, position, sequence, and texture drawn as hatching. It CANNOT show colour, sheen, gloss, browning, or photographic detail. Never write a brief whose point depends on one of those — the golden crust of a dish, the exact shade something turns. Put those in the PROSE, where the reader can actually read them (\"deep amber, not pale straw\"), and let the picture show the form, the step, or the arrangement instead.")
    appendLine("    ${course.figurePolicy.rule}")
    }
    appendLine("- Where a metaphor or term from an earlier chapter helps, reuse it explicitly.")
    if (readerNotes.isNotBlank()) {
        // Bounding this is the whole design. An unbounded "adapt to the
        // learner" instruction produces a chapter built around a perceived
        // weakness, and the reader has no way to say "I understood that
        // weeks ago" — it gets patronising fast, and it is the point where
        // this stops feeling like teaching and starts feeling like being
        // watched. See docs/design.md §13.
        appendLine("- HOW THE READING HAS GONE is a record of what this reader asked about, not a grade and not a judgement of their ability. Use it ONLY to decide which earlier ideas are worth restating briefly in passing, and — if something was asked about more than once — to open with a short recap of it, a few sentences at most.")
        appendLine("- Do NOT restructure the chapter around it, do not add extra exercises because of it, do not simplify the chapter because of it, and never phrase anything as a correction of the reader or a comment on what they found hard.")
        appendLine("- If you do open with such a recap, say in one short sentence that it is there because of what they asked about. A book that quietly rewrites itself around someone is worse than one that says what it is doing.")
    }
    appendLine("- Do NOT restate the outline or objectives — just write the chapter.")
    appendLine("- Punctuation: put a space on each side of every em-dash. Write \"word — word\", not \"word—word\" or \"word---word\". This reads more comfortably on narrow e-ink pages.")
}

/**
 * Builds the user-turn prompt for the post-chapter memory summary. The output
 * gets appended to memory.md and is what future chapters see as WHAT WAS TAUGHT.
 */
internal fun buildMemoryUpdatePrompt(
    chapter: PersistedChapter,
    chapterMarkdown: String,
    learnerGoal: String,
): String = buildString {
    appendLine("Summarize chapter ${chapter.n} for the running course memory.")
    appendLine()
    appendLine("Chapter title: ${chapter.title}")
    appendLine("Learner goal: ${learnerGoal.ifBlank { "(none stated)" }}")
    appendLine()
    appendLine("CHAPTER MARKDOWN")
    appendLine(chapterMarkdown)
    appendLine()
    appendLine("Write ~200 words, prose, no headings, capturing:")
    appendLine("- Concepts introduced (name them).")
    appendLine("- Metaphors or analogies used — be specific (\"gradient descent as walking down a hill\", not \"a physical metaphor\").")
    appendLine("- Terminology choices (if we picked one word over another, note it).")
    appendLine("- Exercises given (topic only, not the full text).")
    appendLine()
    appendLine("Future chapters will read this. Do not include a heading — just the paragraph(s).")
}
