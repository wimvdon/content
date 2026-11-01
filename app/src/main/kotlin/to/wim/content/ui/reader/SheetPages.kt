package to.wim.content.ui.reader

import androidx.activity.compose.BackHandler
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.launch

/** The pages open in one sheet, over its own content: the last one shows. */
private class SheetPages {
    val pages = mutableStateListOf<SheetPage>()
}

/** A sheet opened from within a sheet: its content and what Back does (the same as closing it). */
private class SheetPage {
    var onBack by mutableStateOf({})
    var content by mutableStateOf<@Composable () -> Unit>({})
}

/** Whether the keyboard is open in the sheet, and how to close it. */
private class SheetKeyboard {
    var open = false
    var hide: () -> Unit = {}
}

private val LocalSheetPages = staticCompositionLocalOf<SheetPages?> { null }

/** True inside a page of a sheet: [SheetHeader] shows a back arrow instead of Cancel or Done. */
internal val LocalInSheetPage = staticCompositionLocalOf { false }

/**
 * A bottom sheet, the way every sheet in the app opens (Material: one sheet, never one over another).
 * Opened from within another sheet (Edit topics in Add feed, a feed in Manage feeds, New topic in
 * Manage topics), it doesn't open a second sheet: it becomes a page of that sheet, with a back arrow
 * in its header. Back (the arrow or the phone's back gesture) closes the page and shows the sheet
 * under it as it was, with what was typed there; its state lives where it was declared, so nothing
 * is lost while it is hidden.
 *
 * The sheet stops below the status bar, and it only closes on a swipe, a tap beside it or Back once
 * it has settled ([SheetDismissGuard]): with the keyboard open, those close the keyboard first.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ContentSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val host = LocalSheetPages.current
    if (host != null) {
        val page = remember { SheetPage() }
        page.onBack = onDismiss
        page.content = content
        // Declared inside the sheet, so this is the sheet's focus: a field on the page going out of
        // sight lets go of the keyboard (the new page may then take it).
        val focus = LocalFocusManager.current
        DisposableEffect(host, page) {
            focus.clearFocus()
            host.pages.add(page)
            onDispose {
                focus.clearFocus()
                host.pages.remove(page)
            }
        }
        return
    }
    val pages = remember { SheetPages() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val guard = remember { SheetDismissGuard() }
    // Read inside the sheet (its own window has the keyboard), used by the close below.
    val keyboard = remember { SheetKeyboard() }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    ModalBottomSheet(
        // A swipe, a tap beside the sheet or Back: only when the sheet has settled (SheetDismissGuard);
        // otherwise the keyboard closes and the sheet comes back up.
        onDismissRequest = {
            if (guard.allowsDismiss(SystemClock.uptimeMillis(), keyboard.open)) {
                currentOnDismiss()
            } else {
                keyboard.hide()
                scope.launch { sheetState.show() }
            }
        },
        sheetState = sheetState,
        // Never under the status bar: a tall sheet stops just below it, so it doesn't jump around
        // the status bar's padding while it grows or is dragged.
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        CompositionLocalProvider(LocalSheetPages provides pages) {
            val imeOpen = WindowInsets.isImeVisible
            val focus = LocalFocusManager.current
            val softKeyboard = LocalSoftwareKeyboardController.current
            SideEffect {
                keyboard.open = imeOpen
                keyboard.hide = {
                    focus.clearFocus()
                    softKeyboard?.hide()
                }
            }
            LaunchedEffect(imeOpen, pages.pages.size) { guard.changed(SystemClock.uptimeMillis()) }
            BackHandler(enabled = pages.pages.isNotEmpty()) { pages.pages.lastOrNull()?.onBack?.invoke() }
            // Back with the keyboard open closes the keyboard, not the sheet or its page.
            BackHandler(enabled = imeOpen) { keyboard.hide() }
            // Every page stays composed (each page is declared inside the one under it), but only the
            // last is measured and shown. The sheet takes the new page's height at once: animating it
            // fought the sheet's own placement and made it jump.
            Layout(
                content = {
                    key("sheet") { Box { content() } }
                    pages.pages.forEach { page ->
                        key(page) {
                            CompositionLocalProvider(LocalInSheetPage provides true) { Box { page.content() } }
                        }
                    }
                },
            ) { measurables, constraints ->
                val shown = measurables.last().measure(constraints)
                layout(shown.width, shown.height) { shown.place(0, 0) }
            }
        }
    }
}
