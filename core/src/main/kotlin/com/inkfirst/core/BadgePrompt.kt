package com.inkfirst.core

/**
 * Prompt construction for *badges* — the little drawn marks that sit at the
 * top-left of an attention block (exercise, recap) the way a Head First page
 * puts a small character next to every special section.
 *
 * A badge is not a small figure. A figure renders ~1100px wide on the Boox
 * and can carry hatching and a shouted speech bubble; a badge renders around
 * 120px, where hatching turns to mud and any lettering is hopeless. So it
 * gets its own house style: fewer, fatter strokes, no text at all, and one
 * subject with nothing around it.
 *
 * Badges belong to the *course*, not the chapter: drawn once, stored under
 * `badges/`, and reused by every chapter. See docs/design.md §7.
 */

/** Badges are square — a block label is a row, and a square sits in it cleanly. */
const val BADGE_ASPECT_RATIO: String = "1:1"

/**
 * The badge set a course starts from, in drawing order. The first one drawn
 * defines the mascot and the rest are drawn from it (see [BookBadger]), so
 * this order decides which block sets the family look.
 *
 * It is only a starting point: [badgeKindsIn] discovers what a chapter
 * actually contains, so a book that turns out to use code blocks grows a code
 * badge the first time one is read.
 */
val BADGE_KINDS: List<String> = listOf("exercise", "recap", "answer")

/**
 * Ceiling on how many badges one course can accumulate. The chapter model is
 * free to invent fence kinds, and each new one costs a picture — without a
 * cap, a model in a naming mood could quietly run up a bill. Six is more
 * distinct block types than a readable chapter should have anyway.
 */
const val MAX_BADGE_KINDS: Int = 6

/** Fences that are pictures or page furniture rather than a block with a voice. */
private val UNBADGED_FENCES = setOf("figure", "meta")

/**
 * Info words that mean "this is a specimen of code". A code fence carries its
 * *language* as the fence kind — ```python, ```bash — so without this every
 * language in a book would be drawn its own mascot, which is both expensive
 * and incoherent: they are all the same voice saying "here is some code".
 */
private val CODE_FENCE_WORDS = setOf(
    "code", "python", "py", "kotlin", "kt", "java", "javascript", "js", "jsx",
    "typescript", "ts", "tsx", "bash", "sh", "shell", "zsh", "console",
    "terminal", "cmd", "powershell", "sql", "json", "yaml", "yml", "toml",
    "ini", "xml", "html", "css", "scss", "c", "cpp", "c++", "csharp", "cs",
    "go", "golang", "rust", "rs", "ruby", "rb", "php", "swift", "r", "scala",
    "perl", "lua", "dart", "haskell", "elixir", "clojure", "matlab", "text",
    "txt", "plaintext", "plain", "diff", "patch", "makefile", "dockerfile",
)

private val SAFE_KIND = Regex("""^[a-z0-9_-]{1,40}$""")

/**
 * The badge a fence of this kind should carry, or null if it should carry
 * none. Normalising here is what keeps one book to a handful of marks.
 */
fun badgeKindFor(fenceKind: String): String? {
    val kind = fenceKind.trim().lowercase()
    if (kind in UNBADGED_FENCES) return null
    if (kind.isBlank() || kind in CODE_FENCE_WORDS) return "code"
    if (!kind.matches(SAFE_KIND)) return null
    return kind
}

/**
 * The badges a chapter needs, in the order they first appear. Discovery means
 * the set grows with the book rather than being fixed in advance — a chapter
 * that introduces a new kind of block earns it a mark the first time it is
 * opened.
 *
 * "answer" rides along with "exercise" because it is revealed inside one
 * rather than being a fence of its own.
 */
fun badgeKindsIn(markdown: String): List<String> {
    val kinds = LinkedHashSet<String>()
    parseMarkdown(markdown).forEach { block ->
        if (block is Block.CustomFence) badgeKindFor(block.kind)?.let(kinds::add)
    }
    if ("exercise" in kinds) kinds += "answer"
    return kinds.take(MAX_BADGE_KINDS)
}

/**
 * What each badge should depict. Deliberately a *role*, not a picture: the
 * mascot itself comes from the course topic, and this says what it is doing.
 */
internal fun badgeBrief(kind: String): String = when (kind) {
    "exercise" -> "The mascot at work on a problem — leaning in with a chunky pencil, " +
        "sleeves up, clearly about to try something. It should read as \"your turn now\"."
    "recap" -> "The mascot pausing and satisfied — sitting back with one finger raised in a " +
        "small aha, or hugging a tied-up bundle. It should read as \"that's the idea, hold on to it\"."
    "answer" -> "The mascot giving the game away — holding up a card, or leaning around a corner " +
        "with a hand cupped beside its mouth as if telling you a secret. It should read as " +
        "\"here is the answer\", and must be clearly a different pose from the working-it-out one."
    "code" -> "The mascot typing — hunched over a small keyboard with both mitten hands on " +
        "the keys, absorbed. It should read as \"here is the actual code\"."
    // Any fence kind the chapter model invents. The word itself is the brief:
    // these models read "gotcha" or "warning" as a pose perfectly well, and a
    // hand-written brief per kind is impossible when the set is open-ended.
    else -> "The mascot in a pose that communicates the idea of \"$kind\" — act the word out " +
        "with body language and expression alone, and do not write it."
}

/**
 * The badge equivalent of [SKETCH_HOUSE_STYLE]. Shorter and much stricter,
 * because everything here is fighting the display size.
 */
