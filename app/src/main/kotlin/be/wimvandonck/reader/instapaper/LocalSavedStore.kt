package be.wimvandonck.reader.instapaper

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

/** The articles saved without an Instapaper account, in their own file (in backups, like the feeds). */
private val Context.savedDataStore: DataStore<Preferences> by preferencesDataStore(name = "saved")

class LocalSavedStore(
    context: Context,
) : LocalSavedStorage {
    private val dataStore = context.applicationContext.savedDataStore

    override suspend fun read(): LocalSavedData =
        LocalSavedCodec.decode(
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .first()[KEY],
        )

    override suspend fun update(change: (LocalSavedData) -> LocalSavedData): LocalSavedData {
        var result = LocalSavedData()
        dataStore.edit { prefs ->
            result = change(LocalSavedCodec.decode(prefs[KEY]))
            prefs[KEY] = LocalSavedCodec.encode(result)
        }
        return result
    }

    private companion object {
        val KEY = stringPreferencesKey("articles")
    }
}
