package com.inkfirst.core

/**
 * Prompt construction for chapter illustrations.
 *
 * The pictures exist for *mood*, not for reference: a study page with a
 * drawing on it reads as friendly rather than as a wall of text. That is why
 * the house style below asks for loose sketch work and explicitly forbids
 * fine detail and lettering — a 7" e-ink panel throws away small marks, and
 * an image model's rendering of words can't be trusted in a book someone is
 * learning from. Captions are drawn by the app in real type instead.
 */

/**
 * Every figure is requested and displayed at this ratio. Fixing it is what
 * lets the paginator compute a figure's height without opening the file.
 */
const val FIGURE_ASPECT_RATIO: String = "4:3"

/**
 * The rules every figure obeys whatever its job — the ones that decide
 * whether a course looks like a book or like a pile of stock AI art. Tune
 * here and nowhere else.
 */
private val SHARED_STYLE: List<String> = listOf(
    "Medium: a loose hand-drawn ink sketch, the kind someone doodles in the margin of a notebook with a felt-tip pen. Visible hand wobble in the lines. Output a flat drawing, not a photograph of one — no paper texture, no sketchbook page, no binding, no page edges, no camera lighting. Not a 3D render, not clip art, not vector art.",
    "Colour: pure greyscale. Black ink on a pure white background — #FFFFFF, the same white as the page it sits on. No colour anywhere.",
    "Line and tone: PURE LINE ART. Bold confident black outlines that survive being shrunk, on white. Shading only ever as hatching or cross-hatching — visible separate pen strokes. Absolutely no filled grey areas, no grey wash, no marker tone, no soft shading, no gradients, no drop shadows and no large solid black areas. If a shape needs to look darker, hatch it with more lines; never fill it with tone.",
    "Detail level: keep it simple and bold. This is reduced to a few centimetres on a low-resolution grey screen, so fine detail, thin lines and small elements disappear entirely.",
    "WHAT IS IN FRONT HIDES WHAT IS BEHIND. Decide which object is nearest the viewer, draw that one whole, and let everything behind it STOP at its outline and pick up again on the other side. Never run a line straight across something that is in front of it. A hand resting on strings hides the strings it covers; a finger across a hole hides that part of the hole; an arm in front of a body interrupts the body's edge. Drawn the wrong way round, the near object looks transparent, or looks sunk inside the thing it is only touching — and this is the error a reader spots before any other, because it makes an otherwise good drawing read as wrong without their being able to say why.",
    "SPELLING IS PART OF THE DRAWING: write each word once, spelled correctly, complete. Do not print a word twice, do not start a word and restart it beside itself, do not leave one half-finished or trailing off. Read the lettering back before finishing — a picture that says \"LEAR LEARNING\" is a spoiled picture however good the drawing is. If a word is hard to letter, choose a shorter one.",
    "TEXT SIZE IS CRITICAL: hand-letter every word ENORMOUS. Each capital letter must be at least one twelfth of the whole image height, and a speech bubble must span at least a third of the image width — comic-book lettering, absurdly oversized, as if shouted. Write text straight and horizontal, never rotated, curved or along a slope. The whole picture is reduced to a few centimetres on a small grey screen, so normal-sized lettering becomes an unreadable smudge. If the words will not fit at that size, use fewer words — never smaller ones.",
    "Framing: draw ONLY the illustration itself, never the page it will sit on. No border or frame line around the image, no header, no running title, no book or chapter name, no page number, no caption, no margins, no drop shadow, no watermark. The app puts the picture on the page and sets all type itself.",
    "DRAW THE FORM, NOT THE FINISH. This prints on a small reflective grey panel, so colour, gloss, browning and photographic surface cannot arrive at all. If the brief mentions any of them, render the thing through its shape, its outline and its hatching instead — never reach for tone or realism to carry it. A picture that depends on how something LOOKS in colour is a picture this book cannot print.",
    "Background: flat pure white, no tint, no grey wash, no paper texture, no vignette — any background tone shows up as a visible grey rectangle on the page. Draw no setting or scenery unless the idea genuinely needs one.",
)

