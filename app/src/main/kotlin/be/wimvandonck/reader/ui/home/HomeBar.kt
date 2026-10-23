package be.wimvandonck.reader.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R

/**
 * The title bar as a large search bar (#189): ☰ at its start, then "Search articles", which opens
 * Search, and [trailing] buttons at its end.
 */
@Composable
fun HomeSearchBar(
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(min = 56.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSearch),
    ) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenu) {
                Icon(painterResource(R.drawable.ic_menu), contentDescription = stringResource(R.string.action_open_menu))
            }
            Text(
                stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            trailing()
            Icon(
                painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

/**
 * The state chip at the start of the chip row (#205): what of the place is shown, Unread or All in
 * Feeds, To read, Archive or All in Saved. A menu picks another; it is lit while it leaves things out.
 */
@Composable
fun StateChip(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    /** Whether the chosen state leaves things out (everything but All). */
    filtering: Boolean,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = filtering,
            onClick = { open = true },
            label = { Text(options[selected], maxLines = 1) },
            trailingIcon = { Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { RadioButton(selected = index == selected, onClick = null) },
                    onClick = {
                        open = false
                        if (index != selected) onSelect(index)
                    },
                )
            }
        }
    }
}

/**
 * The chip row under the search bar: the [state] chip, then the groups as chips, one at a time
 * (folders in Feeds, tags in Saved): tapping the chosen one again lets go. While a group or another
 * filter is on, an X at the end clears it.
 */
@Composable
fun TagChips(
    tags: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    filtered: Boolean,
    onClear: () -> Unit,
    state: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state()
        if (tags.isNotEmpty()) VerticalDivider(Modifier.height(24.dp))
        tags.forEach { tag ->
            val on = selected != null && tag.equals(selected, ignoreCase = true)
            FilterChip(
                selected = on,
                onClick = { onSelect(if (on) null else tag) },
                label = { Text(tag) },
                leadingIcon =
                    if (on) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
            )
        }
        if (filtered) {
            IconButton(onClick = onClear) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_clear))
            }
        }
    }
}
