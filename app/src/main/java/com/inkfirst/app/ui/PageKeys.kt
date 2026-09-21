package com.inkfirst.app.ui

/**
 * The Boox side buttons, routed around Compose focus.
 *
 * Onyx maps the two page-turn keys to VolumeUp / VolumeDown, so whoever is
 * showing pages has to CONSUME them — anything less and Android puts its
 * volume slider on the screen, which on e-ink is a full repaint over the
 * sentence you were reading.
 *
 * Consuming them used to be a Compose `onKeyEvent` on a focusable node in the
 * reader, and that only works while something in that node's subtree holds
 * focus. On the pen path it stopped being true: the SDK's SurfaceView is a
 * real Android view inside the composition, every TextButton on the page can
 * take focus, and the selection container is rebuilt from scratch whenever a
 * selection is dropped. Any one of those leaves the key handler out of the
 * focus chain, and the buttons silently become volume keys again.
 *
 * So the keys are taken at the Activity's door instead, where focus is not
 * part of the question, and dispatched to whichever screen last said it wants
 * them. A stack rather than a single slot because screens nest: the Ask panel
 * opens over the reader and both are in composition, and the one on top is
 * the one the buttons belong to.
 */
internal object PageKeys {

    private val handlers = mutableListOf<(Boolean) -> Unit>()

    /**
     * Claim the page keys. `true` means forward — VolumeDown, PageDown, the
     * lower side button.
     *
     * Returns the function that gives them back; call it from `onDispose`.
     */
    fun register(handler: (forward: Boolean) -> Unit): () -> Unit {
        handlers += handler
        return { handlers -= handler }
    }

    /** Returns whether anyone took it — the caller consumes the key if so. */
    fun dispatch(forward: Boolean): Boolean {
        val handler = handlers.lastOrNull() ?: return false
        handler(forward)
        return true
    }

    /** Whether anything at all wants the keys right now. */
    fun claimed(): Boolean = handlers.isNotEmpty()
}
