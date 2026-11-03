package to.wim.content.ui.home

import androidx.compose.animation.core.animate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import to.wim.content.R

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
 * Mark all as read at the end of the list. Tapping it marks all as read; so does pulling past the
 * end ([state]). Meant to be a small pleasure (owner's wish), in Material 3 Expressive's springy
 * motion: it moves up and grows a little with the pull while its ring fills and turns; far enough,
 * it pops (a bouncy spring) into its filled colour and the double tick springs in; let go and it
 * springs back.
 */
@Composable
fun MarkAllReadButton(
    state: PullToMarkState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val armed = state.armed
    val bouncy = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    val scale by animateFloatAsState(if (armed) 1.12f else 1f + 0.05f * state.progress, bouncy, label = "scale")
    val fill by animateFloatAsState(if (armed) 1f else 0f, spring(stiffness = Spring.StiffnessMedium), label = "fill")
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .graphicsLayer {
                translationY = -state.pull * PullToMark.RESISTANCE
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        OutlinedButton(
            onClick = onClick,
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = lerp(Color.Transparent, colors.primaryContainer, fill),
                    contentColor = lerp(colors.primary, colors.onPrimaryContainer, fill),
                ),
            border = BorderStroke(1.dp, colors.outline.copy(alpha = 1f - fill)),
        ) {
            AnimatedContent(
                targetState = state.pull > 0f && !armed,
                transitionSpec = { (scaleIn(bouncy) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                label = "icon",
            ) { pulling ->
                if (pulling) {
                    CircularProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = state.progress * 270f },
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(painterResource(R.drawable.ic_done_all), contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                stringResource(if (armed) R.string.action_mark_all_read_release else R.string.action_mark_all_read),
                modifier = Modifier.padding(start = 8.dp),
            )
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
