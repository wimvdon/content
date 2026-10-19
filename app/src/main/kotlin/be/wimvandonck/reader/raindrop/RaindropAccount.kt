package be.wimvandonck.reader.raindrop

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Raindrop's tokens are kept in their own file, which data_extraction_rules.xml keeps out of
 * backups and phone transfers, as Instapaper's. Each device connects itself.
 */
private val Context.raindropDataStore: DataStore<Preferences> by preferencesDataStore(name = "raindrop")

enum class RaindropState { Loading, SignedOut, SignedIn }

/** The result of coming back from Raindrop's sign-in page, shown once. */
enum class RaindropNotice { SignedIn, Failed }

/**
 * Raindrop.io on this phone (#112): connect and disconnect, and a current token for its API.
 * Saved's sync with it is services/ServiceManager.kt's.
 */
class RaindropAccount(
    context: Context,
    scope: CoroutineScope,
    private val http: OkHttpClient,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dataStore = context.applicationContext.raindropDataStore

    val state: StateFlow<RaindropState> =
        dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { prefs -> if (prefs[ACCESS].isNullOrEmpty()) RaindropState.SignedOut else RaindropState.SignedIn }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, RaindropState.Loading)

    private val noticeFlow = MutableStateFlow<RaindropNotice?>(null)
    val notice: StateFlow<RaindropNotice?> = noticeFlow

    fun clearNotice() {
        noticeFlow.value = null
    }

    /** One refresh at a time. */
    private val refreshLock = Mutex()

    /** Starts connecting: remembers a fresh state and returns the address to open in the browser. */
    suspend fun begin(): String {
        val state = RaindropAuth.newState()
        dataStore.edit { it[PENDING_STATE] = state }
        return RaindropAuth.authorizeUrl(state)
    }

    /** Finishes connecting with the address Raindrop sent the user back to. */
    suspend fun finish(link: String) {
        val expected = dataStore.data.first()[PENDING_STATE]
        dataStore.edit { it.remove(PENDING_STATE) }
        val answer = RaindropAuth.parseCallback(link)
        val code = (answer as? RaindropAuth.Callback.Code)
        // The state proves the answer belongs to this sign-in (Raindrop may leave it out: then one must be pending).
        if (code == null || expected.isNullOrEmpty() || (code.state != null && code.state != expected)) {
            noticeFlow.value = RaindropNotice.Failed
            return
        }
        val exchanged = RaindropAuth.exchange(http, mapOf("code" to code.code), RaindropAuth.TOKEN_URL)
        if (exchanged !is RaindropAuth.Exchange.Done) {
            noticeFlow.value = RaindropNotice.Failed
            return
        }
        store(exchanged.tokens)
        noticeFlow.value = RaindropNotice.SignedIn
    }

    /** The token to call Raindrop with, renewed a day before it runs out; null when not connected. */
    suspend fun token(): String? {
        val prefs = dataStore.data.first()
        val access = prefs[ACCESS]?.takeIf { it.isNotEmpty() } ?: return null
        val expiresAt = prefs[EXPIRES_AT] ?: Long.MAX_VALUE
        return if (expiresAt - now() < RENEW_BEFORE_MS) refresh() ?: access else access
    }

    /** New tokens with the refresh token; disconnects when Raindrop no longer accepts it. */
    suspend fun refresh(): String? =
        refreshLock.withLock {
            val refreshToken = dataStore.data.first()[REFRESH]?.takeIf { it.isNotEmpty() } ?: return@withLock null
            when (val tokens = RaindropAuth.exchange(http, mapOf("refresh_token" to refreshToken), RaindropAuth.REFRESH_URL)) {
                is RaindropAuth.Exchange.Done -> {
                    store(tokens.tokens)
                    tokens.tokens.access
                }
                RaindropAuth.Exchange.Refused -> {
                    signOut()
                    null
                }
                // Offline: try again later with what there is.
                RaindropAuth.Exchange.Failed -> null
            }
        }

    private suspend fun store(tokens: RaindropAuth.Tokens) {
        dataStore.edit { prefs ->
            prefs[ACCESS] = tokens.access
            if (tokens.refresh.isNotEmpty()) prefs[REFRESH] = tokens.refresh
            if (tokens.expiresInSeconds != null) prefs[EXPIRES_AT] = now() + tokens.expiresInSeconds * 1000 else prefs.remove(EXPIRES_AT)
        }
    }

    /** Disconnects Raindrop on this device (ServiceManager forgets what was synced). */
    suspend fun signOut() {
        dataStore.edit { prefs ->
            prefs.remove(ACCESS)
            prefs.remove(REFRESH)
            prefs.remove(EXPIRES_AT)
        }
    }

    private companion object {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at")
        val PENDING_STATE = stringPreferencesKey("pending_state")

        /** Tokens are renewed when less than a day is left. */
        const val RENEW_BEFORE_MS = 24 * 60 * 60 * 1000L
    }
}
