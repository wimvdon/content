package to.wim.content.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.data.Folder
import to.wim.content.data.chosenTopic
import to.wim.content.ui.feeds.TopicEditActions
import to.wim.content.ui.feeds.TopicsEditor
import to.wim.content.ui.reader.TopicsHeading

/**
 * Asks for a website or feed address. [busy] shows a spinner in the Add button while the app
 * looks for the feed; [error] explains why the last attempt failed.
 */
@Composable
fun AddFeedDialog(
    busy: Boolean,
    error: String?,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
    /** An address to start with: the link shared to the share menu's Add feed. */
    initialAddress: String = "",
) {
    var address by rememberSaveable { mutableStateOf(initialAddress) }
    // The address last tried: its problem shows until the address is changed.
    var tried by rememberSaveable { mutableStateOf("") }
    val shownError = error?.takeIf { address == tried }
    val add = {
        tried = address
        onAdd(address)
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val canAdd = address.isNotBlank() && !busy

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeds_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.add_feed_label)) },
                    singleLine = true,
                    enabled = !busy,
                    isError = shownError != null,
                    supportingText =
                        shownError?.let {
                            { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canAdd) add() }),
                    modifier = Modifier.focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = add, enabled = canAdd) {
                if (busy) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text(stringResource(R.string.action_add))
                }
            }
        },
        dismissButton = {
            // While it looks for the feed, Cancel can't stop it: shown as off rather than doing nothing.
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Add feed as a sheet (#307), like the Save sheet: the website or feed address, then its topic as
 * chips (No topic, then every topic in your order), with the pen next to it that turns the sheet
 * into Manage topics (Back returns here), and Cancel and Add. [busy] shows a spinner in Add while
 * the app looks for the feed; [error] explains why the last attempt failed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddFeedSheet(
    busy: Boolean,
    error: String?,
    topics: List<Folder>,
    /** The topic it starts in: the one being viewed, or the topic page it came from. */
    initialTopic: String?,
    onAdd: (address: String, topicId: String?) -> Unit,
    onDismiss: () -> Unit,
    /** Manage topics in the sheet; null: no pen. */
    topicActions: TopicEditActions? = null,
) {
    var address by rememberSaveable { mutableStateOf("") }
    // The address last tried: its problem shows until the address is changed.
    var tried by rememberSaveable { mutableStateOf("") }
    var topicId by rememberSaveable { mutableStateOf(initialTopic) }
    var managing by rememberSaveable { mutableStateOf(false) }
    // The keyboard opens once, not again when coming back from Manage topics.
    var focused by rememberSaveable { mutableStateOf(false) }
    val chosen = chosenTopic(topicId, topics)
    val shownError = error?.takeIf { address == tried }
    val add = {
        tried = address
        onAdd(address, chosen)
    }
    val canAdd = address.isNotBlank() && !busy
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // On Manage topics, Back goes back to the sheet rather than closing it.
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !managing),
    ) {
        BackHandler(enabled = managing) { managing = false }
        if (managing && topicActions != null) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 24.dp, bottom = 24.dp),
            ) {
                TopicsEditor(topics = topics, actions = topicActions, onBack = { managing = false })
            }
        } else {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                if (!focused) {
                    focused = true
                    focus.requestFocus()
                }
            }
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.feeds_add),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.add_feed_label)) },
                    singleLine = true,
                    enabled = !busy,
                    isError = shownError != null,
                    supportingText =
                        shownError?.let {
                            { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canAdd) add() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                TopicsHeading(stringResource(R.string.feed_folder), topicActions?.let { { managing = true } })
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    TopicChip(stringResource(R.string.folder_none), icon = null, selected = chosen == null, enabled = !busy) { topicId = null }
                    topics.forEach { topic ->
                        TopicChip(topic.title, topic.icon, selected = chosen == topic.id, enabled = !busy) { topicId = topic.id }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    // While it looks for the feed, Cancel can't stop it: shown as off rather than doing nothing.
                    TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
                    Button(onClick = add, enabled = canAdd) {
                        if (busy) {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = LocalContentColor.current, modifier = Modifier.size(18.dp))
                        } else {
                            Text(stringResource(R.string.action_add))
                        }
                    }
                }
            }
        }
    }
}

/** One topic to pick in the Add feed sheet: its icon (a check when chosen) and name. */
@Composable
private fun TopicChip(
    label: String,
    icon: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon =
            when {
                selected -> {
                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                }
                icon != null -> {
                    { Icon(rememberFolderIconPainter(icon), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                }
                else -> null
            },
        modifier = Modifier.semantics { role = Role.RadioButton },
    )
}
