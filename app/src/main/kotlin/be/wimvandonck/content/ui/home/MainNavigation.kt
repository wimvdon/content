package be.wimvandonck.content.ui.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import be.wimvandonck.content.R

/** The app's top-level destinations: Feed and Saved in the navigation bar (#189); Search opens from the search bar. */
enum class MainTab(
    val icon: Int,
    val label: Int,
) {
    Feeds(R.drawable.ic_rss_feed, R.string.home_tab_feed),
    Saved(R.drawable.ic_list, R.string.saved_title),
    Search(R.drawable.ic_search, R.string.action_search),
}

/** What the navigation bar and rail show. */
private val BAR_TABS = listOf(MainTab.Feeds, MainTab.Saved)

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
    /** Something shared with you waits in Saved (#167). */
    savedDot: Boolean = false,
) {
    NavigationBar {
        BAR_TABS.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { if (selected != tab) onSelect(tab) },
                icon = { TabIcon(tab, savedDot && tab == MainTab.Saved) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

/** The same destinations at the side of a wide window. */
@Composable
fun MainNavigationRail(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    savedDot: Boolean = false,
) {
    NavigationRail(Modifier.padding(top = 8.dp)) {
        BAR_TABS.forEach { tab ->
            NavigationRailItem(
                selected = selected == tab,
                onClick = { if (selected != tab) onSelect(tab) },
                icon = { TabIcon(tab, savedDot && tab == MainTab.Saved) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

/** A destination's icon, with Material's small badge when something new waits there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabIcon(
    tab: MainTab,
    dot: Boolean,
) {
    if (dot) {
        BadgedBox(badge = { Badge() }) { Icon(painterResource(tab.icon), contentDescription = null) }
    } else {
        Icon(painterResource(tab.icon), contentDescription = null)
    }
}
