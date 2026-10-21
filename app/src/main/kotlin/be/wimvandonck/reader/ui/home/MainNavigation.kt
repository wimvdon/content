package be.wimvandonck.reader.ui.home

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
import be.wimvandonck.reader.R

/** The app's top-level destinations (#171): Feeds, Saved and Search, in this order everywhere. */
enum class MainTab(
    val icon: Int,
    val label: Int,
) {
    Feeds(R.drawable.ic_rss_feed, R.string.feeds_title),
    Saved(R.drawable.ic_list, R.string.saved_title),
    Search(R.drawable.ic_search, R.string.action_search),
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
) {
    NavigationBar {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { if (selected != tab) onSelect(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
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
) {
    NavigationRail(Modifier.padding(top = 8.dp)) {
        MainTab.entries.forEach { tab ->
            NavigationRailItem(
                selected = selected == tab,
                onClick = { if (selected != tab) onSelect(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}
