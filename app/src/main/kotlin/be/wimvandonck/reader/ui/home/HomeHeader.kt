package be.wimvandonck.reader.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * The home screen's header (top app bar, folder tabs, the Unread chip row) as one block that slides away
 * when scrolling down the list and comes back as soon as you scroll up, like Material 3's
 * "enter always" top app bar. Only the status bar strip stays.
 *
 * It drives [scrollBehavior] itself, so the bar inside must not get the scroll behavior and should
 * have a transparent background and no window insets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsingHeader(
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = scrollBehavior.state
    // Material's "on scroll" colour once articles slide under the header.
    val container by animateColorAsState(
        if (state.overlappedFraction > 0.01f) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        label = "header",
    )
    Column(modifier.background(container).windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))) {
        Layout(
            content = {
                Column(
                    Modifier.onSizeChanged { size ->
                        // How far the header can slide away: all of it.
                        val limit = -size.height.toFloat()
                        if (state.heightOffsetLimit != limit) state.heightOffsetLimit = limit
                    },
                ) { content() }
            },
            modifier = Modifier.clipToBounds(),
        ) { measurables, constraints ->
            val placeable = measurables.single().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            val offset = state.heightOffset.coerceIn(-placeable.height.toFloat(), 0f).roundToInt()
            layout(placeable.width, placeable.height + offset) {
                placeable.place(0, offset)
            }
        }
    }
}
