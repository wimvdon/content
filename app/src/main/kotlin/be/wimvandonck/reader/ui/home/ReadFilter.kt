package be.wimvandonck.reader.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R

/**
 * Unread only, as a Material 3 filter chip in a chip row under the folder tabs, where Material puts
 * chips that filter the content below them (like the tag chips on Instapaper's Tags tab). Selected
 * (with a check) shows only unread articles, unselected shows all of them.
 */
@Composable
fun ReadFilter(
    unreadOnly: Boolean,
    onUnreadOnlyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        )
    }
}
