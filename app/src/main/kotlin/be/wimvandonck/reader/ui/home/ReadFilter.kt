package be.wimvandonck.reader.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R

/**
 * Unread or All: two Material 3 filter chips used as a single choice, in a chip row under the folder
 * tabs, where Material puts chips that filter the content below them (like the chips in Search and
 * on Saved's Tags tab). The chosen one has a check, so the list shown is clear at a glance.
 */
@Composable
fun ReadFilter(
    unreadOnly: Boolean,
    onUnreadOnlyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(true to R.string.filter_unread, false to R.string.filter_all).forEach { (unread, label) ->
            val selected = unreadOnly == unread
            FilterChip(
                selected = selected,
                onClick = { if (!selected) onUnreadOnlyChange(unread) },
                label = { Text(stringResource(label)) },
                leadingIcon =
                    if (selected) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
}