internal val BADGE_HOUSE_STYLE: String = listOf(
    "Medium: a hand-drawn ink doodle, felt-tip pen, visible hand wobble. Flat drawing — not a photograph of one, not a 3D render, not clip art, not vector art, not an app icon.",
    "Colour: black ink on flat pure white #FFFFFF. No colour, no grey, anywhere.",
    "Background: FLAT DIGITAL WHITE, every single background pixel pure #FFFFFF. No paper texture, no grain, no fibres, no sketchbook page, no photograph of a drawing, no scan, no page edge, no shadow, no vignette, no off-white or cream tint. The page behind this drawing is pure white, so any tone at all shows up as a visible grey square around it.",
    "Line: PURE LINE ART with THICK, HEAVY outlines. No hatching, no cross-hatching, no shading, no tone, no fill, no shadow. Hatching that works on a big drawing turns into a grey smudge at this size.",
    "LINE WEIGHT IS CRITICAL: every stroke must be at least one fiftieth of the image width thick — a broad marker or a brush, never a pen nib. Far thicker than feels natural. The drawing is shrunk to about a tenth of its size before anyone sees it, and at that reduction a normal-weight line averages out into pale grey and disappears. If a detail needs a thin line to work, leave the detail out.",
    "Detail: BRUTALLY simple. Aim for under twenty pen strokes in the whole drawing. No fine detail, no texture, no small parts, no thin lines, no busy outlines — every one of those disappears.",
    "Composition: one single subject, centred, filling nearly the whole square edge to edge. No scene, no setting, no ground line, no props beyond the one named, no second character.",
    "ABSOLUTELY NO TEXT. No letters, no numbers, no speech bubble, no label, no caption, no signature. This drawing is displayed about the size of a thumbnail, where any lettering is an unreadable blot.",
    "No frame, no border, no circle or badge shape around it, no background panel, no drop shadow, no watermark. Just the subject on white.",
    "This is printed roughly one centimetre square on a low-resolution grey e-ink screen. If a detail would not survive that, leave it out.",
).joinToString("\n") { "- $it" }

/**
 * Tone of the mascot, following the course's teaching style. A Head First-ish
 * course wants a character with a face; a formal textbook wants an emblem
 * rather than a cartoon, or the page stops looking serious.
 *
 * A custom style brings its own line (written with the style itself — see
 * [StyleBrief]) because the arms below are keyed on the built-in presets and
 * a described style matches none of them. Without it every custom book, sober
 * or silly, fell through to the friendly-cartoon arm.
 */
private fun mascotTone(course: Course): String {
    if (course.styleMascot.isNotBlank()) return course.styleMascot.trim()
    return mascotToneForKey(course.style)
}

private fun mascotToneForKey(styleKey: String): String = when (styleKey) {
    StylePresets.TEXTBOOK.key ->
        "a simple, restrained emblem — an object rather than a cartoon character, " +
            "no face, calm and formal, the kind of mark a serious textbook would print"
    StylePresets.PRACTICAL.key ->
        "a plain, sturdy workshop object or a pair of hands doing the work — " +
            "practical and unfussy, no whimsy"
    else ->
        "a friendly cartoon mascot with a face and clear body language — expressive, " +
            "a little funny, the kind of character a playful study book would use throughout"
}

/**
 * Builds the brief for one badge.
 *
 * The first badge of a set invents the mascot and is asked to describe it in
 * words too. Every later badge is drawn from that description ([mascot]) with
 * the first picture attached as a reference ([hasReference]), which is what
 * keeps one course's badges recognisably the same creature. Either input
 * alone is enough — a model that returns no description still gets the image.
 */
internal fun buildBadgePrompt(
    course: Course,
    kind: String,
    mascot: String = "",
    hasReference: Boolean = false,
): String = buildString {
    appendLine("Draw one tiny black-and-white doodle. It will be printed about one")
    appendLine("centimetre square next to a section heading in a study book.")
    appendLine()
    if (!hasReference && mascot.isBlank()) {
        appendLine("INVENT THE BOOK'S MASCOT — this is the first drawing of a set, and every")
        appendLine("later drawing copies this character. Make it ${mascotTone(course)}.")
        appendLine("Base it on what the book is about, but keep it simple enough to redraw:")
        appendLine("  Book: ${course.title}")
        if (course.topicPrompt.isNotBlank()) {
            appendLine("  About: ${course.topicPrompt.trim().take(300)}")
        }
        appendLine("Do NOT draw any of those words — they describe the subject, not the picture.")
        appendLine()
        appendLine("ALSO REPLY IN TEXT, alongside the picture: one sentence, under 25 words,")
        appendLine("describing the character you drew — what it is, its shape, its one or two")
        appendLine("distinguishing features — so another artist could redraw the same creature.")
        appendLine("Describe the character only: not its pose, not the background, not the style.")
    } else {
        appendLine("DRAW THE SAME CHARACTER AGAIN — same creature, same shapes, same pen, same")
        appendLine("thickness of line, new pose. It must be instantly recognisable as the same")
        appendLine("one; do not redesign it, do not restyle it, do not add anything to it.")
        if (hasReference) {
            appendLine("The attached picture is that character. Copy the character from it —")
            appendLine("but NOT any paper texture, grain or off-white tint it may have: your")
            appendLine("drawing goes on flat pure white.")
        }
        if (mascot.isNotBlank()) {
            appendLine("  The character: ${mascot.trim()}")
        }
    }
    appendLine()
    appendLine("WHAT IT IS DOING")
    appendLine(badgeBrief(kind))
    appendLine()
    appendLine("STYLE — follow every rule exactly:")
    append(BADGE_HOUSE_STYLE)
}
