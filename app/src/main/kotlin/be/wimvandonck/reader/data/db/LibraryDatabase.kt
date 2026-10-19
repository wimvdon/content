package be.wimvandonck.reader.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import be.wimvandonck.reader.data.LibraryChanges
import be.wimvandonck.reader.data.LibraryRows

/** Everything in the library tables, read in one transaction. */
data class LibraryTables(
    val folders: List<FolderRow>,
    val feeds: List<FeedRow>,
    val articles: List<ArticleRow>,
    val items: List<ItemRow>,
)

@Dao
abstract class LibraryDao {
    @Query("SELECT * FROM folders ORDER BY position")
    protected abstract suspend fun folders(): List<FolderRow>

    @Query("SELECT * FROM feeds ORDER BY position")
    protected abstract suspend fun feeds(): List<FeedRow>

    @Query("SELECT * FROM articles")
    protected abstract suspend fun articles(): List<ArticleRow>

    @Query("SELECT * FROM items")
    protected abstract suspend fun items(): List<ItemRow>

    @Transaction
    open suspend fun read(): LibraryTables = LibraryTables(folders(), feeds(), articles(), items())

    @Query("DELETE FROM folders")
    protected abstract suspend fun clearFolders()

    @Query("DELETE FROM feeds")
    protected abstract suspend fun clearFeeds()

    @Query("DELETE FROM articles")
    protected abstract suspend fun clearArticles()

    @Query("DELETE FROM items")
    protected abstract suspend fun clearItems()

    @Upsert
    protected abstract suspend fun upsertFolders(rows: List<FolderRow>)

    @Upsert
    protected abstract suspend fun upsertFeeds(rows: List<FeedRow>)

    @Upsert
    protected abstract suspend fun upsertArticles(rows: List<ArticleRow>)

    @Upsert
    protected abstract suspend fun upsertItems(rows: List<ItemRow>)

    @Query("DELETE FROM articles WHERE `key` IN (:keys)")
    protected abstract suspend fun deleteArticles(keys: List<String>)

    @Query("DELETE FROM items WHERE `key` IN (:keys)")
    protected abstract suspend fun deleteItems(keys: List<String>)

    @Query("DELETE FROM item_texts WHERE itemKey IN (:keys)")
    protected abstract suspend fun deleteTexts(keys: List<String>)

    @Query("SELECT * FROM item_texts WHERE itemKey = :key")
    abstract suspend fun texts(key: String): List<ItemTextRow>

    @Upsert
    abstract suspend fun putText(row: ItemTextRow)

    @Query("SELECT value FROM meta WHERE name = :name")
    abstract suspend fun meta(name: String): String?

    @Upsert
    protected abstract suspend fun putMeta(row: MetaRow)

    /** Writes what changed between two libraries, all or nothing. */
    @Transaction
    open suspend fun write(changes: LibraryChanges) {
        changes.folders?.let { folders ->
            clearFolders()
            upsertFolders(folders.mapIndexed(LibraryRows::folder))
        }
        changes.feeds?.let { feeds ->
            clearFeeds()
            upsertFeeds(feeds.mapIndexed(LibraryRows::feed))
        }
        changes.deletedArticles.chunked(CHUNK).forEach { deleteArticles(it) }
        changes.articles.chunked(CHUNK).forEach { upsertArticles(it.map(LibraryRows::article)) }
        changes.deletedItems.chunked(CHUNK).forEach {
            deleteItems(it)
            deleteTexts(it)
        }
        changes.items.chunked(CHUNK).forEach { upsertItems(it.map(LibraryRows::item)) }
    }

    /** Replaces everything (moving the old files in), and records [name] so it happens once. */
    @Transaction
    open suspend fun replaceAll(
        changes: LibraryChanges,
        name: String,
    ) {
        clearFolders()
        clearFeeds()
        clearArticles()
        clearItems()
        write(changes)
        putMeta(MetaRow(name, "1"))
    }

    private companion object {
        /** SQLite allows at most 999 values in one statement. */
        const val CHUNK = 500
    }
}

@Database(
    entities = [FolderRow::class, FeedRow::class, ArticleRow::class, ItemRow::class, ItemTextRow::class, MetaRow::class],
    version = 1,
    exportSchema = true,
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        fun open(context: Context): LibraryDatabase = Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "library.db").build()
    }
}
