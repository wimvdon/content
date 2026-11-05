package to.wim.content.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.graphicsLayer
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

    /** What the cookie says: "+3", "+99" at most; null when nothing came in (no cookie then). */
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
 * Pull to refresh (2026-11-05, owner's choice): Material's standard indicator, as everywhere on
 * Android (its box, arrow, spinner, place, shadow and the way it leaves). Only when the refresh
 * brought new items into [shownKeys] (the list on screen) does its circle, once done, smoothly
 * become Content's cookie in its light tone with "+3", for a moment, before it slides away. Nothing
 * new: it leaves as Material's always does.
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
    // Once the refresh is over: null while the count isn't known yet, then the count.
    var result by remember { mutableStateOf<Int?>(null) }
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            before = keys.toSet()
            result = null
            holding = true
        } else {
            val start = before ?: return@LaunchedEffect
            before = null
            // The list follows the library a moment after the refresh ends.
            delay(150)
            val added = RefreshCount.added(start, keys)
            if (added > 0) {
                result = added
                delay(RefreshCount.SHOWN_MS)
            }
            holding = false
        }
    }
    // The indicator stays out (as while refreshing) until the result has been seen.
    val shown = isRefreshing || holding
    PullToRefreshBox(
        isRefreshing = shown,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = { RefreshIndicator(state, shown, if (isRefreshing) null else result, Modifier.align(Alignment.TopCenter)) },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshIndicator(
    state: PullToRefreshState,
    shown: Boolean,
    result: Int?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // Circle to cookie, smoothly, only once new items came in; back to a circle once gone.
    val grows = shown && result != null
    val form by animateFloatAsState(if (grows) 1f else 0f, if (grows) tween(500, easing = FastOutSlowInEasing) else snap(), label = "form")
    val done by animateFloatAsState(if (result != null) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), label = "done")
    val container by animateColorAsState(if (result != null) colors.primaryContainer else PullToRefreshDefaults.indicatorContainerColor, tween(300), label = "container")
    val ink = PullToRefreshDefaults.indicatorColor
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = colors.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
    val label = result?.let { RefreshCount.label(it) }
    val said = result?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.refresh_added, it, it) }
    val shape = remember(form) { GenericShape { box, _ -> addPath(cookiePath(RefreshCount.outline(form, box.minDimension), box.width / 2, box.height / 2)) } }
    PullToRefreshDefaults.IndicatorBox(
        state = state,
        isRefreshing = shown,
        modifier =
            modifier.semantics {
                if (said != null) {
                    contentDescription = said
                    liveRegion = LiveRegionMode.Polite
                }
            },
        maxDistance = PullToRefreshDefaults.PositionalThreshold,
        shape = shape,
        containerColor = container,
    ) {
        when {
            // Pulling: Material's arrow, a ring that grows with the pull.
            !shown -> PullArrow({ state.distanceFraction }, ink)
            // Refreshing: Material's spinner, inside the shape as it becomes the cookie.
            result == null -> CircularProgressIndicator(Modifier.size(20.dp), color = ink, strokeWidth = 2.5.dp)
            else ->
                Canvas(Modifier.size(40.dp).graphicsLayer { scaleX = 0.6f + 0.4f * done; scaleY = 0.6f + 0.4f * done; alpha = done.coerceIn(0f, 1f) }) {
                    val text = measurer.measure(label ?: return@Canvas, labelStyle)
                    drawText(text, topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f))
                }
        }
    }
}

/** Material's pull arrow: a ring that grows with the pull, with its arrowhead; faint until far enough. */
@Composable
private fun PullArrow(
    fraction: () -> Float,
    color: Color,
) {
    Canvas(Modifier.size(40.dp)) {
        val p = fraction().coerceIn(0f, 1f)
        val alpha = if (fraction() >= 1f) 1f else 0.3f + 0.4f * p
        val radius = 7.5.dp.toPx()
        val stroke = 2.5.dp.toPx()
        val start = -90f + 180f * p
        val sweep = 300f * p
        rotate(0f) {
            drawArc(color.copy(alpha = alpha), start, sweep, useCenter = false, topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2), style = Stroke(stroke, cap = StrokeCap.Butt))
            if (sweep > 2f) {
                // The arrowhead at the end of the ring, pointing along it.
                val end = Math.toRadians((start + sweep).toDouble())
                val tip = Offset(center.x + radius * kotlin.math.cos(end).toFloat(), center.y + radius * kotlin.math.sin(end).toFloat())
                val head = 5.dp.toPx() * p
                rotate(start + sweep + 90f, pivot = tip) {
                    val path =
                        Path().apply {
                            moveTo(tip.x - head, tip.y - head / 2)
                            lineTo(tip.x + head, tip.y - head / 2)
                            lineTo(tip.x, tip.y + head / 2)
                            close()
                        }
                    drawPath(path, color.copy(alpha = alpha))
                }
            }
        }
    }
}
