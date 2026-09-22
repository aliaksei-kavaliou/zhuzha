package com.inkfirst.core

import java.util.Locale

/**
 * How many pictures a book is allowed to draw, and what that comes to in
 * money.
 *
 * Until this existed there was no limit at all. `ChapterIllustrator` drew
 * every slot it found, and the only thing standing between a chapter and a
 * dozen pictures was one sentence of prompt guidance — "a chapter of pure
 * ideas is well served by two". Guidance in a prompt is a suggestion; this is
 * the fact. A model that decides a chapter wants twelve pictures is not
 * misbehaving, it is answering the question it was asked, and the answer
 * costs about 36 cents in silence.
 *
 * Stored per course alongside [Course.language] and [Course.units], for the
 * same reason: it is a property of the BOOK, fixed when the book was
 * commissioned. A course lavishly illustrated in chapter 1 and bare by
 * chapter 6 is worse than either.
 *
 * Nothing here can lose content. A figure over the ceiling keeps its alt text
 * on the page, exactly as an undrawn figure always has, and the reader's menu
 * still offers to draw it — so the ceiling turns the last few pictures from a
 * charge into a decision.
 */
data class FigurePolicy(
    val key: String,
    val label: String,
    /** One line for the picker, saying what this costs in practice. */
    val note: String,
    /**
     * Hard ceiling on the figures drawn in one automatic pass. Enforced in
     * [ChapterIllustrator.illustrate], not merely requested in the prompt.
     *
     * Per PASS, not per chapter for all time: the reader's menu draws what is
     * still pending without a ceiling, because there the count and the price
     * are on the row being tapped. The ceiling exists to bound what is spent
     * WITHOUT being asked.
     */
    val maxPerChapter: Int,
    /**
     * Whether compiling a chapter draws its pictures without being asked.
     * False makes every picture a deliberate tap in the reader's menu, where
     * its price is shown first.
     */
    val automatic: Boolean,
    /** Appended to the chapter prompt's figure section verbatim. */
    val rule: String,
)

val FIGURE_POLICIES: List<FigurePolicy> = listOf(
    FigurePolicy(
        key = "as-needed",
        label = "As needed",
        note = "The book decides from the material. Up to 6 a chapter, drawn as it is written.",
        maxPerChapter = 6,
        automatic = true,
        rule = "How MANY figures depends on how visual the subject is: a physical, hands-on " +
            "skill where the reader must see positions and movements can carry five or six, " +
            "while a chapter of pure ideas is well served by two. Never add one that has " +
            "nothing to show, and never more than SIX in this chapter.",
    ),
    FigurePolicy(
        key = "generous",
        label = "Generous",
        note = "For books where seeing it is most of the lesson. Up to 10 a chapter.",
        maxPerChapter = 10,
        automatic = true,
        rule = "This book is a visual one: include a picture wherever one would genuinely " +
            "help the reader see what is meant, and prefer showing to describing where both " +
            "would work. Never add one that has nothing to show, and never more than TEN in " +
            "this chapter.",
    ),
    FigurePolicy(
        key = "on-request",
        label = "On request",
        note = "Chapters are written with the briefs, but nothing is drawn until you ask.",
        maxPerChapter = 6,
        automatic = false,
        rule = "How MANY figures depends on how visual the subject is: a physical, hands-on " +
            "skill where the reader must see positions and movements can carry five or six, " +
            "while a chapter of pure ideas is well served by two. Never add one that has " +
            "nothing to show, and never more than SIX in this chapter.",
    ),
    FigurePolicy(
        key = "none",
        label = "None",
        note = "Words only. Nothing is drawn and nothing is charged.",
        maxPerChapter = 0,
        automatic = false,
        rule = "Do NOT include any ```figure blocks in this chapter. This book is being " +
            "written without pictures, so anything a picture would have carried has to be " +
            "carried by the prose.",
    ),
)

const val DEFAULT_FIGURE_POLICY: String = "as-needed"

/** The policy for a stored key. Anything unrecognised reads as the default. */
fun figurePolicyFor(key: String?): FigurePolicy =
    FIGURE_POLICIES.firstOrNull { it.key == key?.trim()?.lowercase() }
        ?: FIGURE_POLICIES.first { it.key == DEFAULT_FIGURE_POLICY }

/** This book's policy. */
val Course.figurePolicy: FigurePolicy get() = figurePolicyFor(figures)

/**
 * Roughly what one generated picture costs, in US dollars.
 *
 * A constant rather than anything cleverer, because the app cannot know the
 * real number: the image model is a free-text field in Settings, so the rate
 * belongs to whatever the reader typed there. This tracks
 * [DEFAULT_GEMINI_IMAGE_MODEL]; every price the app shows carries a "~" and
 * means it.
 */
const val PICTURE_PRICE_USD: Double = 0.03

/** "~3¢", "~12¢", "~$1.20" — a price for a sentence, always marked approximate. */
fun pictureCost(count: Int): String {
    val total = count * PICTURE_PRICE_USD
    if (total < 1.0) return "~${Math.round(total * 100)}¢"
    return String.format(Locale.US, "~$%.2f", total)
}

/**
 * What the pictures of a whole book are likely to come to, for the page where
 * a course is commissioned.
 *
 * A range, and deliberately not a precise number: how many figures a chapter
 * carries is the model's call within the ceiling, so the honest answer is
 * "between a couple a chapter and the ceiling". Opening and closing pictures
 * are not counted — they are drawn from the reader's menu one at a time,
 * where each says its own price.
 */
fun bookPictureEstimate(chapters: Int, policy: FigurePolicy): String = when {
    policy.maxPerChapter == 0 -> "No pictures, nothing to pay for."
    !policy.automatic ->
        "Nothing is drawn until you ask for it, chapter by chapter — ${pictureCost(1)} a picture."
    else -> {
        val low = chapters * TYPICAL_FIGURES_PER_CHAPTER
        val high = chapters * policy.maxPerChapter
        "About $low–$high pictures over $chapters chapters — ${pictureCost(low)} to " +
            "${pictureCost(high)}, spent as each chapter is written."
    }
}

/** What an unremarkable chapter asks for, used as the low end of an estimate. */
private const val TYPICAL_FIGURES_PER_CHAPTER = 2