/** What a MOOD figure adds: one vivid subject, a shouted line, a smile. */
private val MOOD_STYLE: List<String> = listOf(
    "Composition: ZOOM IN CLOSE. The subject must fill the frame edge to edge, taking up at least three quarters of the image — crop into it if that helps. No wide empty margins, no distant wide shot, no small figure lost in a large scene. Draw one clear subject only.",
    "Text, if any: at most a handful of words — a short speech bubble or one big label. A character saying something in a speech bubble is welcome and encouraged. Keep it to three or four SHORT everyday words; a long word is a long word to hand-letter and comes back mangled.",
    "Mood: warm, a little humorous, human. The point is to make a page of study material feel alive.",
)

/**
 * What a SHOW figure adds instead: this one has a job, and being charming is
 * not it. The reader is going to copy what this picture shows, so the thing
 * has to be legible and right, and anything decorative is competition.
 *
 * The hand rule is here because it is the known failure. A grip is among the
 * hardest subjects an image model is asked for — fused fingers, six of them,
 * impossible thumbs — and a wrong hand does not read as charmingly loose, it
 * reads as broken. Loose ink line art is far more forgiving than photorealism
 * (wobble passes as style), which is the only reason this is worth trying at
 * all.
 */
private val SHOW_STYLE: List<String> = listOf(
    "THE PICTURE HAS A JOB: the reader is looking at this to learn how the thing actually is, and will copy it. Draw the thing itself, plainly and correctly. Do not turn it into a joke, a metaphor, a mascot or a scene — those are welcome elsewhere in this book but they are wrong here.",
    "Composition: one close, plain view of the subject, filling the frame. No setting, no scenery, no background objects, no bystanders, no characters reacting. Nothing in the frame that is not part of what is being shown.",
    "Accuracy is the whole point: proportions correct, and the specific relationship the brief describes clearly visible — what touches what, what sits where, which way round it goes. If an angle would hide the thing being shown, choose the angle that reveals it.",
    "IF A HAND APPEARS, it is the hardest part of this drawing and the part most likely to spoil it: draw exactly five fingers, separate and countable, in a pose a real hand can hold. Show clearly where each finger contacts the object. A hand with the wrong number of fingers makes the picture useless, however good the rest is.",
    "WHICH HAND IT IS, IS PART OF THE INSTRUCTION. When the brief names the left or the right hand, draw THAT hand and not a mirror of it. This is got wrong constantly and it is not a small error: the reader is copying the picture, so a mirrored hand teaches the posture backwards, and on an instrument it swaps the two hands' jobs entirely. Check it before finishing — seen from the back of the hand with the fingers pointing towards the LEFT of the picture, a RIGHT hand's thumb is on the LOWER side and a left hand's thumb is on the upper side; the other way round when the fingers point right. Assume a right-handed person unless the brief says otherwise.",
    "THE POSE IN THE BRIEF IS THE SUBJECT, NOT A SUGGESTION. If it says which fingers are extended and which are curled, which one touches and which stay clear, draw exactly that and nothing looser — a relaxed fist means a closed fist, not an open hand. An easier or more natural-looking pose is the WRONG picture: the reader is copying this, and the pose is the entire thing being taught. Check the drawn pose against the brief before finishing.",
    "Text, if any: at most ONE short label of one or two words, and only if a part genuinely needs naming. No speech bubbles and no jokes in the lettering — words take space from the thing being shown.",
    "Mood: calm and clear rather than comic. Still warm and hand-drawn, never cold or technical — a patient teacher's sketch on a napkin, not an engineering drawing.",
)

/**
 * Backwards-compatible name for the mood style, which is what "the house
 * style" meant when there was only one. Referenced by design.md §7.
 */
internal val SKETCH_HOUSE_STYLE: String =
    (SHARED_STYLE + MOOD_STYLE).joinToString("\n") { "- $it" }

/** The style rules for one kind of figure, as a prompt block. */
internal fun houseStyleFor(kind: FigureKind): String = when (kind) {
    FigureKind.MOOD -> SKETCH_HOUSE_STYLE
    // Show rules first: they decide what the picture is, and the shared rules
    // only decide how it is drawn.
    FigureKind.SHOW -> (SHOW_STYLE + SHARED_STYLE).joinToString("\n") { "- $it" }
}

/**
 * Builds the image-generation brief for one figure slot. Image models take
 * the whole instruction in the user turn — there is no system prompt — so
 * the style rules ride along with every single request.
 */
