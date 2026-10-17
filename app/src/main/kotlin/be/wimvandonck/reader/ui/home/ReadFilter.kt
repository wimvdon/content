package be.wimvandonck.reader.ui.home

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R

/**
 * Unread / All as Material 3's connected button group, at the normal button height: two halves of one pill, the selected one
 * filled, fully rounded and checked. (Material 3 1.4 doesn't ship the component yet.)
 */
@Composable
fun ReadFilter(
    unreadOnly: Boolean,
    onUnreadOnlyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Normal button size. Both halves are as wide as the widest one with its check mark, so
    // switching never moves anything around.
    Row(
        modifier = modifier.width(IntrinsicSize.Max).selectableGroup(),
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
    // Surface's selectable overload keeps a 48dp touch target around the 40dp button.
    Surface(
        selected = selected,
        onClick = onClick,
        shape = shape,
        color = container,
        contentColor = content,
        modifier =
            modifier
                .heightIn(min = HEIGHT)
                .semantics { role = Role.RadioButton },
    ) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            // Invisible: reserves the width of the label with its check mark.
            Row(Modifier.alpha(0f).clearAndSetSemantics {}) {
                Spacer(Modifier.width(CHECK_SPACE))
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            Row(
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
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

private val HEIGHT = 40.dp
private val CHECK_SPACE = 26.dp
private val INNER_CORNER = 8.dp
