package be.wimvandonck.reader.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/** Why syncing failed. */
enum class SyncProblem {
    /** No connection, or Reader's server didn't answer. */
    Offline,

    /** This device's key no longer works (signed out elsewhere, or the data was deleted): sign in again. */
    SignedOut,

    /** The server refused or failed. */
    ServerError,

    /** The synced document is from a newer version of the app, or damaged; it is left alone. */
    UnreadableFile,

    /** The server has no accounts yet (docs/account.md). */
    NotSetUp,
}

class SyncException(
    val problem: SyncProblem,
    cause: Throwable? = null,
) : Exception(problem.name, cause)

/** The account's document: its version, and its text (null before the first save). */
data class Versioned(
    val version: Long,
    val text: String?,
)

/** Reading and saving the account's document (see [SyncRound]). [AccountClient] is the real one; tests use a fake. */
interface SyncTarget {
    /** The document, or null when [knownVersion] is still the latest. */
    suspend fun read(knownVersion: Long?): Versioned?

    /** Saves [text] unless another device saved after [baseVersion]: then returns that device's document. */
    suspend fun write(
        baseVersion: Long,
        text: String,
    ): WriteResult
}

sealed interface WriteResult {
    data class Saved(
        val version: Long,
    ) : WriteResult

    data class Conflict(
        val current: Versioned,
    ) : WriteResult
}

/**
 * The user's Reader account on the feed loader (worker/src/account.ts, docs/account.md), with
 * this device's [key] from signing in with Instapaper.
 */
class AccountClient(
    private val http: OkHttpClient,
    private val key: String,
    private val base: String = BASE_URL,
) : SyncTarget {
    override suspend fun read(knownVersion: Long?): Versioned? {
        val request = request("/account/sync").apply { if (knownVersion != null) header("If-None-Match", "\"$knownVersion\"") }.get().build()
        return call(request) { code, text -> if (code == 304) null else documentFrom(text) }
    }

    override suspend fun write(
        baseVersion: Long,
        text: String,
    ): WriteResult {
        val body = "{\"baseVersion\":$baseVersion,\"data\":$text}".toRequestBody(JSON_TYPE)
        return call(request("/account/sync").put(body).build(), alsoOk = 409) { code, answer ->
            if (code == 409) WriteResult.Conflict(documentFrom(answer)) else WriteResult.Saved(versionFrom(answer))
        }
    }

    /** Forgets this device's key on the server. */
    suspend fun signOut() {
        call(request("/account/signout").post(ByteArray(0).toRequestBody()).build()) { _, _ -> }
    }

    /** Deletes everything the account keeps, for every device. */
    suspend fun deleteAll() {
        call(request("/account").delete().build()) { _, _ -> }
    }

    private fun request(path: String) =
        Request
            .Builder()
            .url(base + path)
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")

    private suspend fun <T> call(
        request: Request,
        alsoOk: Int? = null,
        read: (Int, String) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            val response: Response =
                try {
                    http.newCall(request).execute()
                } catch (e: IOException) {
                    throw SyncException(SyncProblem.Offline, e)
                }
            response.use { r ->
                val text =
                    try {
                        r.body.string()
                    } catch (e: IOException) {
                        throw SyncException(SyncProblem.Offline, e)
                    }
                if (!r.isSuccessful && r.code != 304 && r.code != alsoOk) throw SyncException(problemOf(r.code))
                read(r.code, text)
            }
        }

    companion object {
        /** The owner's feed loader, which also keeps the accounts. */
        const val BASE_URL = "https://rss-reader-worker.wim-d57.workers.dev"

        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        fun problemOf(code: Int): SyncProblem =
            when (code) {
                401 -> SyncProblem.SignedOut
                503 -> SyncProblem.NotSetUp
                else -> SyncProblem.ServerError
            }

        /** `{"version": n, "data": {...} | null}` as a [Versioned] with the document's JSON text. */
        fun documentFrom(text: String): Versioned {
            val map = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: throw SyncException(SyncProblem.ServerError)
            val version = (map["version"] as? Number)?.toLong() ?: throw SyncException(SyncProblem.ServerError)
            return Versioned(version, map["data"]?.let { Json.write(it) })
        }

        fun versionFrom(text: String): Long =
            ((runCatching { Json.parse(text) }.getOrNull() as? Map<*, *>)?.get("version") as? Number)?.toLong()
                ?: throw SyncException(SyncProblem.ServerError)

        /** The live connection's address (see [LiveUpdates]). */
        fun liveUrl(
            key: String,
            base: String = BASE_URL,
        ): String = base.replaceFirst("http", "ws") + "/account/live?key=" + java.net.URLEncoder.encode(key, "UTF-8")
    }
}
