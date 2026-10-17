package be.wimvandonck.reader.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Access to the app's hidden folder in the user's Google Drive, with Google's authorization
 * screen. Only the drive.appdata scope is asked: the app can't see the user's other files.
 */
class GoogleAuth(
    context: Context,
) {
    private val context = context.applicationContext
    private val client get() = Identity.getAuthorizationClient(context)

    /** Either an access token, or the screen the user has to approve first. */
    sealed interface Result {
        data class Token(val token: String) : Result

        data class NeedsApproval(val intent: PendingIntent) : Result
    }

    suspend fun authorize(): Result = toResult(request())

    /** The answer of the approval screen. */
    fun fromIntent(data: Intent?): Result? = data?.let { runCatching { toResult(client.getAuthorizationResultFromIntent(it)) }.getOrNull() }

    /** A token without asking the user; throws [SyncException] when approval is needed. */
    suspend fun token(): String =
        when (val result = authorize()) {
            is Result.Token -> result.token
            is Result.NeedsApproval -> throw SyncException(SyncProblem.NeedsApproval)
        }

    /** Forgets a token Google refused, so the next [authorize] gets a fresh one. */
    suspend fun forget(token: String) {
        withContext(Dispatchers.IO) { runCatching { GoogleAuthUtil.clearToken(context, token) } }
    }

    private fun toResult(result: AuthorizationResult): Result {
        val pending = result.pendingIntent
        val token = result.accessToken
        return when {
            result.hasResolution() && pending != null -> Result.NeedsApproval(pending)
            token != null -> Result.Token(token)
            else -> throw SyncException(SyncProblem.NeedsApproval)
        }
    }

    private suspend fun request(): AuthorizationResult =
        suspendCancellableCoroutine { cont ->
            val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(GoogleDrive.SCOPE))).build()
            client
                .authorize(request)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(SyncException(authorizationProblem((it as? ApiException)?.statusCode), it)) }
        }
}
