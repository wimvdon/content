package to.wim.content.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountSyncTest {
    private val feed = SyncFeed(url = "f", title = "F", renamed = false, folderId = null, siteUrl = null, iconUrl = null)

    private fun doc(vararg feeds: String) = SyncDocument(feeds = feeds.map { feed.copy(url = it) })

    private fun urls(text: String?) = SyncDocument.decode(text!!)!!.feeds.mapTo(HashSet()) { it.url }

    /** An account in memory, like the Worker: versions, and conflicts for old bases. */
    private class FakeAccount(
        var version: Long = 0,
        var text: String? = null,
    ) : SyncTarget {
        var writes = 0
        var beforeWrite: () -> Unit = {}

        override suspend fun read(knownVersion: Long?): Versioned? = if (knownVersion == version) null else Versioned(version, text)

        override suspend fun write(
            baseVersion: Long,
            text: String,
        ): WriteResult {
            beforeWrite()
            if (baseVersion != version) return WriteResult.Conflict(Versioned(version, this.text))
            writes++
            version++
            this.text = text
            return WriteResult.Saved(version)
        }
    }

    @Test
    fun `the first device's library becomes the document`() =
        runBlocking {
            val account = FakeAccount()
            val next =
                SyncRound.run(account, null) { _, remote ->
                    assertNull(remote)
                    doc("f a")
                }
            assertEquals(1L, next.version)
            assertEquals(setOf("f a"), urls(account.text))
        }

    @Test
    fun `nothing is saved when nothing changed`() =
        runBlocking {
            val text = SyncDocument.encode(doc("f a"))
            val account = FakeAccount(4, text)
            val next = SyncRound.run(account, SyncBase(4, text)) { base, remote -> remote ?: base!! }
            assertEquals(SyncBase(4, text), next)
            assertEquals(0, account.writes)
        }

    @Test
    fun `another device's save in between is merged in, with what was merged as the base`() =
        runBlocking {
            val account = FakeAccount(1, SyncDocument.encode(doc("f a")))
            var library = doc("f b")
            account.beforeWrite = {
                // Another device adds feed "f c" while this one merges; only once.
                if (account.version == 1L) {
                    account.version = 2
                    account.text = SyncDocument.encode(doc("f a", "f c"))
                }
            }
            var merges = 0
            val next =
                SyncRound.run(account, null) { base, remote ->
                    merges++
                    library = if (remote == null) library else SyncMerge.merge(base, library, remote)
                    library
                }
            assertEquals(2, merges)
            assertEquals(3L, next.version)
            assertEquals(setOf("f a", "f b", "f c"), urls(account.text))
        }

    @Test
    fun `a document from a newer version is left alone`() {
        val account = FakeAccount(1, """{"format":"rss-reader-sync","version":99}""")
        val e = assertThrows(SyncException::class.java) { runBlocking { SyncRound.run(account, null) { _, _ -> doc() } } }
        assertEquals(SyncProblem.UnreadableFile, e.problem)
    }

    @Test
    fun `the server's answers are read`() {
        assertEquals(Versioned(2, null), AccountClient.documentFrom("""{"version":2,"data":null}"""))
        val doc = AccountClient.documentFrom("""{"version":3,"data":{"format":"rss-reader-sync","version":2}}""")
        assertEquals(3L, doc.version)
        assertEquals(mapOf("format" to "rss-reader-sync", "version" to 2L), Json.parse(doc.text!!))
        assertEquals(5L, AccountClient.versionFrom("""{"version":5}"""))
        assertThrows(SyncException::class.java) { AccountClient.documentFrom("<html>") }
        assertEquals(SyncProblem.SignedOut, AccountClient.problemOf(401))
        assertEquals(SyncProblem.NotSetUp, AccountClient.problemOf(503))
        assertEquals(SyncProblem.ServerError, AccountClient.problemOf(500))
    }

    @Test
    fun `the live connection's address and messages`() {
        assertEquals("wss://worker.example/account/live?key=a.b", AccountClient.liveUrl("a.b", "https://worker.example"))
        assertEquals(LiveVersions(7L, null), LiveUpdates.versionOf("""{"version":7}"""))
        assertEquals(LiveVersions(7L, 3L), LiveUpdates.versionOf("""{"version":7,"items":3}"""))
        assertNull(LiveUpdates.versionOf("pong"))
        assertEquals(2_000L, LiveUpdates.retryDelay(1))
        assertEquals(4_000L, LiveUpdates.retryDelay(2))
        assertEquals(60_000L, LiveUpdates.retryDelay(20))
    }

    @Test
    fun `the stored base keeps its version`() {
        val base = SyncBase(12, """{"a":1}""")
        assertEquals(base, SyncManager.baseFrom(SyncManager.baseText(base)))
        // A base from the Google Drive version (just the document) is no base.
        assertNull(SyncManager.baseFrom("""{"format":"rss-reader-sync"}"""))
        assertNull(SyncManager.baseFrom(""))
    }
}
