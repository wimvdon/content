package be.wimvandonck.reader.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncProblemTest {
    @Test
    fun `an app Google doesn't know is not set up, anything else is offline`() {
        assertEquals(SyncProblem.NotSetUp, authorizationProblem(10))
        assertEquals(SyncProblem.Offline, authorizationProblem(7))
        assertEquals(SyncProblem.Offline, authorizationProblem(null))
    }
}
