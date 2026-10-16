package io.github.impca201.rssreader.instapaper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** An Instapaper account as signed in under Settings › Save and share. */
data class InstapaperAccount(
    /** The email address or username. */
    val username: String,
    /** Empty for accounts without a password. */
    val password: String,
)

enum class InstapaperResult { Ok, WrongPassword, RateLimited, BadRequest, Offline, ServerError }

/**
 * Instapaper's Simple API (free, no app key): check an account and save a link to it. HTTPS only;
 * the account is sent with every request, as the Simple API requires.
 */
class Instapaper(
    private val http: OkHttpClient,
    private val apiBase: String = "https://www.instapaper.com",
) {
    /** Checks the email (or username) and password. */
    suspend fun authenticate(account: InstapaperAccount): InstapaperResult = post("/api/authenticate", account, emptyMap())

    /** Saves [url] (with its [title]) to the account's Unread list. */
    suspend fun add(
        account: InstapaperAccount,
        url: String,
        title: String,
    ): InstapaperResult = post("/api/add", account, addForm(url, title))

    private suspend fun post(
        path: String,
        account: InstapaperAccount,
        form: Map<String, String>,
    ): InstapaperResult =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
            val request =
                Request
                    .Builder()
                    .url(apiBase + path)
                    .header("Authorization", Credentials.basic(account.username, account.password))
                    .post(body)
                    .build()
            try {
                http.newCall(request).execute().use { resultOf(it.code) }
            } catch (_: IOException) {
                InstapaperResult.Offline
            }
        }

    companion object {
        /** The Simple API's answers: 200/201 done, 400 bad request or too many, 403 wrong account, 5xx their problem. */
        fun resultOf(code: Int): InstapaperResult =
            when (code) {
                200, 201 -> InstapaperResult.Ok
                403 -> InstapaperResult.WrongPassword
                400 -> InstapaperResult.BadRequest
                429 -> InstapaperResult.RateLimited
                else -> InstapaperResult.ServerError
            }

        /** The fields for /api/add; a blank title lets Instapaper take the page's own. */
        fun addForm(
            url: String,
            title: String,
        ): Map<String, String> = buildMap {
            put("url", url)
            if (title.isNotBlank()) put("title", title.trim())
        }
    }
}
