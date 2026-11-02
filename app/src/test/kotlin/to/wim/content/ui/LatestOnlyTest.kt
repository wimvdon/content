package to.wim.content.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestOnlyTest {
    @Test
    fun aNewMessageCancelsTheOneShowing() =
        runBlocking {
            val messages = LatestOnly(this)
            val firstCancelled = CompletableDeferred<Boolean>()
            val shown = mutableListOf<String>()
            messages.launch {
                shown += "first"
                try {
                    awaitCancellation()
                } finally {
                    firstCancelled.complete(true)
                }
            }
            yield()
            messages.launch { shown += "second" }
            assertTrue(firstCancelled.await())
            yield()
            assertEquals(listOf("first", "second"), shown)
        }

    @Test
    fun onlyTheLastOfSeveralWaitingRuns() =
        runBlocking {
            val messages = LatestOnly(this)
            val shown = mutableListOf<String>()
            // Launched before any of them gets to run: the earlier ones never show.
            messages.launch { shown += "one" }
            messages.launch { shown += "two" }
            messages.launch { shown += "three" }
            yield()
            assertEquals(listOf("three"), shown)
        }
}
