package to.wim.content.ui

import androidx.compose.animation.EnterExitState.PostExit
import androidx.compose.animation.EnterExitState.PreEnter
import androidx.compose.animation.EnterExitState.Visible
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSettledTest {
    @Test
    fun notSettledWhileMovingIn() {
        assertFalse(ScreenSettled.of(current = PreEnter, target = Visible))
        assertFalse(ScreenSettled.of(current = PreEnter, target = PreEnter))
    }

    @Test
    fun settledOnceFullyIn() {
        assertTrue(ScreenSettled.of(current = Visible, target = Visible))
    }

    @Test
    fun notSettledWhileMovingOut() {
        assertFalse(ScreenSettled.of(current = Visible, target = PostExit))
        assertFalse(ScreenSettled.of(current = PostExit, target = PostExit))
    }
}
