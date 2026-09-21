package com.inkfirst.app.ui

import com.inkfirst.core.Annotation
import com.inkfirst.core.Stroke as InkStroke

/*
 * Tap routing for the reader, deliberately kept in a file of its own with no
 * Compose in it.
 *
 * The rules used to be spread across four places and drifted apart; the fix is
 * only durable if there is one obvious place they live. Everything here is a
 * pure function of a contact and the page's state, which also means it can be
 * unit-tested — see TapRoutingTest.
 */

/**
 * Where a tap lands. One rule, used by both tap detectors — the reader's own
 * and the Onyx overlay's, which had to take over page taps when the SDK
 * started owning the host view's touch stream.
 */
internal enum class TapZone { MENU, PREV, NEXT, NONE }

/**
 * How much of the page width each page-turn band takes, per side.
 *
 * The bands used to cover the whole page — left third back, the remaining two
 * thirds forward — on the theory that a centre tap means "move on". On the
 * device that theory lost: with a stylus in hand most contacts with the middle
 * of a page are incidental, and every one of them turned a page. The middle is
 * now inert, so the only way to move is to aim at an edge.
 */
internal const val PAGE_TURN_BAND_FRACTION = 0.25f

internal fun tapZoneFor(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    menuBandPx: Float,
): TapZone = when {
    // The turn bands run the FULL height of the page, and they are tested
    // first. The menu band used to be tested first and spanned the whole
    // width, so the two overlapped at the bottom corners — which is exactly
    // where a thumb aims for "next page" on a 7" device held one-handed, and
    // every one of those taps opened the format menu instead. A corner
    // belongs to the band it is in the corner OF.
    x < width * PAGE_TURN_BAND_FRACTION -> TapZone.PREV
    x > width * (1f - PAGE_TURN_BAND_FRACTION) -> TapZone.NEXT
    // What is left of the bottom band: the middle half, directly above the
    // footer line, which is itself a menu target across the full width. The
    // band is the generous margin around that target rather than the target.
    y > height - menuBandPx -> TapZone.MENU
    // The dead middle. Half the page that answers to nothing, which is the
    // point: it is where the text is and where the pen works.
    else -> TapZone.NONE
}

/**
 * What one finger contact means.
 *
 * The rules used to live in four places — [tapZoneFor], the reader's own
 * gesture detector, the Onyx overlay's finger branch, and the footer row —
 * and three bugs in a single session came from them drifting apart: an open
 * menu that could not be closed on the pen path, a chip that turned the page
 * instead of closing, a margin marker that was not a target at all.
 *
 * The two DETECTORS cannot be merged. While raw drawing is live the Onyx SDK
 * owns the host view's touch stream and Compose never sees the contact, so
 * the overlay has to observe it itself. What can be merged, and is merged
 * here, is the DECISION: both transports resolve through [resolveTap] and act
 * on what it hands back. A detector's remaining job is to notice a contact
 * and to deal with its own consequences (panel repaints on the pen path,
 * drag arbitration on the finger path) — never to decide what a tap meant.
 */
internal sealed interface TapAction {
    /** An open panel swallows the next tap wherever it lands. */
    data object CloseMenu : TapAction
    data object DismissChip : TapAction

    /** Drop a live text selection, and the chip offering actions on it. */
    data object ClearSelection : TapAction
    data class OpenMarker(val annotation: Annotation) : TapAction
    data class ShowStrokeChip(val stroke: InkStroke) : TapAction
    data object ToggleMenu : TapAction
    data object PrevPage : TapAction
    data object NextPage : TapAction

    /**
     * A button drawn on the page was aimed at — "Show answer" is the only one
     * so far.
     *
     * It needs a rung of its own because on the pen path the SDK owns the
     * host view's touch stream and the button's own `clickable` never runs:
     * the contact arrives here instead, and without a rule for it a button
     * sitting in a turn band simply turned the page. [blockIndex] names the
     * exercise whose answer to reveal.
     */
    data class PressButton(val blockIndex: Int) : TapAction

    /** Swipe left/right past the first page of a chapter, or up/down. */
    data object PrevChapter : TapAction
    data object NextChapter : TapAction

    /** The dead middle of the page. A real outcome, not a failure to decide. */
    data object Ignore : TapAction
}

