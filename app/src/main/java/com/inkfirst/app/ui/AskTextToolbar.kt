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
) : TextToolbar {

    /** When non-null, a selection is active and the Reader should show the Ask chip. */
    var selectionRect: Rect? by mutableStateOf(null)
        private set

    private var copyCallback: (() -> Unit)? = null

    override val status: TextToolbarStatus
        get() = if (selectionRect != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun hide() {
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
        selectionRect = rect
        copyCallback = onCopyRequested
    }

    /** User tapped Ask — copy selection to clipboard, read it back, dispatch. */
    fun ask() {
        val copy = copyCallback ?: return
        copy()
        val text = clipboard.getText()?.text?.takeIf { it.isNotBlank() }
        if (text != null) onAsk(text)
        hide()
    }
}
