package to.wim.content.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import to.wim.content.R
import to.wim.content.ui.Cookie
import to.wim.content.ui.cookiePath

/**
 * Pulling past the end of the list to mark it all as read (2026-11-03), after Material 3's pull to
 * refresh: the further you pull, the fuller the button's ring; past [THRESHOLD_DP] the button is
 * filled and says to let go, and letting go marks all as read. Plain Kotlin, tested on the JVM.
 */
object PullToMark {
    /** How far to pull, in dp, before letting go marks all as read. */
    const val THRESHOLD_DP = 96f

    /** The pull follows the finger at half its distance, as an overscroll does. */
    const val RESISTANCE = 0.5f

    /** Pulling stops at this many thresholds. */
    private const val MAX = 1.5f

    /**
     * The pull after the finger moves [delta] pixels (negative: up, past the end; positive: back
     * down). Never below 0 or over [MAX] thresholds.
     */
    fun drag(
        pull: Float,
        delta: Float,
        threshold: Float,
    ): Float = (pull - delta * RESISTANCE).coerceIn(0f, threshold * MAX)

    /** How full the ring is, 0 to 1. */
    fun progress(
        pull: Float,
        threshold: Float,
    ): Float = if (threshold <= 0f) 0f else (pull / threshold).coerceIn(0f, 1f)

    /** Letting go now marks all as read. */
    fun armed(
        pull: Float,
        threshold: Float,
    ): Boolean = threshold > 0f && pull >= threshold
}

/** The pull past the end of the list, fed by [connection] on the list. */
@Stable
class PullToMarkState internal constructor(
    private val threshold: Float,
    private val haptics: HapticFeedback,
    private val onMark: () -> Unit,
) {
    var pull by mutableFloatStateOf(0f)
        private set

    val progress: Float get() = PullToMark.progress(pull, threshold)
    val armed: Boolean get() = PullToMark.armed(pull, threshold)

    private fun drag(delta: Float) {
        val wasArmed = armed
        pull = PullToMark.drag(pull, delta, threshold)
        if (armed && !wasArmed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    val connection =
        object : NestedScrollConnection {
            // Back down while pulled: the pull gives way before the list scrolls.
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || pull <= 0f || available.y <= 0f) return Offset.Zero
                val before = pull
                drag(available.y)
                return Offset(0f, (before - pull) / PullToMark.RESISTANCE)
            }

            // Up past the end, by the finger only (a fling that reaches the end doesn't pull).
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
                drag(available.y)
                return Offset(0f, available.y)
            }

            // Letting go: marks all as read when armed, and the button goes back in place.
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull <= 0f) return Velocity.Zero
                if (armed) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onMark()
                }
                // Springs back, with a little bounce.
                animate(pull, 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) { value, _ -> pull = value }
                return available
            }
        }
}

@Composable
fun rememberPullToMarkState(onMark: () -> Unit): PullToMarkState {
    val threshold = with(LocalDensity.current) { PullToMark.THRESHOLD_DP.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val mark by rememberUpdatedState(onMark)
    return remember(threshold, haptics) { PullToMarkState(threshold, haptics) { mark() } }
}

/**
 * Mark all as read at the end of the list (2026-11-03). Tapping it marks all as read; so does
 * pulling past the end ([state]), a small pleasure in Material 3 Expressive's manner: while you
 * pull, its label fades, a primary line runs round its outline (as pull to refresh's ring fills)
 * and the pill shrinks to a circle that becomes Content's cookie ([Cookie]); far enough, the cookie
 * fills with the primary colour, pops with a bouncy spring, the double tick springs in and it
 * turns slowly. Let go and it springs back (or, marked, All caught up springs in).
 */
@Composable
fun MarkAllReadButton(
    state: PullToMarkState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val armed = state.armed
    val morph = Cookie.morph(state.progress)
    val label = stringResource(R.string.action_mark_all_read)
    val release = stringResource(R.string.action_mark_all_read_release)
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.labelLarge.copy(color = colors.primary)
    val measurer = rememberTextMeasurer()
    val text = remember(label, textStyle) { measurer.measure(label, textStyle) }
    val icon = painterResource(R.drawable.ic_done_all)
    val density = LocalDensity.current
    // An outlined button's size: 24 dp padding, the 18 dp icon, 8 dp, the label, 24 dp.
    val pillWidth = with(density) { 74.dp.toPx() } + text.size.width
    val pillHeight = with(density) { 40.dp.toPx() }
    val circle = with(density) { 64.dp.toPx() }
    val cookie = with(density) { 76.dp.toPx() }
    val pop by animateFloatAsState(
        if (armed) 1f else 0f,
        spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow),
        label = "pop",
    )
    val fill by animateColorAsState(
        if (armed) colors.primary else lerp(Color.Transparent, colors.primaryContainer, morph.form),
        spring(stiffness = Spring.StiffnessMedium),
        label = "fill",
    )
    val tick by animateColorAsState(if (armed) colors.onPrimary else colors.onPrimaryContainer, label = "tick")
    // Turns slowly while it waits to be let go.
    var turn by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(armed) {
        while (armed) withFrameNanos { turn += 0.012 }
    }
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .graphicsLayer { translationY = -state.pull * PullToMark.RESISTANCE },
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(
            Modifier
                .size(width = with(density) { pillWidth.toDp() }, height = 88.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = if (armed) release else label },
        ) {
            val cx = size.width / 2
            val cy = 44.dp.toPx()
            // Pops a little past its size when it fills, then settles.
            val scale = 1f + 0.1f * pop * (1f - pop) * 4f
            val outline = cookiePath(Cookie.outline(morph, pillWidth, pillHeight, circle, cookie * scale, turn), cx, cy)
            drawPath(outline, fill)
            if (!armed) {
                drawPath(outline, colors.outline.copy(alpha = 1f - morph.form * 0.6f), style = Stroke(1.dp.toPx()))
                if (state.pull > 0f) {
                    val measure = PathMeasure().apply { setPath(outline, false) }
                    val run = Path()
                    measure.getSegment(0f, measure.length * state.progress, run, true)
                    drawPath(run, colors.primary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            if (morph.label > 0f) {
                val iconSize = 18.dp.toPx()
                val left = cx - (iconSize + 8.dp.toPx() + text.size.width) / 2
                translate(left, cy - iconSize / 2) {
                    with(icon) { draw(Size(iconSize, iconSize), alpha = morph.label, colorFilter = ColorFilter.tint(colors.primary)) }
                }
                drawText(text, topLeft = Offset(left + iconSize + 8.dp.toPx(), cy - text.size.height / 2), alpha = morph.label)
            }
            if (morph.check > 0f) {
                val size = 28.dp.toPx() * (0.6f + 0.4f * morph.check + 0.15f * pop * (1f - pop) * 4f)
                translate(cx - size / 2, cy - size / 2) {
                    with(icon) { draw(Size(size, size), alpha = morph.check, colorFilter = ColorFilter.tint(tick)) }
                }
            }
        }
    }
}

/** The caught-up screen after marking all as read springs in, a small reward. */
@Composable
fun Modifier.springIn(): Modifier {
    val grow = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    return graphicsLayer {
        scaleX = grow.value
        scaleY = grow.value
        alpha = ((grow.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
    }
}
