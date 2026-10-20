package be.wimvandonck.reader.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.wimvandonck.reader.R
import be.wimvandonck.reader.data.Place
import be.wimvandonck.reader.data.SaveChoices

/**
 * The reader's bar for a saved article (#139, #156): two buttons, as the Organise sheet groups it.
 * **Folder** says where it is and opens To read and Archive, a divider, then the folders and New
 * folder; **Tags** says its tags (or Liked) and opens Like, a divider, then the tags and Add a tag.
 * New folder and Add a tag open Organise, the only place to type.
 */
@Composable
internal fun RowScope.SavedBar(item: ReaderItem) {
    val choice = SaveChoices.of(item.item)
    var placesOpen by remember { mutableStateOf(false) }
    var tagsOpen by remember { mutableStateOf(false) }
    val folderLabel = stringResource(R.string.folder_field)
    val tagsTitle = stringResource(R.string.tags_title)
    Box(Modifier.padding(start = 8.dp).weight(1f, fill = false)) {
        // Read out as "Folder: To read", a menu, not as an action called "To read".
        val placeSpoken = "$folderLabel: ${placeName(choice.place)}"
        TextButton(onClick = { placesOpen = true }, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = placeSpoken; role = Role.DropdownList }) {
            Icon(painterResource(placeIcon(choice.place)), contentDescription = null)
            Text(placeName(choice.place), modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = placesOpen, onDismissRequest = { placesOpen = false }) {
            SaveChoices.places(item.folders, choice.place).forEach { option ->
                MenuChoice(
                    icon = placeIcon(option),
                    text = placeName(option),
                    selected = SaveChoices.same(option, choice.place),
                ) {
                    placesOpen = false
                    if (!SaveChoices.same(option, choice.place)) item.onPlace(option)
                }
                // The folders after To read and Archive, as in the sheet.
                if (option == Place.Archive) HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folder_new)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                onClick = {
                    placesOpen = false
                    item.onOrganise()
                },
            )
        }
    }
    Spacer(Modifier.weight(1f))
    Box(Modifier.padding(end = 8.dp).weight(1f, fill = false)) {
        val liked = stringResource(R.string.reading_list_liked)
        val tagsSpoken = "$tagsTitle: ${SaveChoices.tagsLabel(choice, liked) ?: "–"}"
        TextButton(onClick = { tagsOpen = true }, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = tagsSpoken; role = Role.DropdownList }) {
            Icon(
                painterResource(if (choice.liked) R.drawable.ic_favorite_filled else R.drawable.ic_label),
                contentDescription = null,
                tint = if (choice.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                SaveChoices.tagsLabel(choice, liked) ?: stringResource(R.string.tags_title),
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = tagsOpen, onDismissRequest = { tagsOpen = false }) {
            // Like stays open on the menu, like the tags: several can change at once.
            MenuChoice(if (choice.liked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite, stringResource(R.string.action_like), choice.liked, item.onLike)
            HorizontalDivider()
            (item.tags + choice.tags.filterNot { t -> item.tags.any { it.equals(t, ignoreCase = true) } }).forEach { tag ->
                MenuChoice(R.drawable.ic_label, tag, choice.tags.any { it.equals(tag, ignoreCase = true) }) { item.onTag(tag) }
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tags_add)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                onClick = {
                    tagsOpen = false
                    item.onOrganise()
                },
            )
        }
    }
}

/** A menu line that is on or off: its icon, its name, and a check when chosen. */
@Composable
private fun MenuChoice(
    icon: Int,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        trailingIcon = if (selected) ({ Icon(painterResource(R.drawable.ic_check), contentDescription = null) }) else null,
        onClick = onClick,
        // The check is announced too.
        modifier = Modifier.semantics { this.selected = selected },
    )
}

private fun placeIcon(place: Place): Int =
    when (place) {
        Place.ToRead -> R.drawable.ic_reading_list
        Place.Archive -> R.drawable.ic_inventory
        is Place.Folder -> R.drawable.ic_folder
    }

@Composable
private fun placeName(place: Place): String =
    when (place) {
        Place.ToRead -> stringResource(R.string.reading_list_to_read)
        Place.Archive -> stringResource(R.string.reading_list_archive)
        is Place.Folder -> place.name
    }
