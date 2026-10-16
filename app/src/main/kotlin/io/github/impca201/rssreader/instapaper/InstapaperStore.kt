package io.github.impca201.rssreader.instapaper

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

/** Its own file, which Android backup and phone-to-phone transfer leave out (res/xml/data_extraction_rules.xml). */
private val Context.instapaperDataStore: DataStore<Preferences> by preferencesDataStore(name = "instapaper")

/** The signed-in Instapaper account, on this device only. */
class InstapaperStore(
    context: Context,
    scope: CoroutineScope,
) {
    private val dataStore = context.applicationContext.instapaperDataStore

    val account: StateFlow<InstapaperAccount?> =
        dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map { p -> p[USERNAME]?.let { InstapaperAccount(it, p[PASSWORD].orEmpty()) } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun signIn(account: InstapaperAccount) {
        dataStore.edit {
            it[USERNAME] = account.username
            it[PASSWORD] = account.password
        }
    }

    suspend fun signOut() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
    }
}
