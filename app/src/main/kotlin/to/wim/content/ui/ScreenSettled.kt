package to.wim.content.ui

import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the screen around this has finished moving in (and isn't moving out): false while it
 * slides and fades between screens. An embedded player's web view waits for it: a WebView inside
 * the fading screen draws black, so an article with players flashed black as it opened.
 */
val LocalScreenSettled = compositionLocalOf { true }

object ScreenSettled {
    /** Settled once the screen's own transition is fully in, and not on its way out. */
    fun of(
        current: EnterExitState,
        target: EnterExitState,
    ): Boolean = current == EnterExitState.Visible && target == EnterExitState.Visible
}
