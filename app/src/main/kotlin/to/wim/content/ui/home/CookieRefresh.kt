package to.wim.content.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
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
import androidx.compose.ui.graphics.lerp
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
import to.wim.content.ui.Cookie
import to.wim.content.ui.cookiePath

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

    /** Material's indicator, a circle, becoming the cookie: its outline at [form] (0 circle, 1 cookie), [size] across. */
    fun outline(
        form: Float,
        size: Float,
    ): List<Cookie.Point> = Cookie.mix(Cookie.pill(size, size), Cookie.points(size), form.coerceIn(0f, 1f))
}

/**
 * Pull to refresh (2026-11-05, owner's choice): while you pull and while it refreshes, Material's own
 * indicator (the circle with the turning arrow), as everywhere on Android. When it's done, that
 * circle turns into Content's cookie, in its light tone (Content was busy: never the blue fill of
 * actions that change your articles), and says how many of [shownKeys] (the list on screen) are
 * new, "+3", or a tick for nothing new; then it shrinks away.
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
    // After a refresh: Settling (the circle stays where the spinner was) until the count is known, then the count.
    var after by remember { mutableStateOf<RefreshResult?>(null) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            before = keys.toSet()
            after = null
        } else {
            val start = before ?: return@LaunchedEffect
            before = null
            after = RefreshResult.Settling
            // The list follows the library a moment after the refresh ends.
            delay(150)
            after = RefreshResult.Counted(RefreshCount.added(start, keys))
            delay(RefreshCount.SHOWN_MS)
            after = RefreshResult.Gone
            delay(300)
            after = null
        }
    }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            val done = after
            if (done == null) {
                PullToRefreshDefaults.Indicator(state = state, isRefreshing = isRefreshing, modifier = Modifier.align(Alignment.TopCenter))
            } else {
                RefreshDone(done, Modifier.align(Alignment.TopCenter))
            }
        },
        content = content,
    )
}

/** What the indicator shows once the refresh is over. */
private sealed interface RefreshResult {
    data object Settling : RefreshResult

    data class Counted(
        val added: Int,
    ) : RefreshResult

    data object Gone : RefreshResult
}

/** Material's indicator circle, where the spinner was, turning into the cookie with "+3" or a tick. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshDone(
    done: RefreshResult,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val counted = done as? RefreshResult.Counted
    val form by animateFloatAsState(if (done is RefreshResult.Settling) 0f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "form")
    val leave by animateFloatAsState(if (done is RefreshResult.Gone) 0f else 1f, tween(250), label = "leave")
    val container = lerp(PullToRefreshDefaults.indicatorContainerColor, colors.primaryContainer, form.coerceIn(0f, 1f))
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = colors.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
    val label = counted?.let { RefreshCount.label(it.added) }
    val tick = painterResource(R.drawable.ic_check)
    val said = counted?.added?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.refresh_added, it, it) }
    val size = 40.dp
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                // Where Material's indicator sits while refreshing.
                translationY = PullToRefreshDefaults.PositionalThreshold.toPx() - size.toPx()
                // Material's indicator has a shadow; the shape keeps it as it becomes the cookie.
                shadowElevation = PullToRefreshDefaults.Elevation.toPx()
                shape = GenericShape { box, _ -> addPath(cookiePath(RefreshCount.outline(form, box.minDimension * (1f + 0.12f * form.coerceIn(0f, 1f))), box.width / 2, box.height / 2)) }
                scaleX = leave
                scaleY = leave
                alpha = leave
            }.semantics {
                if (said != null) {
                    contentDescription = said
                    liveRegion = LiveRegionMode.Polite
                }
            },
    ) {
        Canvas(Modifier.size(size)) {
            val d = this.size.minDimension
            // A little larger as it becomes the cookie, as its bumps reach past the circle.
            val outline = cookiePath(RefreshCount.outline(form, d * (1f + 0.12f * form.coerceIn(0f, 1f))), center.x, center.y)
            drawPath(outline, container)
            val ink = form.coerceIn(0f, 1f)
            if (counted == null || ink <= 0f) return@Canvas
            if (label != null) {
                val text = measurer.measure(label, labelStyle)
                drawText(text, topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f), alpha = ink)
            } else {
                val icon = d * 0.5f
                translate(center.x - icon / 2, center.y - icon / 2) {
                    with(tick) { draw(Size(icon, icon), alpha = ink, colorFilter = ColorFilter.tint(colors.onPrimaryContainer)) }
                }
            }
        }
    }
}
