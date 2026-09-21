package com.inkfirst.app.ui

import com.inkfirst.core.Annotation
import com.inkfirst.core.Stroke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The tap rules lived in four places once and drifted apart, costing three
 * bugs in a single session. They live in one place now, and these are the
 * rungs of the ladder pinned down so the next edit has to break a test rather
 * than a page turn.
 */
class TapRoutingTest {

    private val W = 1000f
    private val H = 2000f
    private val MENU = 100f  // bottom band

    private fun tap(
        x: Float,
        y: Float = 500f,
        menuOpen: Boolean = false,
        chipOpen: Boolean = false,
        selectionActive: Boolean = false,
        buttonHit: Int? = null,
        markerHit: Annotation? = null,
        strokeHit: Stroke? = null,
    ) = resolveTap(
        x, y, W, H, MENU,
        menuOpen = menuOpen,
        chipOpen = chipOpen,
        selectionActive = selectionActive,
        buttonHit = buttonHit,
        markerHit = markerHit,
        strokeHit = strokeHit,
    )

    private fun swipe(
        dx: Float = 0f,
        dy: Float = 0f,
        menuOpen: Boolean = false,
        chipOpen: Boolean = false,
        selectionActive: Boolean = false,
    ) = resolveSwipe(
        dx, dy,
        horizontalThresholdPx = 60f,
        verticalThresholdPx = 80f,
        menuOpen = menuOpen,
        chipOpen = chipOpen,
        selectionActive = selectionActive,
    )

    private fun annotation() = Annotation(
        chapterSlug = "ch1",
        blockIndex = 0,
        quote = "q",
        note = "n",
        created = "2026-09-18",
    )

    private fun stroke() = Stroke(blockIndex = 0, points = emptyList(), created = "2026-09-18")

    // --- zones -----------------------------------------------------------

    @Test
    fun `outer left band goes back`() {
        assertEquals(TapAction.PrevPage, tap(x = 10f))
        assertEquals(TapAction.PrevPage, tap(x = W * 0.24f))
    }

    @Test
    fun `outer right band goes forward`() {
        assertEquals(TapAction.NextPage, tap(x = W - 10f))
        assertEquals(TapAction.NextPage, tap(x = W * 0.76f))
    }

    @Test
    fun `the middle of the page is inert`() {
        // Half the page answers to nothing — the point of narrowing the bands.
        assertEquals(TapAction.Ignore, tap(x = W * 0.26f))
        assertEquals(TapAction.Ignore, tap(x = W / 2f))
        assertEquals(TapAction.Ignore, tap(x = W * 0.74f))
    }

    @Test
    fun `the bottom band opens the menu only between the turn bands`() {
        // The middle, which is inert everywhere else, is the menu's target
        // here — the panel opens from the bottom, so that is where you reach.
        val y = H - MENU / 2f
        assertEquals(TapAction.ToggleMenu, tap(x = W / 2f, y = y))
    }

    @Test
    fun `a bottom corner turns the page`() {
        // The two bands used to overlap here and the menu won, so the corner
        // a thumb naturally lands on opened the format menu instead of
        // turning the page. A corner belongs to the band it is a corner of.
        val y = H - MENU / 2f
        assertEquals(TapAction.PrevPage, tap(x = 10f, y = y))
        assertEquals(TapAction.NextPage, tap(x = W - 10f, y = y))
        // The very last pixel row included: that is where "bottom" is aimed.
        assertEquals(TapAction.NextPage, tap(x = W - 1f, y = H - 1f))
    }

    @Test
    fun `the footer line is all band, so only x decides there`() {
        // The page-number row passes its OWN height as the band, which makes
        // every y in it "bottom". It used to toggle the menu at any x, so the
        // bottom of the page said two different things one pixel apart: the
        // band above turned the page at its corners, the line below opened
        // the menu there.
        val footerH = 60f
        fun footer(x: Float) = resolveTap(
            x, footerH / 2f, W, footerH, footerH,
            menuOpen = false, chipOpen = false,
        )
        assertEquals(TapAction.PrevPage, footer(10f))
        assertEquals(TapAction.ToggleMenu, footer(W / 2f))
        assertEquals(TapAction.NextPage, footer(W - 10f))
    }

    @Test
    fun `a page button outranks every zone and every mark`() {
        // On the pen path the SDK owns the touch stream and a button's own
        // click never runs, so "Show answer" sitting in a turn band simply
        // turned the page.
        assertEquals(TapAction.PressButton(7), tap(x = 10f, buttonHit = 7))
        assertEquals(TapAction.PressButton(7), tap(x = W / 2f, buttonHit = 7, strokeHit = stroke()))
        assertEquals(
            TapAction.PressButton(7),
            tap(x = W / 2f, buttonHit = 7, markerHit = annotation()),
        )
        // But not what is open over it.
        assertEquals(TapAction.CloseMenu, tap(x = 10f, buttonHit = 7, menuOpen = true))
    }

