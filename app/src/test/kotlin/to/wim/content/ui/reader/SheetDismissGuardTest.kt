package to.wim.content.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetDismissGuardTest {
    @Test
    fun `a sheet that has settled closes`() {
        assertTrue(SheetDismissGuard().allowsDismiss(now = 0L, keyboardOpen = false))
    }

    @Test
    fun `with the keyboard open the sheet stays`() {
        assertFalse(SheetDismissGuard().allowsDismiss(now = 10_000L, keyboardOpen = true))
    }

    @Test
    fun `just after the keyboard or a page changed the sheet stays, then closes`() {
        val guard = SheetDismissGuard(settleMillis = 600L)
        guard.changed(1_000L)
        assertFalse(guard.allowsDismiss(1_200L, keyboardOpen = false))
        assertFalse(guard.allowsDismiss(1_599L, keyboardOpen = false))
        assertTrue(guard.allowsDismiss(1_600L, keyboardOpen = false))
    }
}
