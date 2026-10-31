package to.wim.content.sync

import to.wim.content.data.InboxMessage
import to.wim.content.data.LibraryRows
import to.wim.content.data.SharedArticle
import to.wim.content.data.SharedBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder

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
) : SyncTarget,
    ItemsTarget {
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

    override suspend fun readItems(since: Long): ItemsPage = call(request("/account/items?since=$since").get().build()) { _, text -> itemsPageFrom(text) }

    override suspend fun writeItems(rows: List<ItemChange>): ItemsWritten {
        val body = Json.write(mapOf("rows" to rows.map { linkedMapOf("key" to it.key, "baseVersion" to it.baseVersion, "data" to it.data) }))
        return call(request("/account/items").post(body.toRequestBody(JSON_TYPE)).build()) { _, text -> itemsWrittenFrom(text) }
    }

    /** The text stored for item [key] (one per saved article, shared by every device), or null when none is. */
    suspend fun text(key: String): String? =
        call(request("/account/texts?key=" + URLEncoder.encode(key, "UTF-8")).get().build(), alsoOk = 404) { code, body ->
            if (code == 404) null else htmlFrom(body)
        }

    /** Stores [html] as item [key]'s text unless one is there already; gives back the one that is stored. */
    suspend fun storeText(
        key: String,
        html: String,
    ): String? {
        val body = Json.write(linkedMapOf("key" to key, "html" to html)).toRequestBody(JSON_TYPE)
        return call(request("/account/texts").post(body).build()) { _, answer -> htmlFrom(answer) }
    }

    /** The name on [email]'s Gravatar profile (#234), which the feed loader looks up; "" when there is none. */
    suspend fun profileName(email: String): String {
        val body = Json.write(mapOf("email" to email.trim())).toRequestBody(JSON_TYPE)
        return call(request("/account/profile").post(body).build()) { _, text -> Gravatar.nameFrom(text) }
    }

    /** Gives the feed loader this phone's Firebase token for notifications (#235), or with null takes it back. */
    suspend fun setPush(token: String?) {
        val r = request("/account/push")
        val built = if (token == null) r.delete().build() else r.put(Json.write(linkedMapOf("kind" to "fcm", "token" to token)).toRequestBody(JSON_TYPE)).build()
        call(built) { _, _ -> }
    }

    /** Whether [email] has a Content account (#167), to say so before sharing with it. */
    suspend fun hasAccount(email: String): Boolean {
        val body = Json.write(mapOf("email" to email.trim())).toRequestBody(JSON_TYPE)
        return call(request("/account/share/check").post(body).build()) { _, text -> (runCatching { Json.parse(text) }.getOrNull() as? Map<*, *>)?.get("exists") == true }
    }

    /**
     * Shares [items] with the Content user at [to] (#167), as [from] (an address of this account).
     * False when the address has no account.
     */
    suspend fun share(
        to: String,
        from: SharedBy,
        items: List<SharedArticle>,
    ): Boolean {
        val body =
            Json.write(
                linkedMapOf(
                    "to" to to.trim(),
                    "from" to linkedMapOf("email" to from.email, "name" to from.name),
                    "items" to items.map { linkedMapOf("url" to it.url, "title" to it.title, "imageUrl" to it.imageUrl, "description" to it.description) },
                ),
            ).toRequestBody(JSON_TYPE)
        return call(request("/account/share").post(body).build(), alsoOk = 404) { code, _ -> code != 404 }
    }

    /** What other Content users shared with this account and no device has taken in yet. */
    suspend fun inbox(): List<InboxMessage> = call(request("/account/inbox").get().build()) { _, text -> inboxFrom(text) }

    /** Takes messages out of the inbox once they are in the library. */
    suspend fun inboxDone(ids: List<String>) {
        val body = Json.write(mapOf("ids" to ids)).toRequestBody(JSON_TYPE)
        call(request("/account/inbox/done").post(body).build()) { _, _ -> }
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
        const val BASE_URL = "https://content-worker.wim-d57.workers.dev"

        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        fun problemOf(code: Int): SyncProblem =
            when (code) {
                401 -> SyncProblem.SignedOut
                503 -> SyncProblem.NotSetUp
                else -> SyncProblem.ServerError
            }

        /** The messages of an inbox answer; ones that aren't one are left out. */
        fun inboxFrom(text: String): List<InboxMessage> {
            val list = ((runCatching { Json.parse(text) }.getOrNull() as? Map<*, *>)?.get("messages") as? List<*>).orEmpty()
            return list.mapNotNull { v ->
                val m = v as? Map<*, *> ?: return@mapNotNull null
                val id = m["id"] as? String ?: return@mapNotNull null
                val from = LibraryRows.sharedByOf(m["from"]) ?: return@mapNotNull null
                val items =
                    (m["items"] as? List<*>).orEmpty().mapNotNull { i ->
                        val a = i as? Map<*, *> ?: return@mapNotNull null
                        val url = a["url"] as? String ?: return@mapNotNull null
                        SharedArticle(url, a["title"] as? String ?: url, (a["imageUrl"] as? String)?.takeIf { it.startsWith("https://") }, a["description"] as? String ?: "")
                    }
                InboxMessage(id, from, items)
            }
        }

        /** The `html` of a stored-text answer, or null. */
        fun htmlFrom(text: String): String? = (runCatching { Json.parse(text) }.getOrNull() as? Map<*, *>)?.get("html") as? String

        /** `{"version": n, "data": {...} | null}` as a [Versioned] with the document's JSON text. */
        fun documentFrom(text: String): Versioned {
            val map = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: throw SyncException(SyncProblem.ServerError)
            val version = (map["version"] as? Number)?.toLong() ?: throw SyncException(SyncProblem.ServerError)
            return Versioned(version, map["data"]?.let { Json.write(it) })
        }

        fun versionFrom(text: String): Long =
            ((runCatching { Json.parse(text) }.getOrNull() as? Map<*, *>)?.get("version") as? Number)?.toLong()
                ?: throw SyncException(SyncProblem.ServerError)

        private fun rowsFrom(value: Any?): List<ItemRow> =
            (value as? List<*>).orEmpty().mapNotNull { r ->
                val m = r as? Map<*, *> ?: return@mapNotNull null
                val key = m["key"] as? String ?: return@mapNotNull null
                val version = (m["version"] as? Number)?.toLong() ?: return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                ItemRow(key, version, m["data"] as? Map<String, Any?>)
            }

        /** `{"version": n, "rows": [...], "more": bool}`. */
        fun itemsPageFrom(text: String): ItemsPage {
            val map = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: throw SyncException(SyncProblem.ServerError)
            val version = (map["version"] as? Number)?.toLong() ?: throw SyncException(SyncProblem.ServerError)
            return ItemsPage(version, rowsFrom(map["rows"]), map["more"] == true)
        }

        /** `{"version": n, "saved": [{"key", "version"}], "conflicts": [...]}`. */
        fun itemsWrittenFrom(text: String): ItemsWritten {
            val map = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: throw SyncException(SyncProblem.ServerError)
            val version = (map["version"] as? Number)?.toLong() ?: throw SyncException(SyncProblem.ServerError)
            val saved =
                (map["saved"] as? List<*>).orEmpty().mapNotNull { s ->
                    val m = s as? Map<*, *> ?: return@mapNotNull null
                    val key = m["key"] as? String ?: return@mapNotNull null
                    key to ((m["version"] as? Number)?.toLong() ?: return@mapNotNull null)
                }.toMap()
            return ItemsWritten(version, saved, rowsFrom(map["conflicts"]))
        }

        /** The live connection's address (see [LiveUpdates]). */
        fun liveUrl(
            key: String,
            base: String = BASE_URL,
        ): String = base.replaceFirst("http", "ws") + "/account/live?key=" + java.net.URLEncoder.encode(key, "UTF-8")
    }
}