internal fun buildFigurePrompt(
    course: Course,
    chapterTitle: String,
    brief: String,
    context: String,
    kind: FigureKind = FigureKind.MOOD,
): String = buildString {
    appendLine(
        when (kind) {
            FigureKind.MOOD ->
                "Draw a single illustration to be placed on a page of a study book."
            FigureKind.SHOW ->
                "Draw a single instructional illustration for a page of a study book. " +
                    "The reader will look at it to see how the thing actually is, and copy it."
        },
    )
    appendLine()
    appendLine("WHERE IT APPEARS — background knowledge only. These are NOT things to draw,")
    appendLine("and none of these words may appear anywhere in the image:")
    appendLine("  Book: ${course.title}")
    appendLine("  Chapter: $chapterTitle")
    appendLine()
    appendLine("WHAT THE PICTURE SHOULD SHOW")
    appendLine(brief.trim())
    if (context.isNotBlank()) {
        appendLine()
        appendLine("WHAT THE PAGE AROUND IT SAYS (for context — do not illustrate it literally)")
        appendLine(context.trim())
    }
    appendLine()
    // Belt and braces: the brief usually arrives already written in the
    // book's language, but every rule above and below this line is in
    // English, which is enough on its own to pull the lettering back to
    // English. Said explicitly, for every picture, not just covers.
    appendLine(languageRule(course.language))
    appendLine()
    appendLine("STYLE — follow every rule exactly:")
    append(houseStyleFor(kind))
}

/**
 * Width ÷ height of every figure — the numeric form of [FIGURE_ASPECT_RATIO],
 * used by the reader to reserve space and by the paginator to measure it.
 * Keep the two in sync.
 */
const val FIGURE_ASPECT: Float = 4f / 3f

/**
 * The chapter's first words as plain prose, at most [maxChars] of them.
 *
 * Not [parseMarkdown]: this feeds a prompt, not a page, so the cheap line
 * scan that [findFigureSlots] uses is the right shape — and it can afford to
 * be lossy where a renderer cannot. Fenced blocks are dropped whole. An
 * exercise or a recap is not the opening scene, and a figure fence is a brief
 * for a picture that will already be on the page a little further down; both
 * would only pull the cover towards the wrong moment. Heading markers go,
 * their words stay, because "Why your first plane veers left" is exactly the
 * kind of line that says what the chapter opens on.
 *
 * Truncation is at a word boundary and marked, so the model can see that it
 * was handed a beginning rather than a whole chapter.
 */
internal fun chapterOpening(markdown: String, maxChars: Int = 1200): String {
    if (markdown.isBlank()) return ""

    val kept = mutableListOf<String>()
    var inFence = false
    var titleDropped = false
    var length = 0

    for (raw in markdown.lines()) {
        val line = raw.trim()
        if (line.startsWith("```")) {
            // The single-line ```figure alt="…"``` shape opens and closes on
            // one line; toggling on it would swallow the rest of the chapter.
            val selfClosing = !inFence && line.removePrefix("```").trimEnd().endsWith("```")
            if (!selfClosing) inFence = !inFence
            continue
        }
        if (inFence) continue

        if (line.isBlank()) {
            // One blank line between paragraphs, never a run of them.
            if (kept.isNotEmpty() && kept.last().isNotBlank()) kept += ""
            continue
        }

        val text = line.trimStart('#').trim()
        if (text.isBlank()) continue
        // The chapter's own h1 is its title, which this prompt already has.
        if (!titleDropped && line.startsWith("# ")) {
            titleDropped = true
            continue
        }

        kept += text
        length += text.length + 1
        if (length >= maxChars) break
    }

    val prose = kept.joinToString("\n").trim()
    if (prose.length <= maxChars) return prose
    val cut = prose.take(maxChars)
    val lastSpace = cut.lastIndexOfAny(charArrayOf(' ', '\n'))
    return (if (lastSpace > 0) cut.take(lastSpace) else cut).trimEnd() + " …"
}

