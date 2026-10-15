package io.github.impca201.rssreader.data

/**
 * Turns a [Library] into text for storage and back. One record per line, fields separated by
 * tabs; backslashes, tabs and line breaks inside fields are escaped. Unknown or broken lines are
 * skipped, so a damaged file loses only those records.
 */
object LibraryCodec {
    private const val HEADER = "rss-reader-library 1"
    private const val FEED = "F"
    private const val ARTICLE = "A"
    private const val FOLDER = "D"

    fun encode(library: Library): String =
        buildString {
            append(HEADER).append('\n')
            for (folder in library.folders) {
                appendRecord(FOLDER, folder.id, folder.title, folder.icon)
            }
            for (feed in library.feeds) {
                appendRecord(FEED, feed.url, feed.title, feed.siteUrl.orEmpty(), feed.folderId.orEmpty(), feed.iconUrl.orEmpty(), if (feed.renamed) "1" else "")
            }
            for (a in library.articles) {
                appendRecord(
                    ARTICLE,
                    a.feedUrl,
                    a.id,
                    a.title,
                    a.link.orEmpty(),
                    a.author.orEmpty(),
                    a.publishedAt?.toString().orEmpty(),
                    a.fetchedAt.toString(),
                    if (a.read) "1" else "0",
                    a.html,
                    a.imageUrl.orEmpty(),
                )
            }
        }

    fun decode(text: String): Library {
        val lines = text.split('\n')
        if (lines.firstOrNull() != HEADER) return Library()
        val feeds = mutableListOf<Feed>()
        val articles = mutableListOf<Article>()
        val folders = mutableListOf<Folder>()
        for (line in lines.drop(1)) {
            val fields = line.split('\t').map(::unescape)
            when (fields.firstOrNull()) {
                FEED -> decodeFeed(fields)?.let(feeds::add)
                ARTICLE -> decodeArticle(fields)?.let(articles::add)
                FOLDER -> decodeFolder(fields)?.let(folders::add)
            }
        }
        val feedUrls = feeds.map { it.url }.toSet()
        val uniqueFolders = folders.distinctBy { it.id }
        val folderIds = uniqueFolders.map { it.id }.toSet()
        return Library(
            feeds = feeds.distinctBy { it.url }.map { if (it.folderId != null && it.folderId !in folderIds) it.copy(folderId = null) else it },
            articles = articles.filter { it.feedUrl in feedUrls }.distinctBy { it.key },
            folders = uniqueFolders,
        )
    }

    private fun decodeFeed(f: List<String>): Feed? {
        if (f.size < 4 || f[1].isEmpty()) return null
        // The folder, icon and renamed fields were added after the first release.
        return Feed(
            url = f[1],
            title = f[2],
            siteUrl = f[3].ifEmpty { null },
            folderId = f.getOrNull(4)?.ifEmpty { null },
            iconUrl = f.getOrNull(5)?.ifEmpty { null },
            renamed = f.getOrNull(6) == "1",
        )
    }

    private fun decodeFolder(f: List<String>): Folder? {
        if (f.size < 4 || f[1].isEmpty() || f[2].isBlank()) return null
        return Folder(id = f[1], title = f[2], icon = IconCatalog.fromStored(f[3]))
    }

    private fun decodeArticle(f: List<String>): Article? {
        if (f.size < 10 || f[1].isEmpty() || f[2].isEmpty()) return null
        return Article(
            feedUrl = f[1],
            id = f[2],
            title = f[3],
            link = f[4].ifEmpty { null },
            author = f[5].ifEmpty { null },
            publishedAt = f[6].toLongOrNull(),
            fetchedAt = f[7].toLongOrNull() ?: return null,
            read = f[8] == "1",
            html = f[9],
            // Added after the first release; older files have no picture field.
            imageUrl = f.getOrNull(10)?.ifEmpty { null },
        )
    }

    private fun StringBuilder.appendRecord(vararg fields: String) {
        fields.forEachIndexed { index, field ->
            if (index > 0) append('\t')
            escapeTo(field, this)
        }
        append('\n')
    }

    private fun escapeTo(
        value: String,
        out: StringBuilder,
    ) {
        for (c in value) {
            when (c) {
                '\\' -> out.append("\\\\")
                '\t' -> out.append("\\t")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                else -> out.append(c)
            }
        }
    }

    private fun unescape(value: String): String {
        if ('\\' !in value) return value
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    't' -> out.append('\t')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    else -> out.append(value[i + 1])
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