    // --- drags -----------------------------------------------------------

    @Test
    fun `a horizontal drag turns the page, following the paper`() {
        assertEquals(TapAction.NextPage, swipe(dx = -120f))
        assertEquals(TapAction.PrevPage, swipe(dx = 120f))
    }

    @Test
    fun `a vertical drag changes chapter`() {
        assertEquals(TapAction.NextChapter, swipe(dy = -120f))
        assertEquals(TapAction.PrevChapter, swipe(dy = 120f))
    }

    @Test
    fun `the dominant axis decides`() {
        // A page turn with a wobble in it is still a page turn, not a
        // chapter change.
        assertEquals(TapAction.NextPage, swipe(dx = -200f, dy = -100f))
        assertEquals(TapAction.NextChapter, swipe(dx = -40f, dy = -200f))
    }

    @Test
    fun `a drag too short to mean anything means nothing`() {
        assertEquals(TapAction.Ignore, swipe(dx = -40f))
        // Vertical asks for more, because a stray vertical wobble is commoner
        // and reading one as a chapter change costs the reader the book.
        assertEquals(TapAction.Ignore, swipe(dy = -70f))
    }

    @Test
    fun `what is open over the page swallows a drag too`() {
        assertEquals(TapAction.CloseMenu, swipe(dx = -200f, menuOpen = true))
        assertEquals(TapAction.DismissChip, swipe(dy = -200f, chipOpen = true))
        assertEquals(TapAction.ClearSelection, swipe(dx = 200f, selectionActive = true))
    }

    // --- ladder ----------------------------------------------------------

    @Test
    fun `an open menu swallows every tap`() {
        // The panel covers the band that opened it, so if this rung is not
        // first the menu cannot be closed at all on the pen path.
        assertEquals(TapAction.CloseMenu, tap(x = 10f, menuOpen = true))
        assertEquals(TapAction.CloseMenu, tap(x = W / 2f, menuOpen = true))
        assertEquals(TapAction.CloseMenu, tap(x = W - 10f, menuOpen = true))
        assertEquals(
            TapAction.CloseMenu,
            tap(x = W / 2f, y = H - 10f, menuOpen = true),
        )
        // Outranks ink, which is the bug this ordering was written for.
        assertEquals(
            TapAction.CloseMenu,
            tap(x = W / 2f, menuOpen = true, strokeHit = stroke()),
        )
    }

    @Test
    fun `an open chip is dismissed before the page turns`() {
        // Turning the page instead meant the only way to cancel a chip was to
        // leave the page it belonged to.
        assertEquals(TapAction.DismissChip, tap(x = 10f, chipOpen = true))
        assertEquals(TapAction.DismissChip, tap(x = W - 10f, chipOpen = true))
        assertEquals(
            TapAction.DismissChip,
            tap(x = W / 2f, chipOpen = true, strokeHit = stroke()),
        )
    }

    @Test
    fun `a menu outranks a chip`() {
        assertEquals(
            TapAction.CloseMenu,
            tap(x = W / 2f, menuOpen = true, chipOpen = true),
        )
    }

    @Test
    fun `a marker is a target even in the dead middle`() {
        val a = annotation()
        assertEquals(TapAction.OpenMarker(a), tap(x = W / 2f, markerHit = a))
        // And it outranks ink: a drawn glyph beats a slop-radius guess.
        assertEquals(
            TapAction.OpenMarker(a),
            tap(x = W / 2f, markerHit = a, strokeHit = stroke()),
        )
    }

    @Test
    fun `a stroke opens its chip, anywhere on the page`() {
        val s = stroke()
        assertEquals(TapAction.ShowStrokeChip(s), tap(x = W / 2f, strokeHit = s))
        // Including inside a turn band — ink you can see beats a zone you cannot.
        assertEquals(TapAction.ShowStrokeChip(s), tap(x = 10f, strokeHit = s))
    }

    @Test
    fun `a live text selection is dismissed before the page turns`() {
        // Both halves matter. Turning the page as well would drop the
        // selection AND lose the reader's place; doing nothing in the dead
        // middle would leave no way to dismiss it by tapping at all.
        assertEquals(TapAction.ClearSelection, tap(x = W / 2f, selectionActive = true))
        assertEquals(TapAction.ClearSelection, tap(x = 10f, selectionActive = true))
        assertEquals(TapAction.ClearSelection, tap(x = W - 10f, selectionActive = true))
    }

    @Test
    fun `a menu and a stroke chip both outrank a selection`() {
        assertEquals(
            TapAction.CloseMenu,
            tap(x = W / 2f, menuOpen = true, selectionActive = true),
        )
        assertEquals(
            TapAction.DismissChip,
            tap(x = W / 2f, chipOpen = true, selectionActive = true),
        )
    }

    @Test
    fun `the dead middle is inert only when nothing is up`() {
        assertEquals(TapAction.Ignore, tap(x = W / 2f))
    }
}