/**
 * Brief-writing prompt for a chapter's opening picture.
 *
 * The cover is asked for in two steps rather than one. A figure inside a
 * chapter gets its brief from the chapter model, which has already decided
 * what scene makes the point ("a tiny exhausted gnome on a mountain of paper
 * bags"); a cover has no such brief, and handing an image model a title and a
 * list of objectives gets a literal, lifeless illustration of the words. So
 * the text model writes the brief first, then the image model draws it.
 *
 * [chapterMarkdown] is the chapter's own finished text, and it is what stops
 * the picture and the page disagreeing. Written from the title and the
 * objectives alone, this prompt invented a metaphor of its own — while the
 * chapter model had already invented a different one for the same idea, and
 * put it in the first paragraph. The reader met a woman tasting cookies on
 * the title page and a paper plane being thrown and corrected on the page
 * after it, two pictures of training a model with nothing in common
 * (reported from the neural-networks book, chapter 4, 2026-09-28). Nothing
 * was missing but the wiring: in every flow the prose is written and saved
 * before any picture is drawn, so the chapter's own opening scene was
 * already on disk when the cover was briefed. Blank is allowed and behaves
 * as before — a cover can still be drawn for a chapter whose text is not to
 * hand.
 */
internal fun buildCoverBriefPrompt(
    course: Course,
    chapter: PersistedChapter,
    chapterMarkdown: String = "",
): String = buildString {
    appendLine("A study book is opening a new chapter, and it needs one picture for the")
    appendLine("chapter's title page.")
    appendLine()
    appendLine("BOOK: ${course.title}")
    appendLine("CHAPTER ${chapter.n}: ${chapter.title}")
    if (chapter.objectives.isNotEmpty()) {
        appendLine("WHAT THE READER WILL LEARN:")
        chapter.objectives.forEach { appendLine("  - $it") }
    }
    appendLine()

    val opening = chapterOpening(chapterMarkdown)
    if (opening.isNotBlank()) {
        appendLine("HOW THE CHAPTER ITSELF OPENS — its first words, as the reader meets them")
        appendLine("one page after this picture:")
        appendLine(opening)
        appendLine()
        appendLine("THE CHAPTER'S OWN OPENING IS THE SCENE TO DRAW. A chapter like this one")
        appendLine("makes its point with something concrete — an object, an activity, someone")
        appendLine("doing something. Draw THAT, and do not invent a second metaphor for the")
        appendLine("same idea. The reader turns this picture over and immediately reads those")
        appendLine("words, so two unrelated pictures of one idea make the book read as two")
        appendLine("books stitched together, and leave the reader holding the wrong one.")
        appendLine()
    }

    val drawnInside = findFigureSlots(chapterMarkdown).map { it.brief }.filter { it.isNotBlank() }
    if (drawnInside.isNotEmpty()) {
        appendLine("PICTURES THAT ALREADY APPEAR INSIDE THIS CHAPTER:")
        drawnInside.forEach { appendLine("  - ${it.replace('\n', ' ').trim()}") }
        appendLine("Do not describe one of these a second time. If one of them already draws")
        appendLine("the opening moment, draw a DIFFERENT moment of the same scene — the same")
        appendLine("character and the same objects, earlier or later — never a different")
        appendLine("metaphor.")
        appendLine()
    }

    // One instruction or the other, never both. An "invent a metaphor"
    // paragraph left standing under "draw the chapter's own" is a
    // contradiction the model resolves however it likes, and the whole point
    // here is that it stops resolving it the wrong way.
    if (opening.isNotBlank()) {
        appendLine("Describe ONE concrete scene an artist should draw — the chapter's own,")
        appendLine("taken from the words above. Catch it at its PROBLEM moment: the thing")
        appendLine("not working yet, the character stuck or mid-struggle — not a summary of")
        appendLine("what the chapter concludes, and not a diagram of the topic. It should")
        appendLine("raise a smile.")
        appendLine()
        appendLine("Only if the opening gives you nothing at all to picture — no object, no")
        appendLine("activity, nobody doing anything — invent a scene instead: a physical,")
        appendLine("everyday metaphor with a character in it, something going wrong or")
        appendLine("someone facing an absurd amount of work.")
    } else {
        appendLine("Describe ONE concrete scene an artist should draw. The scene must capture")
        appendLine("the PROBLEM this chapter solves — the difficulty or the question the reader")
        appendLine("arrives with — not a summary of the answer, and not a diagram of the topic.")
        appendLine("Use a physical, everyday metaphor with a character in it: something going")
        appendLine("wrong, someone stuck, someone facing an absurd amount of work. It should")
        appendLine("raise a smile.")
    }
    appendLine()
    appendLine("Rules: one vivid subject, no diagrams, no charts, no screens full of text,")
    appendLine("no labels or numbers. A short shouted speech bubble of three or four")
    appendLine("short words is welcome if it sharpens the joke — short ones, because the")
    appendLine("artist hand-letters them and long words come back misspelled.")
    appendLine()
    // The cover is the one picture whose words are invented HERE rather than
    // by the chapter model, which is told what language the book is in. Say
    // it again or the brief comes back in English and a Russian book opens
    // with a character shouting "HOW DOES THIS FIT?!" — observed on the Boox,
    // 2026-09-23, while the chapter's own figures lettered clean Cyrillic.
    appendLine("${languageRule(course.language)}")
    appendLine()
    appendLine("Reply with the scene description only — one or two sentences, nothing else.")
}

