package to.wim.content.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import to.wim.content.R

/**
 * The app's top-level destinations: Feed, Saved and, while signed in, the Inbox in the navigation
 * bar (#189, #246); Search opens from the search bar.
 */
enum class MainTab(
    val icon: Int,
    val label: Int,
) {
    Feeds(R.drawable.ic_rss_feed, R.string.home_tab_feed),
    Saved(R.drawable.ic_list, R.string.saved_title),
    Inbox(R.drawable.ic_notifications, R.string.inbox_title),
    Search(R.drawable.ic_search, R.string.action_search),
}

/** What the navigation bar and rail show: the Inbox only with an account, which it comes through. */
private fun barTabs(signedIn: Boolean): List<MainTab> = listOfNotNull(MainTab.Feeds, MainTab.Saved, MainTab.Inbox.takeIf { signedIn })

/**
 * A destination's icon, with Material's small badge on the Inbox while something in it is unread,
 * and on Saved while a newsletter waits unread (with the setting on).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabIcon(
    tab: MainTab,
    inboxUnread: Int,
    savedDot: Boolean,
) {
    BadgedBox(badge = { if (tab == MainTab.Inbox && inboxUnread > 0 || tab == MainTab.Saved && savedDot) NewBadge() }) {
        Icon(painterResource(tab.icon), contentDescription = null)
    }
}

/**
 * Material's small badge for "something new waits" (the Inbox, a newsletter): in primary, like the
 * unread dot on a row, not Material's default error colour, which says something is wrong.
 */
@Composable
fun NewBadge(modifier: Modifier = Modifier) {
    Badge(modifier, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
}

/** From this width the destinations sit in a navigation rail instead of a bar (Material's medium window). */
private val WIDE_WINDOW = 600.dp

/** Whether the window is wide enough for the navigation rail. */
@Composable
fun isWideWindow(): Boolean = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= WIDE_WINDOW

/** The destinations at the bottom of a phone-sized window. */
@Composable
fun MainNavigationBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    signedIn: Boolean = false,
    inboxUnread: Int = 0,
    savedDot: Boolean = false,
) {
    NavigationBar {
        barTabs(signedIn).forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { if (selected != tab) onSelect(tab) },
                icon = { TabIcon(tab, inboxUnread, savedDot) },
                label = { Text(stringResource(tab.label)) },
                modifier = Modifier.tabDescription(tab, inboxUnread, savedDot),
            )
        }
    }
}

/** The same destinations at the side of a wide window. */
@Composable
fun MainNavigationRail(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    signedIn: Boolean = false,
    inboxUnread: Int = 0,
    savedDot: Boolean = false,
) {
    NavigationRail(Modifier.padding(top = 8.dp)) {
        barTabs(signedIn).forEach { tab ->
            NavigationRailItem(
                selected = selected == tab,
                onClick = { if (selected != tab) onSelect(tab) },
                icon = { TabIcon(tab, inboxUnread, savedDot) },
                label = { Text(stringResource(tab.label)) },
                modifier = Modifier.tabDescription(tab, inboxUnread, savedDot),
            )
        }
    }
}

/** The Inbox read out as "Inbox, something new" while its badge shows; Saved as "Saved, a newsletter waits". */
@Composable
private fun Modifier.tabDescription(
    tab: MainTab,
    inboxUnread: Int,
    savedDot: Boolean,
): Modifier {
    val spoken =
        when {
            tab == MainTab.Inbox && inboxUnread > 0 -> stringResource(R.string.inbox_open_unread)
            tab == MainTab.Saved && savedDot -> stringResource(R.string.saved_open_unread)
            else -> return this
        }
    return semantics(mergeDescendants = true) { contentDescription = spoken }
}
