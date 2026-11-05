package to.wim.content.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import to.wim.content.R
import to.wim.content.ui.drawBusyCookie

/**
 * What a refresh brought (2026-11-05): how many items of the list shown are new, which the cookie
 * shows as "+3" before it leaves. Plain Kotlin, tested on the JVM.
 */
object RefreshCount {
    /** How many of [after] (the list's keys once refreshed) weren't in [before]. */
    fun added(
        before: Set<String>,
        after: Collection<String>,
    ): Int = after.count { it !in before }

    /** What the cookie says: "+3", "+99" at most; null when nothing came in (a tick instead). */
    fun label(added: Int): String? =
        when {
            added <= 0 -> null
            added > 99 -> "+99"
            else -> "+$added"
        }

    /** How long the result stays, in milliseconds. */
    const val SHOWN_MS = 1400L
}

/**
 * Pull to refresh with Content's cookie instead of Material's spinner (2026-11-05, owner's choice
 * from a mockup). Content is busy, so the cookie stays in its light tone (never the blue fill of
 * actions that change your articles): while you pull, a primary line runs round it; while
 * refreshing it turns; when done it says how many of [shownKeys] (the list on screen) are new,
 * "+3", or a tick for nothing new, then shrinks away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookieRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    shownKeys: List<String>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val keys by rememberUpdatedState(shownKeys)
    var before by remember { mutableStateOf<Set<String>?>(null) }
    var result by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            before = keys.toSet()
            result = null
        } else {
            val start = before ?: return@LaunchedEffect
            before = null
            // The list follows the library a moment after the refresh ends.
            delay(150)
            result = RefreshCount.added(start, keys)
            delay(RefreshCount.SHOWN_MS)
            result = null
        }
    }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = { CookieRefreshIndicator(state, isRefreshing, result, Modifier.align(Alignment.TopCenter)) },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CookieRefreshIndicator(
    state: PullToRefreshState,
    refreshing: Boolean,
    result: Int?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val held = refreshing || result != null
    // Comes in with the pull; once refreshing (or showing the result) it stays, then shrinks away.
    val shown by animateFloatAsState(if (held) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "shown")
    // A small pop when the result comes in.
    val pop by animateFloatAsState(if (result != null) 1f else 0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow), label = "pop")
    val transition = rememberInfiniteTransition(label = "refreshing")
    val turn by transition.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "turn")
    val run by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "run")
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = colors.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
    val label = result?.let { RefreshCount.label(it) }
    val tick = painterResource(R.drawable.ic_check)
    val said = result?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.refresh_added, it, it) }
    Box(
        modifier
            .size(44.dp)
            .graphicsLayer {
                val fraction = state.distanceFraction.coerceIn(0f, 1.3f)
                val visible = maxOf(shown, fraction.coerceAtMost(1f))
                translationY = (if (held) 1f else fraction) * 64.dp.toPx() - 44.dp.toPx()
                val s = visible * (1f + 0.15f * pop * (1f - pop) * 4f)
                scaleX = s
                scaleY = s
                alpha = visible.coerceIn(0f, 1f)
            }.semantics {
                if (said != null) {
                    contentDescription = said
                    liveRegion = LiveRegionMode.Polite
                }
            },
    ) {
        Canvas(Modifier.size(44.dp)) {
            val size = this.size.minDimension
            when {
                result != null -> {
                    drawBusyCookie(size, turn.toDouble(), colors.primaryContainer, colors.primary, start = 0f, length = 0f)
                    if (label != null) {
                        val text = measurer.measure(label, labelStyle)
                        drawText(text, topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f))
                    } else {
                        val icon = size * 0.5f
                        translate(center.x - icon / 2, center.y - icon / 2) {
                            with(tick) { draw(Size(icon, icon), colorFilter = ColorFilter.tint(colors.onPrimaryContainer)) }
                        }
                    }
                }
                refreshing -> drawBusyCookie(size, turn.toDouble(), colors.primaryContainer, colors.primary, start = run, length = 0.3f)
                else -> drawBusyCookie(size, 0.0, colors.primaryContainer, colors.primary, start = 0f, length = state.distanceFraction.coerceIn(0f, 1f))
            }
        }
    }
}