/**
 * What language the words inside a picture are written in.
 *
 * Said to every model that can put words in a picture, because each of them
 * would otherwise default to the language these instructions are written in.
 * The escape hatch matters as much as the rule: a script the model cannot
 * letter cleanly produces gibberish that looks like damage, and a wordless
 * picture is better than a spoiled one. It has not been needed for Cyrillic,
 * which comes back correctly spelled.
 */
internal fun languageRule(language: String): String =
    if (!needsTranslation(language)) {
        "Any words in the picture are in English."
    } else {
        "IMPORTANT: this book is written in $language, so every word inside the picture — " +
            "speech bubble or label — must be in $language, in that language's own alphabet, " +
            "spelled correctly. Never write them in English. If you cannot letter that " +
            "alphabet cleanly, draw the picture with NO words at all rather than with " +
            "malformed ones."
    }

/**
 * The image brief for a chapter cover. Same house style as any other figure:
 * a cover is a full-width picture on the page, not a badge, so the rules that
 * matter are the figure ones.
 */
internal fun buildCoverPrompt(course: Course, chapter: PersistedChapter, brief: String): String =
    buildFigurePrompt(
        course = course,
        chapterTitle = chapter.title,
        brief = brief,
        context = "",
    )

/** File name a chapter's cover is stored under. */
fun chapterCoverName(chapterSlug: String, extension: String): String =
    "$chapterSlug-cover.${extension.ifBlank { "png" }}"

/**
 * Brief-writing prompt for a chapter's *closing* picture.
 *
 * Not another illustration — the payoff to the cover. The cover shows the
 * difficulty the reader arrives with; this shows the same scene after they
 * have learned the thing. Same character, same setting, same objects; only
 * the situation has changed. That is why it takes the cover's own brief as
 * input rather than the chapter title: it is a rewrite of one scene, not a
 * second idea about the chapter.
 */
internal fun buildClosingBriefPrompt(
    course: Course,
    chapter: PersistedChapter,
    coverBrief: String,
): String = buildString {
    appendLine("A study book chapter opened with this picture:")
    appendLine()
    appendLine(coverBrief.trim())
    appendLine()
    appendLine("BOOK: ${course.title}")
    appendLine("CHAPTER ${chapter.n}: ${chapter.title}")
    if (chapter.objectives.isNotEmpty()) {
        appendLine("WHAT THE READER HAS NOW LEARNED:")
        chapter.objectives.forEach { appendLine("  - $it") }
    }
    appendLine()
    appendLine("Describe the SAME scene at the end of the chapter, once the reader has")
    appendLine("learned all of that. Keep the same character, the same setting and the same")
    appendLine("objects — a reader must recognise it instantly as the opening picture")
    appendLine("revisited. Change only the SITUATION: what was overwhelming is now handled")
    appendLine("easily, what was a mess is in order, the character who was buried in it is")
    appendLine("on top of it and pleased with themselves.")
    appendLine()
    appendLine("Do NOT invent a new metaphor, a new place or a new cast. Do not simply")
    appendLine("remove the problem — show the same objects mastered, so the pair reads as")
    appendLine("before and after.")
    appendLine()
    appendLine("${languageRule(course.language)}")
    appendLine()
    appendLine("Reply with the scene description only — one or two sentences, nothing else.")
}

/** File name a chapter's closing picture is stored under. */
fun chapterTailName(chapterSlug: String, extension: String): String =
    "$chapterSlug-tail.${extension.ifBlank { "png" }}"

