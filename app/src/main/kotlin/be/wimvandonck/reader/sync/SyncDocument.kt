package be.wimvandonck.reader.sync

import be.wimvandonck.reader.data.IconCatalog
import be.wimvandonck.reader.data.Library

/** A folder as synced: everything the user chose about it. */
data class SyncFolder(
    val id: String,
    val title: String,
    val icon: String,
)

/** A feed as synced. Articles themselves are not synced; every device downloads them. */
data class SyncFeed(
    val url: String,
    val title: String,
    val renamed: Boolean,
    val folderId: String?,
    val siteUrl: String?,
    val iconUrl: String?,
)

/**
 * The sync file shared by every device and the web app: folders and feeds in the user's order,
 * the read articles (article key → the article's date, used to forget old ones) and the settings
 * (shared name → text value, see [SyncSettings]). The format is documented in docs/sync.md.
 */
data class SyncDocument(
    val folders: List<SyncFolder> = emptyList(),
    val feeds: List<SyncFeed> = emptyList(),
    val read: Map<String, Long> = emptyMap(),
    val settings: Map<String, String> = emptyMap(),
) {
    /** Leaves out read marks of removed feeds and of articles older than [keepSince]. */
    fun pruned(keepSince: Long): SyncDocument {
        val urls = feeds.mapTo(HashSet()) { it.url }
        return copy(read = read.filter { (key, time) -> time >= keepSince && feedUrlOf(key) in urls })
    }

    companion object {
        const val FORMAT = "rss-reader-sync"
        // 2 added settings. Older apps leave a version 2 file alone and ask to be updated, rather
        // than writing it back without the settings.
        const val VERSION = 2L

        /** Read marks older than this are dropped from the file (feeds rarely go back further). */
        const val KEEP_READ_MS = 120L * 24 * 60 * 60 * 1000

        /** The feed address in an article key ("<feed url> <article id>"). */
        fun feedUrlOf(key: String): String = key.substringBefore(' ')

        /** What [library] holds that is synced. */
        fun of(library: Library): SyncDocument =
            SyncDocument(
                folders = library.folders.map { SyncFolder(it.id, it.title, it.icon) },
                feeds = library.feeds.map { SyncFeed(it.url, it.title, it.renamed, it.folderId, it.siteUrl, it.iconUrl) },
                read = library.articles.filter { it.read }.associate { it.key to it.sortTime },
            )

        fun encode(doc: SyncDocument): String =
            Json.write(
                linkedMapOf(
                    "format" to FORMAT,
                    "version" to VERSION,
                    "folders" to doc.folders.map { linkedMapOf("id" to it.id, "title" to it.title, "icon" to it.icon) },
                    "feeds" to
                        doc.feeds.map {
                            linkedMapOf(
                                "url" to it.url,
                                "title" to it.title,
                                "renamed" to it.renamed,
                                "folderId" to it.folderId,
                                "siteUrl" to it.siteUrl,
                                "iconUrl" to it.iconUrl,
                            )
                        },
                    "read" to doc.read.toSortedMap(),
                    "settings" to doc.settings.toSortedMap(),
                ),
            )

        /**
         * Reads a sync file. Returns null when it isn't one, or is from a newer version of the
         * format this app doesn't know (it is then left alone). Broken entries are skipped.
         */
        fun decode(text: String): SyncDocument? {
            val root = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: return null
            if (root["format"] != FORMAT) return null
            val version = root["version"] as? Long ?: return null
            if (version > VERSION) return null
            val folders =
                (root["folders"] as? List<*>).orEmpty().mapNotNull { item ->
                    val m = item as? Map<*, *> ?: return@mapNotNull null
                    val id = (m["id"] as? String)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val title = (m["title"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    SyncFolder(id, title, IconCatalog.fromStored(m["icon"] as? String ?: IconCatalog.DEFAULT))
                }.distinctBy { it.id }
            val folderIds = folders.mapTo(HashSet()) { it.id }
            val feeds =
                (root["feeds"] as? List<*>).orEmpty().mapNotNull { item ->
                    val m = item as? Map<*, *> ?: return@mapNotNull null
                    val url = (m["url"] as? String)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    SyncFeed(
                        url = url,
                        title = (m["title"] as? String)?.ifBlank { null } ?: url,
                        renamed = m["renamed"] == true,
                        folderId = (m["folderId"] as? String)?.takeIf { it in folderIds },
                        siteUrl = (m["siteUrl"] as? String)?.ifEmpty { null },
                        iconUrl = (m["iconUrl"] as? String)?.ifEmpty { null },
                    )
                }.distinctBy { it.url }
            val read =
                (root["read"] as? Map<*, *>).orEmpty().entries.mapNotNull { (k, v) ->
                    val key = k as? String ?: return@mapNotNull null
                    val time = (v as? Number)?.toLong() ?: return@mapNotNull null
                    key to time
                }.toMap()
            val settings =
                (root["settings"] as? Map<*, *>).orEmpty().entries.mapNotNull { (k, v) ->
                    val key = k as? String ?: return@mapNotNull null
                    val value = v as? String ?: return@mapNotNull null
                    key to value
                }.toMap()
            return SyncDocument(folders, feeds, read, settings)
        }
    }
}
