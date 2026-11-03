package to.wim.content.ui.home

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
                animate(pull, 0f) { value, _ -> pull = value }
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
 * end ([state]): it moves up with the pull, its ring fills, and once far enough it is filled and
 * says to let go.
 */
@Composable
fun MarkAllReadButton(
    state: PullToMarkState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .graphicsLayer { translationY = -state.pull * PullToMark.RESISTANCE },
        contentAlignment = Alignment.Center,
    ) {
        val content: @Composable RowScope.() -> Unit = {
            if (state.pull > 0f && !state.armed) {
                CircularProgressIndicator(progress = { state.progress }, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(painterResource(R.drawable.ic_done_all), contentDescription = null)
            }
            Text(
                stringResource(if (state.armed) R.string.action_mark_all_read_release else R.string.action_mark_all_read),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (state.armed) FilledTonalButton(onClick = onClick, content = content) else OutlinedButton(onClick = onClick, content = content)
    }
}