/** An emailed link that couldn't be used (#166), with what to tell the user. */
class EmailLinkException(
    val notice: AccountNotice,
    cause: Throwable? = null,
) : Exception(notice.name, cause)

/** What an emailed link gave: a device key for a sign-in (null when it added the address), and the address. */
data class EmailLinkResult(
    val key: String?,
    val email: String,
)

/**
 * Signing in with an emailed link at the feed loader (#166, worker/src/email.ts): asking for a
 * link, and using one. A [SyncException] says why asking failed.
 */
class EmailLinks(
    private val http: OkHttpClient,
    private val base: String = AccountClient.BASE_URL,
) {
    /** Emails a sign-in link to [email]; with [key], a link that adds it to that account. */
    suspend fun send(
        email: String,
        key: String?,
    ) {
        val body = Json.write(mapOf("email" to email.trim())).toRequestBody(JSON_TYPE)
        val request =
            Request
                .Builder()
                .url(base + if (key != null) "/account/email/add" else "/account/email/start")
                .apply { if (key != null) header("Authorization", "Bearer $key") }
                .post(body)
                .build()
        val code = execute(request) { it.code }
        if (code !in 200..299) throw SyncException(if (code == 400) SyncProblem.ServerError else AccountClient.problemOf(code))
    }

    suspend fun finish(token: String): EmailLinkResult {
        val body = Json.write(mapOf("token" to token)).toRequestBody(JSON_TYPE)
        val request = Request.Builder().url("$base/account/email/finish").post(body).build()
        val (code, text) =
            try {
                execute(request) { it.code to it.body.string() }
            } catch (e: SyncException) {
                throw EmailLinkException(AccountNotice.Failed, e)
            }
        when (code) {
            410 -> throw EmailLinkException(AccountNotice.LinkExpired)
            409 -> throw EmailLinkException(AccountNotice.EmailTaken)
        }
        return (if (code == 200) resultFrom(text) else null) ?: throw EmailLinkException(AccountNotice.Failed)
    }

    private suspend fun <T> execute(
        request: Request,
        read: (Response) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use(read)
            } catch (e: IOException) {
                throw SyncException(SyncProblem.Offline, e)
            }
        }

    companion object {
        /** Where an emailed link opens: the web app's address, which this app opens as an App Link. */
        const val LINK_PREFIX = "https://content.wim.to/signin"

        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        /** The token of an emailed link ("…/signin#token=…"), or null when it isn't one. */
        fun tokenOf(link: String): String? {
            if (!link.startsWith(LINK_PREFIX)) return null
            val fragment = link.substringAfter('#', "")
            return fragment.split('&').firstOrNull { it.startsWith("token=") }?.removePrefix("token=")?.takeIf { it.isNotEmpty() }
        }

        /** The answer of a used link: {"key", "email"} or {"email"}. */
        fun resultFrom(text: String): EmailLinkResult? {
            val map = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: return null
            val email = (map["email"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
            return EmailLinkResult((map["key"] as? String)?.takeIf { it.isNotEmpty() }, email)
        }
    }
}
