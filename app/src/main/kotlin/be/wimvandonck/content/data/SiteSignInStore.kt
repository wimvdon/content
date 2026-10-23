package be.wimvandonck.content.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import be.wimvandonck.content.feed.SiteSignIn
import be.wimvandonck.content.feed.SiteSignIns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

/**
 * The sites signed in to (#180), in their own file, which data_extraction_rules.xml keeps out of
 * backups and phone transfers: sign-in cookies are secrets. Never synced.
 */
private val Context.siteSignInDataStore: DataStore<Preferences> by preferencesDataStore(name = "site_signins")

class SiteSignInStore(
    context: Context,
    scope: CoroutineScope,
) {
    private val dataStore = context.applicationContext.siteSignInDataStore

    val signIns: StateFlow<List<SiteSignIn>> =
        dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { SiteSignIns.decode(it[SIGN_INS]) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Keeps [signIn], replacing the site's earlier one. */
    suspend fun save(signIn: SiteSignIn) =
        dataStore.edit { prefs ->
            val list = SiteSignIns.decode(prefs[SIGN_INS]).filter { it.site != signIn.site } + signIn
            prefs[SIGN_INS] = SiteSignIns.encode(list.sortedBy { it.site })
        }

    suspend fun remove(site: String) =
        dataStore.edit { prefs ->
            prefs[SIGN_INS] = SiteSignIns.encode(SiteSignIns.decode(prefs[SIGN_INS]).filter { it.site != site })
        }

    private companion object {
        val SIGN_INS = stringPreferencesKey("sign_ins")
    }
}
