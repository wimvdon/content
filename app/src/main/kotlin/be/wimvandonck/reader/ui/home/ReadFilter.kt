package be.wimvandonck.reader.ui.home

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import be.wimvandonck.reader.R

/**
 * Unread only, as one Material 3 filter chip in the top app bar: selected (with a check) shows only
 * unread articles, unselected shows all of them. Small, so the list gets the room.
 */
@Composable
fun ReadFilter(
    unreadOnly: Boolean,
    onUnreadOnlyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = unreadOnly,
        onClick = { onUnreadOnlyChange(!unreadOnly) },
        label = { Text(stringResource(R.string.filter_unread)) },
        leadingIcon =
            if (unreadOnly) {
                { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            } else {
                null
            },
        modifier = modifier,
    )
}
