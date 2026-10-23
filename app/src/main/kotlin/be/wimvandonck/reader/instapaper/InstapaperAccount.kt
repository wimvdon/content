package be.wimvandonck.reader.instapaper

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
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * The token and the account key are kept in their own file, which data_extraction_rules.xml keeps
 * out of backups and phone transfers. Each device signs in itself.
 */
private val Context.instapaperDataStore: DataStore<Preferences> by preferencesDataStore(name = "instapaper")

sealed interface InstapaperState {
    /** Not read from storage yet. */
    data object Loading : InstapaperState

    data object SignedOut : InstapaperState

    data class SignedIn(
        val session: InstapaperSession,
    ) : InstapaperState
}

/** The result of coming back from Instapaper's sign-in page, shown once. */
enum class InstapaperNotice { SignedIn, Failed }

/**
 * The user's Instapaper account on this phone: sign in and sign out. Its reading list is synced by
 * services/ServiceManager.kt.
 */
class InstapaperAccount(
    context: Context,
    private val scope: CoroutineScope,
    private val http: OkHttpClient,
) {
    private val dataStore = context.applicationContext.instapaperDataStore

    val state: StateFlow<InstapaperState> =
        dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { prefs -> stateOf(prefs) }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, InstapaperState.Loading)

    private val noticeFlow = MutableStateFlow<InstapaperNotice?>(null)
    val notice: StateFlow<InstapaperNotice?> = noticeFlow

    fun clearNotice() {
        noticeFlow.value = null
    }

    /** Starts a sign-in: remembers a fresh state and returns Instapaper's sign-in address. */
    suspend fun begin(): String {
        val state = InstapaperAuth.newState()
        dataStore.edit { it[PENDING_STATE] = state }
        return InstapaperAuth.authorizeUrl(state)
    }

    /** Finishes a sign-in with the address Instapaper sent the user back to. */
    suspend fun finish(link: String) {
        val expected = dataStore.data.first()[PENDING_STATE]
        dataStore.edit { it.remove(PENDING_STATE) }
        val answer = InstapaperAuth.parseCallback(link)
        if (answer !is InstapaperAuth.Callback.Code || expected.isNullOrEmpty() || answer.state != expected) {
            noticeFlow.value = InstapaperNotice.Failed
            return
        }
        noticeFlow.value =
            try {
                val session = InstapaperAuth.exchange(http, answer.code)
                dataStore.edit { prefs ->
                    prefs[TOKEN] = session.token
                    prefs[USERNAME] = session.username
                    if (session.userId != null) prefs[USER_ID] = session.userId else prefs.remove(USER_ID)
                    // Instapaper is only a connected service (#215): the account it would open isn't kept.
                    prefs.remove(ACCOUNT_KEY)
                }
                InstapaperNotice.SignedIn
            } catch (e: InstapaperException) {
                InstapaperNotice.Failed
            }
    }

    /** The signed-in account once read from storage, or null. */
    suspend fun session(): InstapaperSession? = (state.first { it != InstapaperState.Loading } as? InstapaperState.SignedIn)?.session

    /**
     * The Content account key an Instapaper sign-in from before #166 opened, handed over once: the
     * Content account keeps it from then on (sync/ContentAccount.kt). New sign-ins keep none (#215).
     */
    suspend fun takeAccountKey(): String? {
        var key: String? = null
        dataStore.edit { prefs ->
            key = prefs[ACCOUNT_KEY]?.takeIf { it.isNotEmpty() }
            prefs.remove(ACCOUNT_KEY)
        }
        return key
    }

    /** Disconnects Instapaper on this device. The Content account stays signed in (#166). */
    suspend fun signOut() {
        dataStore.edit { prefs ->
            prefs.remove(TOKEN)
            prefs.remove(USER_ID)
            prefs.remove(USERNAME)
            prefs.remove(ACCOUNT_KEY)
        }
    }

    private fun stateOf(prefs: Preferences): InstapaperState {
        val token = prefs[TOKEN]
        return if (token.isNullOrEmpty()) {
            InstapaperState.SignedOut
        } else {
            InstapaperState.SignedIn(InstapaperSession(token, prefs[USER_ID], prefs[USERNAME].orEmpty(), prefs[ACCOUNT_KEY]))
        }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("token")
        val USERNAME = stringPreferencesKey("username")
        val USER_ID = longPreferencesKey("user_id")
        val PENDING_STATE = stringPreferencesKey("pending_state")
        val ACCOUNT_KEY = stringPreferencesKey("account_key")
    }
}
