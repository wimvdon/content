package to.wim.content.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// The library's tables (#106). Plain data classes, turned into the Library and back by LibraryRows.

@Entity(tableName = "folders")
data class FolderRow(
    @PrimaryKey val id: String,
    val position: Int,
    val title: String,
    val icon: String,
)

@Entity(tableName = "feeds")
data class FeedRow(
    @PrimaryKey val url: String,
    val position: Int,
    val title: String,
    val siteUrl: String?,
    val folderId: String?,
    val iconUrl: String?,
    val renamed: Boolean,
    /** Its tags as JSON (#187). */
    @ColumnInfo(defaultValue = "[]") val tags: String = "[]",
)

@Entity(tableName = "articles", indices = [Index("feedUrl")])
data class ArticleRow(
    /** "<feed url> <article id>", as Article.key. */
    @PrimaryKey val key: String,
    val feedUrl: String,
    val articleId: String,
    val title: String,
    val link: String?,
    val author: String?,
    val html: String,
    val publishedAt: Long?,
    val fetchedAt: Long,
    val read: Boolean,
    val imageUrl: String?,
)

/** An item: its memberships as columns (null when not a member), tags and highlights as JSON. */
@Entity(tableName = "items")
data class ItemRow(
    @PrimaryKey val key: String,
    val url: String,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val feedUrl: String?,
    val articleId: String?,
    val createdAt: Long,
    /** "toread", "archived" or null (not on the reading list). */
    val readingState: String?,
    val readingAddedAt: Long?,
    /** When it was kept in Bookmarks, or null (not a bookmark). */
    val bookmarkAddedAt: Long?,
    val collection: String?,
    /** The folder it is filed in (#119), or null. */
    @ColumnInfo(defaultValue = "NULL") val folder: String? = null,
    /** The service it is kept in (#112), or null. */
    @ColumnInfo(defaultValue = "NULL") val service: String? = null,
    val liked: Boolean,
    val tags: String,
    val note: String,
    val highlights: String,
    val progress: Float,
    /** Who shared it (#167), as {"email", "name"}, or null. */
    @ColumnInfo(defaultValue = "NULL") val sharedBy: String? = null,
)

/**
 * A version of an item's text and where it came from ("feed" or "extracted"; Instapaper's
 * text, kept before #134, is deleted at start), kept on this device only: article text is never synced (#106).
 */
@Entity(tableName = "item_texts", primaryKeys = ["itemKey", "source"])
data class ItemTextRow(
    val itemKey: String,
    val source: String,
    val html: String,
    val savedAt: Long,
)

/** A read mark from another device for an article this one doesn't have (yet), see Library.readElsewhere. */
@Entity(tableName = "read_elsewhere")
data class ReadElsewhereRow(
    @PrimaryKey val key: String,
    val time: Long,
)

/**
 * A row of per-item sync as this device last synced it (the base for merging, sync/ItemSync.kt):
 * its key, the account's items version of it, and its JSON data.
 */
@Entity(tableName = "sync_rows")
data class SyncRowRow(
    @PrimaryKey val key: String,
    val version: Long,
    val data: String,
)

/** An article as a connected service last had it (services/ServiceSync.kt): the base for merging, as JSON. */
@Entity(tableName = "service_rows", primaryKeys = ["service", "itemKey"])
data class ServiceRowRow(
    val service: String,
    val itemKey: String,
    val data: String,
)

/** Small facts about the database itself, such as whether the old files were moved in. */
@Entity(tableName = "meta")
data class MetaRow(
    @PrimaryKey val name: String,
    val value: String,
)
