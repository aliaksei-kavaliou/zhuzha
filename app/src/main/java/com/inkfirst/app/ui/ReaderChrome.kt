package com.inkfirst.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the reader currently has something open OVER the page.
 *
 * The system bars are owned by MainActivity, keyed on the route — a single
 * owner, because when ReaderScreen owned them they came back on every
 * chapter-to-chapter move (Compose composes the incoming screen before
 * disposing the outgoing one, so the new screen hid the bar and the old
 * screen's onDispose showed it again). That rule stands. This is how the
 * reader REPORTS state to the owner without taking the decision back.
 *
 * A plain object rather than a CompositionLocal for the same reason
 * [PageKeys] is one: the reader and the Activity are not in the same subtree,
 * and threading a provider through the whole nav graph to carry one boolean
 * buys nothing.
 */
object ReaderChrome {
    /**
     * True while the menu sheet is up.
     *
     * Opening the menu means you have stopped reading and started operating
     * the app, which is exactly when the clock and the battery are worth
     * seeing — the convention every reader app follows. It is only affordable
     * because the window is edge-to-edge: the bar paints OVER the page
     * instead of shrinking it, so the chapter does not repaginate under an
     * open menu. Measured before this existed: showing it the other way took
     * chapter 1 from 20 pages to 22, and back again on close.
     */
    var menuOpen by mutableStateOf(false)
        internal set
}
