package be.wimvandonck.reader.data

import be.wimvandonck.reader.feed.Html
import java.net.URI
import kotlin.math.ceil

/**
 * Saved items shown like articles in lists and the reader: reading times, the feed article they
 * came from, their source. Plain Kotlin, unit tested.
 */
object ReadingLists {
    /** The feed key of items opened in the reader ("item <item key>"), so it never clashes with a feed's. */
    const val ITEM_FEED = "item"

    /** Where a text kept for an item came from: Reader's own extraction. */
    const val EXTRACTED = "extracted"

    fun readerKey(item: Item): String = Article.keyOf(ITEM_FEED, item.key)

    /** The item key in a reader key, or null when it is a feed article's. */
    fun itemKeyOf(readerKey: String): String? = readerKey.takeIf { it.startsWith("$ITEM_FEED ") }?.removePrefix("$ITEM_FEED ")

    /** About how many minutes reading [words] takes (230 words a minute), at least one. */
    fun minutes(words: Int): Int = if (words <= 0) 0 else ceil(words / WORDS_PER_MINUTE).toInt().coerceAtLeast(1)

    /** The minutes still to read at [progress] (0 to 1), at least one while not finished. */
    fun minutesLeft(
        minutes: Int,
        progress: Float,
    ): Int = if (minutes <= 0 || progress >= 0.99f) 0 else ceil(minutes * (1 - progress.coerceIn(0f, 1f))).toInt().coerceAtLeast(1)

    /** The number of words in some HTML. */
    fun words(html: String): Int = Html.toPlainText(html).split(' ').count { it.isNotBlank() }

    /**
     * The feed article an item came from, while the library has it: by its feed and id, else by its
     * link.
     */
    fun feedArticle(
        item: Item,
        library: Library,
    ): Article? {
        if (item.feedUrl != null && item.articleId != null) library.article(Article.keyOf(item.feedUrl, item.articleId))?.let { return it }
        return library.feedArticleFor(item.url)
    }

    /**
     * An item as the list and the reader show it, like a feed article: [text] when there is one (Reader's
     * extraction), else the feed article's text, else the description.
     */
    fun article(
        item: Item,
        library: Library?,
        text: String? = null,
    ): Article {
        val fromFeed = library?.let { feedArticle(item, it) }
        val time = item.readingList?.addedAt ?: item.bookmark?.addedAt ?: item.createdAt
        return Article(
            feedUrl = ITEM_FEED,
            id = item.key,
            title = item.title,
            link = item.url,
            author = fromFeed?.author,
            html = text ?: fromFeed?.html ?: if (item.description.isBlank()) "" else "<p>${Html.escape(item.description)}</p>",
            publishedAt = fromFeed?.publishedAt ?: time,
            fetchedAt = time,
            read = item.archived,
            imageUrl = item.imageUrl ?: fromFeed?.imageUrl,
        )
    }

    /**
     * Where an item comes from, shown above its title like a feed: the feed it was saved from, else a
     * feed of the same website, else the website itself with its icon (favicon).
     */
    fun source(
        item: Item,
        library: Library?,
    ): Feed? {
        val host = item.host.ifEmpty { return null }
        if (library != null) {
            feedArticle(item, library)?.let { article -> library.feed(article.feedUrl)?.let { return it } }
            library.feeds.firstOrNull { ItemKeys.hostOf(it.siteUrl) == host || ItemKeys.hostOf(it.url) == host }?.let { return it }
        }
        val withWww = runCatching { URI(item.url).host }.getOrNull()?.takeIf { it.isNotEmpty() } ?: host
        return Feed(url = host, title = host, iconUrl = "https://$withWww/favicon.ico")
    }

    private const val WORDS_PER_MINUTE = 230.0
}
