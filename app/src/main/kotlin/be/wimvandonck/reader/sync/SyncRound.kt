package be.wimvandonck.reader.sync

/** The document as this device last saved or read it, with the account's version of it. */
data class SyncBase(
    val version: Long,
    val text: String,
)

/**
 * One sync with the account, the same as the web app's syncRound (web/src/lib/sync.ts): reads the
 * document, lets mergeIn merge it into this device's library (it gets the base and the remote
 * document, applies the result and returns it), and saves the result when it differs. When another
 * device saved in between, it merges again with that device's document.
 */
object SyncRound {
    /** Saves after another device's save are tried again this often, then given up until next time. */
    const val MAX_ATTEMPTS = 4

    suspend fun run(
        target: SyncTarget,
        saved: SyncBase?,
        mergeIn: suspend (base: SyncDocument?, remote: SyncDocument?) -> SyncDocument,
    ): SyncBase {
        var base = saved?.let { SyncDocument.decode(it.text) }
        var answer: Versioned = target.read(saved?.takeIf { base != null }?.version) ?: Versioned(saved!!.version, saved.text)
        var attempt = 1
        while (true) {
            val remote = answer.text?.let { SyncDocument.decode(it) ?: throw SyncException(SyncProblem.UnreadableFile) }
            // Without a document there is nothing to merge with: this device's library becomes it.
            val out = mergeIn(if (remote == null) null else base, remote)
            val text = SyncDocument.encode(out)
            if (remote != null && SyncDocument.encode(remote) == text) return SyncBase(answer.version, text)
            when (val result = target.write(answer.version, text)) {
                is WriteResult.Saved -> return SyncBase(result.version, text)
                is WriteResult.Conflict -> {
                    if (attempt++ >= MAX_ATTEMPTS) throw SyncException(SyncProblem.ServerError)
                    // What was just merged in is this device's state now, so it is the base for the next try.
                    base = remote
                    answer = result.current
                }
            }
        }
    }
}
