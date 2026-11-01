package to.wim.content.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager

/** The pages open in one sheet, over its own content: the last one shows. */
private class SheetPages {
    val pages = mutableStateListOf<SheetPage>()
}

/** A sheet opened from within a sheet: its content and what Back does (the same as closing it). */
private class SheetPage {
    var onBack by mutableStateOf({})
    var content by mutableStateOf<@Composable () -> Unit>({})
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
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        CompositionLocalProvider(LocalSheetPages provides pages) {
            BackHandler(enabled = pages.pages.isNotEmpty()) { pages.pages.lastOrNull()?.onBack?.invoke() }
            // Every page stays composed (each page is declared inside the one under it), but only the
            // last is measured and shown.
            Layout(
                content = {
                    key("sheet") { Box { content() } }
                    pages.pages.forEach { page ->
                        key(page) {
                            CompositionLocalProvider(LocalInSheetPage provides true) { Box { page.content() } }
                        }
                    }
                },
                modifier = Modifier.animateContentSize(),
            ) { measurables, constraints ->
                val shown = measurables.last().measure(constraints)
                layout(shown.width, shown.height) { shown.place(0, 0) }
            }
        }
    }
}
