package to.wim.content.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import to.wim.content.R

/**
 * Mark all as read is the screen's FAB in Feeds and the Inbox (2026-11-07, owner's choice from
 * mockups; + moved to the search bar): Material 3's extended FAB, ✓✓ and its words, in its corner.
 * While scrolling down (the header slid away) it shrinks to ✓✓ alone, and grows back when scrolling
 * up. With nothing to mark it goes.
 */
@Composable
fun MarkAllReadFab(
    shown: Boolean,
    expanded: Boolean,
    onMarkAllRead: () -> Unit,
) {
    AnimatedVisibility(
        visible = shown,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        ExtendedFloatingActionButton(
            onClick = onMarkAllRead,
            expanded = expanded,
            icon = { Icon(painterResource(R.drawable.ic_done_all), contentDescription = null) },
            text = { Text(stringResource(R.string.action_mark_all_read)) },
        )
    }
}
