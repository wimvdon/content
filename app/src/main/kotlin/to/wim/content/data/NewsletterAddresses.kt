package to.wim.content.data

import java.security.MessageDigest

/**
 * Newsletter addresses (docs/newsletters.md): with a Content account, every tag has an email
 * address, and so does No tag (2026-11-12). The account's feed loader makes them when the apps ask
 * ([missing]); the newsletters sent to one arrive in its feed, called Newsletters, under that tag.
 * Nobody makes, renames, moves or removes one by hand: [reconcile] keeps the library's newsletter
 * feeds the same as the account's addresses, and removing a tag removes its address. The feed
 * loader knows a tag only by [tagHash], never by its name. Plain Kotlin, unit tested; the web app's
 * `newsletterAddresses.ts` is the same.
 */
data class NewsletterAddress(
    val code: String,
    /** [NewsletterAddresses.tagHash] of the tag it belongs to; null: the one without a tag. */
    val tagHash: String?,
    /** The email address to subscribe with, e.g. content+k7q2m9abcdef@in.wim.to. */
    val address: String,
    val feedUrl: String,
    /** When the feed loader made it (milliseconds); 0 when unknown. */
    val createdAt: Long = 0,
    /** After Get a new address (2026-11-15): until when the address it had still takes mail (milliseconds); null when none does. */
    val oldUntil: Long? = null,
)

object NewsletterAddresses {
    private val FEED = Regex("""^https://[^/]+/newsletters/([a-z0-9]{12})/[0-9a-f]{32}/?$""")

    /** How long an address whose tag isn't here is kept: that tag may have been made on another device and not have reached this one. */
    const val ORPHAN_GRACE_MS = 24 * 60 * 60 * 1000L

    /** The code of a newsletter address's feed, or null for any other feed. */
    fun codeOfFeed(url: String?): String? = url?.trim()?.let { FEED.matchEntire(it) }?.groupValues?.get(1)

    fun isNewsletterFeed(url: String?): Boolean = codeOfFeed(url) != null

    private val EMAIL = Regex("""^https://[^/]+/newsletters/[a-z0-9]{12}/(e/[0-9a-f]{16}/[0-9a-f]{32}|[0-9a-f]{32}/[0-9a-f]{16})/?$""")

    /**
     * Whether [url] is one newsletter email's page (its own link, or the feed's link with its id
     * from before 2026-11-15): such a link is private, so Report a problem never sends it.
     */
    fun isNewsletterEmail(url: String?): Boolean = url?.trim()?.let { EMAIL.matches(it) } == true

    private val SITE = Regex("""^https://([a-z0-9-]+(\.[a-z0-9-]+)+)/?$""", RegexOption.IGNORE_CASE)

    /**
     * Who sent [article] of newsletter feed [feed], shown in its place (in the list and the reader):
     * the sender's name, and the icon (`/favicon.ico`) of the sender's website, which the feed loader
     * takes from their address (worker `senderSite`). Without a website (emails from before
     * 2026-11-15) it keeps the envelope. Null for any other feed, or when the sender is unknown.
     */
    /**
     * Whether [shown], what stands in for [feed], gets a small envelope on its icon: a newsletter
     * shown with its sender's website icon, so it still reads as a newsletter. Not when the icon is
     * the envelope already, and never for RSS feeds, which are most of the list.
     */
    fun hasEnvelopeBadge(
        feed: Feed?,
        shown: Feed?,
    ): Boolean = isNewsletterFeed(feed?.url) && shown != null && !isNewsletterFeed(shown.url)

    fun sender(
        feed: Feed?,
        article: Article,
    ): Feed? {
        if (feed == null || !isNewsletterFeed(feed.url)) return null
        val name = article.author?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val host = article.authorUrl?.trim()?.let { SITE.matchEntire(it) }?.groupValues?.get(1)?.lowercase()
        return if (host == null) {
            feed.copy(title = name)
        } else {
            Feed(url = "https://$host", title = name, iconUrl = "https://$host/favicon.ico", folderId = feed.folderId)
        }
    }

