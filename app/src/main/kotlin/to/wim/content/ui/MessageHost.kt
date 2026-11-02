package to.wim.content.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/** Where the message showing is (in the window), so a tap on it isn't a tap elsewhere. */
class MessageArea {
    internal var bounds: Rect? = null
    internal var root: LayoutCoordinates? = null
}

/** The screens' [MessageArea], provided by the navigation. */
val LocalMessageArea = staticCompositionLocalOf<MessageArea?> { null }

/** A screen's messages (a snackbar host), which say where they are while one shows. */
@Composable
fun MessageHost(
    state: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val area = LocalMessageArea.current
    SnackbarHost(
        state,
        modifier.onGloballyPositioned { if (state.currentSnackbarData != null) area?.bounds = it.boundsInWindow() },
    )
}

/**
 * A tap anywhere outside the message showing (not a scroll or a drag) calls [onTapOutside], which
 * closes it (2026-11-02). Looks at touches before the screen does and leaves them to it, so the
 * tap still does what it does.
 */
fun Modifier.closesMessageOnTap(
    area: MessageArea,
    onTapOutside: () -> Unit,
): Modifier =
    onGloballyPositioned { area.root = it }
        .pointerInput(area) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val at = area.root?.takeIf { it.isAttached }?.localToWindow(down.position)
                if (at != null && area.bounds?.contains(at) == true) return@awaitEachGesture
                var moved = false
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                    if (!change.pressed) {
                        if (!moved) onTapOutside()
                        break
                    }
                }
            }
        }
