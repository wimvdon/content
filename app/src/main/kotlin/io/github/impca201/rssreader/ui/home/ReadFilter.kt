package io.github.impca201.rssreader.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.impca201.rssreader.R

/**
 * Unread / All as Material 3's connected button group: two halves of one pill, the selected one
 * filled, fully rounded and checked. (Material 3 1.4 doesn't ship the component yet.)
 */
@Composable
fun ReadFilter(
    unreadOnly: Boolean,
    onUnreadOnlyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Both halves always take half the width, so switching never moves anything around.
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Segment(stringResource(R.string.filter_unread), selected = unreadOnly, first = true, Modifier.weight(1f)) { onUnreadOnlyChange(true) }
        Segment(stringResource(R.string.filter_all), selected = !unreadOnly, first = false, Modifier.weight(1f)) { onUnreadOnlyChange(false) }
    }
}

@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    first: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // The outer side is always round; the inner side rounds fully when selected.
    val inner by animateDpAsState(if (selected) HEIGHT / 2 else INNER_CORNER, spring(stiffness = Spring.StiffnessMediumLow), label = "corner")
    val outer = HEIGHT / 2
    val shape =
        if (first) {
            RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner)
        } else {
            RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer)
        }
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        spring(stiffness = Spring.StiffnessMedium),
        label = "container",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        spring(stiffness = Spring.StiffnessMedium),
        label = "content",
    )
    Surface(
        shape = shape,
        color = container,
        contentColor = content,
        modifier =
            modifier
                .heightIn(min = HEIGHT)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                selected,
                enter = expandHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(spring(stiffness = Spring.StiffnessMedium)),
                exit = shrinkHorizontally(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(spring(stiffness = Spring.StiffnessMedium)),
            ) {
                Row {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private val HEIGHT = 48.dp
private val INNER_CORNER = 8.dp
