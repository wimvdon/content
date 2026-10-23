package be.wimvandonck.content.instapaper

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

/** The articles saved without an Instapaper account before #106, in their own file; read once to move them in. */
private val Context.savedDataStore: DataStore<Preferences> by preferencesDataStore(name = "saved")

class LocalSavedStore(
    context: Context,
) {
    private val dataStore = context.applicationContext.savedDataStore

    suspend fun read(): LocalSavedData =
        LocalSavedCodec.decode(
            dataStore.data
                .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                .first()[KEY],
        )

    private companion object {
        val KEY = stringPreferencesKey("articles")
    }
}