/**
 * The ladder, in priority order. Each rung is something the reader put on the
 * screen and can reasonably expect the next tap to address:
 *
 *  1. menu open   — dismissing what is up beats anything underneath it, and
 *                   the panel covers the band that opened it, so without this
 *                   rung first the menu cannot be closed at all on the pen path.
 *  2. chip open    — same argument one level down. Turning the page instead
 *                   meant the only way to cancel a chip was to leave the page
 *                   it belonged to.
 *  3. selection  — a marked passage is up, with a chip offering things to do
 *                   with it. Dismissing it is what a tap means, and it must
 *                   NOT also turn the page: landing on a different page with
 *                   the selection silently dropped loses the reader's place.
 *                   Two taps to dismiss-then-turn is the same bargain the menu
 *                   and the chip already make.
 *  4. button hit   — a control the book drew, aimed at deliberately. Highest
 *                   of the three hit-tests: it is the only one that is a real
 *                   widget rather than a mark on the page.
 *  5. marker hit   — an explicit glyph the reader aimed at. Ranked above ink
 *                   because it is a small drawn target, where a stroke hit is
 *                   a slop-radius guess.
 *  6. stroke hit   — the ink itself; opens its chip.
 *  7. zone         — the page underneath: bottom band toggles the menu, the
 *                   outer edge bands turn, the middle is inert. See
 *                   [PAGE_TURN_BAND_FRACTION].
 *
 * [buttonHit], [markerHit] and [strokeHit] are passed in rather than computed
 * here because each transport hit-tests differently — on the finger path the
 * child composables consume those taps themselves and all three arrive null.
 */
internal fun resolveTap(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    menuBandPx: Float,
    menuOpen: Boolean,
    chipOpen: Boolean,
    /** Whether a text selection (and its chip) is showing. */
    selectionActive: Boolean = false,
    /** Block index of a page button under the contact, if any. */
    buttonHit: Int? = null,
    markerHit: Annotation? = null,
    strokeHit: InkStroke? = null,
): TapAction = when {
    menuOpen -> TapAction.CloseMenu
    chipOpen -> TapAction.DismissChip
    selectionActive -> TapAction.ClearSelection
    buttonHit != null -> TapAction.PressButton(buttonHit)
    markerHit != null -> TapAction.OpenMarker(markerHit)
    strokeHit != null -> TapAction.ShowStrokeChip(strokeHit)
    else -> when (tapZoneFor(x, y, width, height, menuBandPx)) {
        TapZone.MENU -> TapAction.ToggleMenu
        TapZone.PREV -> TapAction.PrevPage
        TapZone.NEXT -> TapAction.NextPage
        TapZone.NONE -> TapAction.Ignore
    }
}

/**
 * What one finger DRAG means.
 *
 * A drag is the one contact the tap ladder cannot describe, because it has no
 * position — only a direction. It gets the same treatment anyway: one pure
 * function, resolved by both transports, so the two cannot drift.
 *
 * The first three rungs are shared with [resolveTap] on purpose. Whatever is
 * open over the page swallows the contact whatever shape it had — dragging
 * across an open menu means "put this away", not "turn two pages under it".
 *
 * Axes: horizontal turns pages the way a paper book does (drag left to go
 * forward, following the page you are pulling across). Vertical changes
 * chapter, which is why its threshold is higher — a vertical wobble during a
 * horizontal drag is common, and a mis-read one costs you the chapter.
 */
internal fun resolveSwipe(
    dx: Float,
    dy: Float,
    horizontalThresholdPx: Float,
    verticalThresholdPx: Float,
    menuOpen: Boolean,
    chipOpen: Boolean,
    selectionActive: Boolean = false,
): TapAction = when {
    menuOpen -> TapAction.CloseMenu
    chipOpen -> TapAction.DismissChip
    selectionActive -> TapAction.ClearSelection
    kotlin.math.abs(dx) >= kotlin.math.abs(dy) -> when {
        dx <= -horizontalThresholdPx -> TapAction.NextPage
        dx >= horizontalThresholdPx -> TapAction.PrevPage
        else -> TapAction.Ignore
    }
    dy <= -verticalThresholdPx -> TapAction.NextChapter
    dy >= verticalThresholdPx -> TapAction.PrevChapter
    else -> TapAction.Ignore
}
