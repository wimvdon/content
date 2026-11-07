package to.wim.content.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.ui.reader.Tipped

/**
 * The bottom of Feeds, Saved and the Inbox (2026-11-07, the owner's choice from Material-only
 * mockups): + is the screen's FAB; in Feeds and the Inbox, while something is unread, Mark all as
 * read sits beside it as Material 3's floating toolbar with one icon button (✓✓, its name in a
 * tooltip on a long press), the pair in the middle. With nothing to mark the toolbar slides away and
 * + moves to its corner, animated. Fills the width it is given (the Scaffold's centred FAB slot) and
 * lets touches beside the buttons through to the list.
 */
@Composable
fun MarkAllReadToolbar(
    shown: Boolean,
    onMarkAllRead: () -> Unit,
    add: @Composable () -> Unit,
) {
    // 0: in the middle, 1: at the end.
    val bias by animateFloatAsState(if (shown) 0f else 1f, label = "fab")
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(BiasAlignment(bias, 0f)),
        ) {
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp,
                ) {
                    val label = stringResource(R.string.action_mark_all_read)
                    Tipped(label) {
                        IconButton(onClick = onMarkAllRead, modifier = Modifier.padding(8.dp)) {
                            Icon(painterResource(R.drawable.ic_done_all), contentDescription = label)
                        }
                    }
                }
            }
            add()
        }
    }
}
