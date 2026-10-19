package be.wimvandonck.reader.data

import android.content.Context
import androidx.datastore.dataStoreFile
import be.wimvandonck.reader.instapaper.LocalSavedStore
import be.wimvandonck.reader.instapaper.SavedItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The library as versions before #106 kept it: feeds and articles in `datastore/library.txt`
 * ([LibraryCodec]) and Saved without an account in the `saved` DataStore ([LocalSavedStore]).
 * [LibraryRepository] moves them into the database once ([SavedItems.migrate]) and then deletes them.
 */
open class LegacyLibrary(
    context: Context,
) {
    private val app = context.applicationContext
    private val libraryFile: File get() = app.dataStoreFile("library.txt")
    private val savedFile: File get() = app.dataStoreFile("saved.preferences_pb")

    /** Everything from the old files, as one library (empty on a new install). */
    open suspend fun read(): Library {
        val library =
            withContext(Dispatchers.IO) {
                try {
                    if (libraryFile.exists()) LibraryCodec.decode(libraryFile.readText()) else Library()
                } catch (e: IOException) {
                    Library()
                }
            }
        val saved = if (savedFile.exists()) LocalSavedStore(app).read() else null
        return if (saved == null) library else SavedItems.migrate(library, saved)
    }

    /** Deletes the old files once their contents are in the database. */
    open suspend fun delete() {
        withContext(Dispatchers.IO) {
            libraryFile.delete()
            File(libraryFile.path + ".tmp").delete()
            savedFile.delete()
        }
    }
}
