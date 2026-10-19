package be.wimvandonck.reader.data.db

import android.content.Context
import androidx.room.AutoMigration
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
    val readElsewhere: List<ReadElsewhereRow> = emptyList(),
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

    @Query("SELECT * FROM read_elsewhere")
    protected abstract suspend fun readElsewhere(): List<ReadElsewhereRow>

    @Transaction
    open suspend fun read(): LibraryTables = LibraryTables(folders(), feeds(), articles(), items(), readElsewhere())

    @Query("DELETE FROM folders")
    protected abstract suspend fun clearFolders()

    @Query("DELETE FROM feeds")
    protected abstract suspend fun clearFeeds()

    @Query("DELETE FROM articles")
    protected abstract suspend fun clearArticles()

    @Query("DELETE FROM items")
    protected abstract suspend fun clearItems()

    @Query("DELETE FROM read_elsewhere")
    protected abstract suspend fun clearReadElsewhere()

    @Upsert
    protected abstract suspend fun upsertReadElsewhere(rows: List<ReadElsewhereRow>)

    @Query("DELETE FROM read_elsewhere WHERE `key` IN (:keys)")
    protected abstract suspend fun deleteReadElsewhere(keys: List<String>)

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
        changes.deletedReadElsewhere.chunked(CHUNK).forEach { deleteReadElsewhere(it) }
        changes.readElsewhere.entries.chunked(CHUNK).forEach { batch -> upsertReadElsewhere(batch.map { ReadElsewhereRow(it.key, it.value) }) }
    }

    @Query("SELECT * FROM sync_rows")
    abstract suspend fun syncRows(): List<SyncRowRow>

    @Upsert
    protected abstract suspend fun upsertSyncRows(rows: List<SyncRowRow>)

    @Query("DELETE FROM sync_rows WHERE `key` IN (:keys)")
    protected abstract suspend fun deleteSyncRows(keys: List<String>)

    @Query("DELETE FROM sync_rows")
    protected abstract suspend fun clearSyncRows()

    /** Stores what changed in the synced rows and the items version, all or nothing; [clear] forgets them all first. */
    @Transaction
    open suspend fun writeSyncRows(
        put: List<SyncRowRow>,
        delete: List<String>,
        version: Long,
        clear: Boolean = false,
    ) {
        if (clear) clearSyncRows()
        delete.chunked(CHUNK).forEach { deleteSyncRows(it) }
        put.chunked(CHUNK).forEach { upsertSyncRows(it) }
        putMeta(MetaRow(ITEMS_VERSION, version.toString()))
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
        clearReadElsewhere()
        write(changes)
        putMeta(MetaRow(name, "1"))
    }

    companion object {
        /** SQLite allows at most 999 values in one statement. */
        private const val CHUNK = 500

        /** The account's items version this device last synced to. */
        const val ITEMS_VERSION = "items-version"
    }
}

@Database(
    entities = [
        FolderRow::class, FeedRow::class, ArticleRow::class, ItemRow::class, ItemTextRow::class, MetaRow::class, ReadElsewhereRow::class,
        SyncRowRow::class,
    ],
    // 2 added read_elsewhere and sync_rows (per-item sync, #106).
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        fun open(context: Context): LibraryDatabase = Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "library.db").build()
    }
}
