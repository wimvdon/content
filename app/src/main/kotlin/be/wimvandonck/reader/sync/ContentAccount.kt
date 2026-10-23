package be.wimvandonck.reader.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
 * This device's key to the Content account, and the account's email address, in their own file,
 * which data_extraction_rules.xml keeps out of backups and phone transfers.
 */
private val Context.accountDataStore: DataStore<Preferences> by preferencesDataStore(name = "account")

/** Signed in to a Content account: this device's key, and the email address when there is one. */
data class AccountSession(
    val key: String,
    val email: String? = null,
)

sealed interface AccountState {
    /** Not read from storage yet. */
    data object Loading : AccountState

    data object SignedOut : AccountState

    data class SignedIn(
        val session: AccountSession,
    ) : AccountState
}

/** The result of an emailed link opened on this device, shown once. */
enum class AccountNotice { SignedIn, EmailAdded, LinkExpired, EmailTaken, Failed }

/**
 * The Content account on this device (#166): signed in with an emailed link, or opened by signing
 * in with Instapaper (docs/account.md). Instapaper is only a connected service: connecting or
 * disconnecting it doesn't change the account. SyncManager syncs with [state]'s key.
 */
class ContentAccount(
    context: Context,
    scope: CoroutineScope,
    private val http: OkHttpClient,
    private val links: EmailLinks = EmailLinks(http),
) {
    private val dataStore = context.applicationContext.accountDataStore

    val state: StateFlow<AccountState> =
        dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { prefs -> prefs[KEY]?.takeIf { it.isNotEmpty() }?.let { AccountState.SignedIn(AccountSession(it, prefs[EMAIL])) } ?: AccountState.SignedOut }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, AccountState.Loading)

    private val noticeFlow = MutableStateFlow<AccountNotice?>(null)
    val notice: StateFlow<AccountNotice?> = noticeFlow

    fun clearNotice() {
        noticeFlow.value = null
    }

    /** The signed-in account once read from storage, or null. */
    suspend fun session(): AccountSession? = (state.first { it != AccountState.Loading } as? AccountState.SignedIn)?.session

    /** Emails a sign-in link to [email] (or, signed in, a link that adds it to this account). */
    suspend fun sendLink(email: String) {
        links.send(email, session()?.key)
    }

    /** Finishes an emailed link opened on this device. */
    suspend fun finish(link: String) {
        val token = EmailLinks.tokenOf(link)
        if (token == null) {
            noticeFlow.value = AccountNotice.Failed
            return
        }
        noticeFlow.value =
            try {
                val result = links.finish(token)
                if (result.key != null) {
                    dataStore.edit {
                        it[KEY] = result.key
                        it[EMAIL] = result.email
                    }
                    AccountNotice.SignedIn
                } else {
                    dataStore.edit { it[EMAIL] = result.email }
                    AccountNotice.EmailAdded
                }
            } catch (e: EmailLinkException) {
                e.notice
            }
    }

    /**
     * An account opened by signing in with Instapaper: kept when this device has none yet (signed
     * in, Instapaper is just connected). Also how accounts from before #166 move here.
     */
    suspend fun adopt(key: String) {
        dataStore.edit { if (it[KEY].isNullOrEmpty()) it[KEY] = key }
    }

    /** Forgets this device's key here (SyncManager forgets what was synced). */
    suspend fun forget() {
        dataStore.edit {
            it.remove(KEY)
            it.remove(EMAIL)
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("key")
        val EMAIL = stringPreferencesKey("email")
    }
}
