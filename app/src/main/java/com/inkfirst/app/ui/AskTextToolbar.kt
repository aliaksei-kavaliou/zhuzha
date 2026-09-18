package com.inkfirst.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * Replaces Compose's default text-selection toolbar (the system Copy/Paste
 * popup) with our own "Ask" affordance. When SelectionContainer's selection
 * changes and Compose wants to show its toolbar, we intercept — the Reader
 * observes [selectionRect] and renders a book-styled Ask button instead of
 * the platform floating action mode.
 *
 * Getting the selected text out of Compose is trickier than it sounds:
 * TextToolbar's API gives us the copy callback but not the selection string.
 * The workaround: call the copy callback (which puts the selection on the
 * clipboard), then read the clipboard. Not elegant, but reliable across
 * Compose versions and doesn't rely on internal APIs.
 */
class AskTextToolbar(
    private val clipboard: ClipboardManager,
    private val onAsk: (String) -> Unit,
    private val onAddNote: (String) -> Unit,
) : TextToolbar {

    /** When non-null, a selection is active and the Reader should show the selection chips. */
    var selectionRect: Rect? by mutableStateOf(null)
        private set

    private var copyCallback: (() -> Unit)? = null

    /**
     * The selection that was dismissed, held until a *different* one arrives.
     *
     * Hiding the toolbar isn't enough to make the chip go away: the selection
     * itself is still live inside SelectionContainer, so Compose re-reports it
     * and showMenu puts the chip straight back — Cancel appeared to do
     * nothing. There is no public API to clear a SelectionContainer's
     * selection from here, so the same rect is ignored until the user selects
     * something else.
     *
     * Only needed on the path where the selection SURVIVES the dismissal —
     * Compose calling [hide] on its own. When the reader dismisses on purpose
     * the caller destroys the selection outright and uses [dismissForRebuild],
     * which deliberately remembers nothing.
     *
     * Trade-off, on this path only: re-selecting the exact same range, with a
     * pixel-identical rect and no other selection in between, won't re-open
     * the chip. Selecting anything else first clears the block.
     */
    private var dismissedRect: Rect? = null

    override val status: TextToolbarStatus
        get() = if (selectionRect != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun hide() {
        dismissedRect = selectionRect
        selectionRect = null
        copyCallback = null
    }

    /**
     * Dismiss because the selection itself is being destroyed — the caller is
     * about to rebuild the SelectionContainer around it.
     *
     * Unlike [hide], this remembers nothing. Suppression exists to stop a
     * still-live selection from re-reporting itself and putting the chip
     * straight back, and after a rebuild nothing is live. Remembering anyway
     * had a visible cost: selecting the same passage again on purpose got no
     * chip, and because selectionRect stayed null the next tap read as a page
     * turn instead of dismissing the selection.
     */
    fun dismissForRebuild() {
        selectionRect = null
        copyCallback = null
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        if (rect == dismissedRect) return
        dismissedRect = null
        selectionRect = rect
        copyCallback = onCopyRequested
    }

    /** User tapped Ask — copy selection to clipboard, read it back, dispatch. */
    fun ask() = dispatch(onAsk)

    /** User tapped Add note — same selection-fetch trick as ask(). */
    fun addNote() = dispatch(onAddNote)

    private fun dispatch(action: (String) -> Unit) {
        val copy = copyCallback ?: return
        copy()
        val text = clipboard.getText()?.text?.takeIf { it.isNotBlank() }
        if (text != null) action(text)
        hide()
    }
}
