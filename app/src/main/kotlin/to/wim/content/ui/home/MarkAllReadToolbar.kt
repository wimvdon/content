package to.wim.content.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import to.wim.content.R
import kotlin.math.roundToInt

/**
 * The bottom of Feeds, Saved and the Inbox (2026-11-07, the owner's choice from Material-only
 * mockups): + is the screen's FAB; in Feeds and the Inbox, while something is unread, Mark all as
 * read sits beside it as Material 3's floating toolbar holding one text button (✓✓ and its words,
 * owner's wish), the pair in the middle. With nothing to mark the toolbar goes (at once, no animation
 * of its own) and + glides to its corner; when there is something again, + glides back beside it.
 * Fills the width it is given (the Scaffold's centred FAB slot) and lets touches beside the buttons
 * through to the list.
 */
@Composable
fun MarkAllReadToolbar(
    shown: Boolean,
    onMarkAllRead: () -> Unit,
    add: @Composable () -> Unit,
) {
    // Where + is: 0 beside the toolbar in the middle, 1 in its corner.
    val toCorner by animateFloatAsState(if (shown) 0f else 1f, label = "fab")
    Layout(
        content = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
            ) {
                TextButton(
                    onClick = onMarkAllRead,
                    enabled = shown,
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
                    modifier = Modifier.padding(8.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_done_all), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.action_mark_all_read), modifier = Modifier.padding(start = 8.dp))
                }
            }
            add()
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bar = measurables[0].measure(loose)
        val fab = measurables[1].measure(loose)
        val width = constraints.maxWidth
        val height = maxOf(bar.height, fab.height)
        val gap = 8.dp.roundToPx()
        // The pair centred; + at its end, or in the corner.
        val pairStart = (width - (bar.width + gap + fab.width)) / 2
        val fabBeside = pairStart + bar.width + gap
        val fabInCorner = width - fab.width
        layout(width, height) {
            // Not placed while hidden: not drawn and not tappable.
            if (shown) bar.place(pairStart, (height - bar.height) / 2)
            fab.place((fabBeside + (fabInCorner - fabBeside) * toCorner).roundToInt(), (height - fab.height) / 2)
        }
    }
}
