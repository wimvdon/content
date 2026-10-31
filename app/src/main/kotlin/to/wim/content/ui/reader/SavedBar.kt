package to.wim.content.ui.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.data.Place
import to.wim.content.data.SaveChoices

/**
 * The reader's bar for a saved article (#139, #251): its folder on the left, which opens Organise
 * (the folder, Like, tags and the note in one sheet), and while it isn't archived an Archive button
 * next to it: one tap, and the reader closes with Undo; the highlights and Share follow at the bar's
 * right end. An arrow down says the folder opens something; a long folder name gives way before the
 * arrow and Archive do.
 */
@Composable
internal fun SavedBar(item: ReaderItem) {
    val place = SaveChoices.of(item.item).place
    val name = placeName(place)
    // Read out as "Folder: To read", not as an action called "To read".
    val spoken = "${stringResource(R.string.folder_field)}: $name"
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = item.onOrganise,
            modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false).semantics(mergeDescendants = true) { contentDescription = spoken },
        ) {
            Icon(painterResource(placeIcon(place)), contentDescription = null)
            Text(name, modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(ButtonDefaults.IconSize))
        }
        if (!item.item.archived) {
            IconButton(onClick = item.onArchive) {
                Icon(painterResource(R.drawable.ic_inventory), contentDescription = stringResource(R.string.action_archive))
            }
        }
    }
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
