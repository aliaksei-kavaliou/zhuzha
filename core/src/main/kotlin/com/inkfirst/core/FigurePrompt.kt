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
 * The single string that decides whether a course looks like a book or like
 * a pile of stock AI art. Appended verbatim to every image request, so all
 * figures in all courses share one visual voice. Tune here and nowhere else.
 */
internal val SKETCH_HOUSE_STYLE: String = listOf(
    "Medium: a loose hand-drawn ink sketch, the kind someone doodles in the margin of a notebook with a felt-tip pen. Visible hand wobble in the lines. Output a flat drawing, not a photograph of one — no paper texture, no sketchbook page, no binding, no page edges, no camera lighting. Not a 3D render, not clip art, not vector art.",
    "Colour: pure greyscale. Black ink on a pure white background — #FFFFFF, the same white as the page it sits on. No colour anywhere.",
    "Line and tone: PURE LINE ART. Bold confident black outlines that survive being shrunk, on white. Shading only ever as hatching or cross-hatching — visible separate pen strokes. Absolutely no filled grey areas, no grey wash, no marker tone, no soft shading, no gradients, no drop shadows and no large solid black areas. If a shape needs to look darker, hatch it with more lines; never fill it with tone.",
    "Composition: ZOOM IN CLOSE. The subject must fill the frame edge to edge, taking up at least three quarters of the image — crop into it if that helps. No wide empty margins, no distant wide shot, no small figure lost in a large scene. Draw one clear subject only.",
    "Detail level: keep it simple and bold. This is reduced to a few centimetres on a low-resolution grey screen, so fine detail, thin lines and small elements disappear entirely.",
    "Text, if any: at most a handful of words — a short speech bubble or one big label. A character saying something in a speech bubble is welcome and encouraged. Keep it to four or five words.",
    "TEXT SIZE IS CRITICAL: hand-letter every word ENORMOUS. Each capital letter must be at least one twelfth of the whole image height, and a speech bubble must span at least a third of the image width — comic-book lettering, absurdly oversized, as if shouted. Write text straight and horizontal, never rotated, curved or along a slope. The whole picture is reduced to a few centimetres on a small grey screen, so normal-sized lettering becomes an unreadable smudge. If the words will not fit at that size, use fewer words — never smaller ones.",
    "Mood: warm, a little humorous, human. The point is to make a page of study material feel alive.",
    "Framing: draw ONLY the illustration itself, never the page it will sit on. No border or frame line around the image, no header, no running title, no book or chapter name, no page number, no caption, no margins, no drop shadow, no watermark. The app puts the picture on the page and sets all type itself.",
    "Background: flat pure white, no tint, no grey wash, no paper texture, no vignette — any background tone shows up as a visible grey rectangle on the page. Draw no setting or scenery unless the idea genuinely needs one.",
).joinToString("\n") { "- $it" }

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
): String = buildString {
    appendLine("Draw a single illustration to be placed on a page of a study book.")
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
    appendLine("STYLE — follow every rule exactly:")
    append(SKETCH_HOUSE_STYLE)
}

/**
 * Width ÷ height of every figure — the numeric form of [FIGURE_ASPECT_RATIO],
 * used by the reader to reserve space and by the paginator to measure it.
 * Keep the two in sync.
 */
const val FIGURE_ASPECT: Float = 4f / 3f

/**
 * Brief-writing prompt for a chapter's opening picture.
 *
 * The cover is asked for in two steps rather than one. A figure inside a
 * chapter gets its brief from the chapter model, which has already decided
 * what scene makes the point ("a tiny exhausted gnome on a mountain of paper
 * bags"); a cover has no such brief, and handing an image model a title and a
 * list of objectives gets a literal, lifeless illustration of the words. So
 * the text model writes the brief first, then the image model draws it.
 */
internal fun buildCoverBriefPrompt(course: Course, chapter: PersistedChapter): String = buildString {
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
    appendLine("Describe ONE concrete scene an artist should draw. The scene must capture")
    appendLine("the PROBLEM this chapter solves — the difficulty or the question the reader")
    appendLine("arrives with — not a summary of the answer, and not a diagram of the topic.")
    appendLine("Use a physical, everyday metaphor with a character in it: something going")
    appendLine("wrong, someone stuck, someone facing an absurd amount of work. It should")
    appendLine("raise a smile.")
    appendLine()
    appendLine("Rules: one vivid subject, no diagrams, no charts, no screens full of text,")
    appendLine("no labels or numbers. A short shouted speech bubble of four or five words")
    appendLine("is welcome if it sharpens the joke.")
    appendLine()
    appendLine("Reply with the scene description only — one or two sentences, nothing else.")
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
    appendLine("Reply with the scene description only — one or two sentences, nothing else.")
}

/** File name a chapter's closing picture is stored under. */
fun chapterTailName(chapterSlug: String, extension: String): String =
    "$chapterSlug-tail.${extension.ifBlank { "png" }}"