    /** How the feed loader knows a tag: the SHA-256 of its id, in hex (a tag's id can hold its name). Null: No tag. */
    fun tagHash(tagId: String?): String? =
        tagId?.let { id -> MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) } }

    /** One address from the feed loader's answer, or null when it isn't one. */
    fun of(value: Any?): NewsletterAddress? {
        val map = value as? Map<*, *> ?: return null
        val code = map["code"] as? String ?: return null
        val address = map["address"] as? String ?: return null
        val feedUrl = (map["feedUrl"] as? String)?.takeIf { codeOfFeed(it) == code } ?: return null
        return NewsletterAddress(
            code,
            map["tagHash"] as? String,
            address,
            feedUrl,
            (map["createdAt"] as? Number)?.toLong() ?: 0,
            (map["oldUntil"] as? Number)?.toLong(),
        )
    }

    /**
     * Until when [address]'s old address still takes mail, shown under it ("Your old address works
     * until …"); null when it never had a new one or the old one has stopped. As the web's oldAddressUntil.
     */
    fun oldAddressUntil(
        address: NewsletterAddress,
        now: Long,
    ): Long? = address.oldUntil?.takeIf { it > now }

    /** The list with [renewed] (Get a new address's answer) in place of the address it replaces: same code, same feed. */
    fun withRenewed(
        addresses: List<NewsletterAddress>,
        renewed: NewsletterAddress,
    ): List<NewsletterAddress> = addresses.map { if (it.code == renewed.code) renewed else it }

    /** The list `GET /account/newsletters` answers. */
    fun listOf(value: Any?): List<NewsletterAddress> = ((value as? Map<*, *>)?.get("addresses") as? List<*>).orEmpty().mapNotNull(::of)

    /** The address of tag [tagId] (null: No tag), if it has one yet. */
    fun forTag(
        addresses: List<NewsletterAddress>,
        tagId: String?,
    ): NewsletterAddress? = tagHash(tagId).let { hash -> addresses.firstOrNull { it.tagHash == hash } }

    /** The tags (by id, in their order, then null for No tag) that have no address yet: the apps ask the feed loader for these. */
    fun missing(
        folders: List<Folder>,
        addresses: List<NewsletterAddress>,
    ): List<String?> = (folders.map { it.id } + kotlin.collections.listOf<String?>(null)).filter { forTag(addresses, it) == null }

    /**
     * The addresses whose tag is gone (merged into another, or removed while offline): removed from
     * the account once older than [ORPHAN_GRACE_MS].
     */
    fun orphans(
        folders: List<Folder>,
        addresses: List<NewsletterAddress>,
        now: Long,
    ): List<NewsletterAddress> {
        val hashes = folders.mapTo(HashSet()) { tagHash(it.id) }
        return addresses.filter { it.tagHash != null && it.tagHash !in hashes && now - it.createdAt > ORPHAN_GRACE_MS }
    }

    /**
     * The library with one newsletter feed per address whose tag is here, under that tag and called
     * [title] (Newsletters, in the app's language, when it is added); the newsletter feeds of any
     * other address are removed, and one that ended up under another tag goes back. [addresses]
     * must be the account's whole list, just read.
     */
    fun reconcile(
        library: Library,
        addresses: List<NewsletterAddress>,
        title: String,
    ): Library {
        val tagOf = LinkedHashMap<String, String?>()
        for (tagId in library.folders.map { it.id } + kotlin.collections.listOf<String?>(null)) forTag(addresses, tagId)?.let { tagOf[it.feedUrl] = tagId }
        var result = library
        for (feed in library.feeds) {
            if (!isNewsletterFeed(feed.url)) continue
            if (feed.url !in tagOf) {
                result = result.removeFeed(feed.url)
            } else if (feed.folderId != tagOf[feed.url]) {
                result = result.moveFeed(feed.url, tagOf[feed.url])
            }
        }
        for ((url, tagId) in tagOf) {
            if (result.feed(url) == null) result = result.addFeed(Feed(url = url, title = title, folderId = tagId, renamed = true))
        }
        return if (result == library) library else result.feedFoldersToTags()
    }

    /** How the page stores a pick of No tag (a tag's id is never empty). */
    const val NO_TAG = ""

    /**
     * The tag the Newsletter feed page shows (null: No tag): the one [picked] (an id, or [NO_TAG]),
     * while it is still a tag; nothing picked yet, or a tag that is gone: the first tag, else No tag.
     */
    fun chosenTag(
        folders: List<Folder>,
        picked: String?,
    ): String? =
        when {
            picked == NO_TAG -> null
            picked != null && folders.any { it.id == picked } -> picked
            else -> folders.firstOrNull()?.id
        }

    /** The pick that opens the Newsletters page on feed [feedUrl]'s tag: its tag's id, or [NO_TAG]. */
    fun pickFor(
        feeds: List<Feed>,
        feedUrl: String,
    ): String = feeds.firstOrNull { it.url == feedUrl }?.folderId ?: NO_TAG

    /** What a newsletter feed has received: how many newsletters are in the library, and when the newest came (null: none yet). */
    fun activity(
        articles: List<Article>,
        feedUrl: String,
    ): NewsletterActivity {
        val received = articles.filter { it.feedUrl == feedUrl }
        return NewsletterActivity(received.size, received.maxOfOrNull { it.sortTime })
    }

    /** Newsletter feeds in Settings and in +: every tag in its order, then No tag, each with its address (null: not made yet, or offline). */
    fun rows(
        folders: List<Folder>,
        addresses: List<NewsletterAddress>,
    ): List<NewsletterRow> = (folders + kotlin.collections.listOf<Folder?>(null)).map { NewsletterRow(it, forTag(addresses, it?.id)) }
}

/** One tag (null: No tag) and its address. */
data class NewsletterRow(
    val topic: Folder?,
    val address: NewsletterAddress?,
)

/** What a tag's Newsletters feed has received so far ([NewsletterAddresses.activity]). */
data class NewsletterActivity(
    val count: Int,
    /** When the newest one came (milliseconds); null when none has yet. */
    val newestAt: Long?,
)
