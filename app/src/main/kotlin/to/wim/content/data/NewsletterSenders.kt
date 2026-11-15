package to.wim.content.data

/**
 * Newsletter senders (2026-11-15, docs/newsletters.md): a tag's Newsletters feed mixes many
 * newsletters, so each sender is a source of its own inside it, with its own chip and name.
 *
 * - **Who is one sender** ([keyOf]): the sender's website (bozar.be), so newsletter@bozar.be and
 *   events@bozar.be are one. On a platform many newsletters send from ([SHARED_SITES]: substack.com,
 *   gmail.com, …) the website says nothing, so there it is the website and the sender's name. An
 *   email without a website (from before 2026-11-15) goes by the name alone.
 * - **The user's choice** ([SenderRule]): a name of its own, stored and synced as one text
 *   ([encode]). Combining, muting and moving senders to another tag were taken out on 2026-11-15:
 *   what an earlier version stored of them is ignored.
 * - **Facts** on a sender's page: when the newest issue came, how often they come ([cadence]) and
 *   how many are unread.
 *
 * Plain Kotlin, unit tested; the web app's `newsletterSenders.ts` is the same.
 */
data class SenderRule(
    /** The sender ([NewsletterSenders.keyOf]). */
    val key: String,
    /** Its own name; null: the sender's own. */
    val name: String? = null,
) {
    val isEmpty: Boolean get() = name == null
}

/** How often a sender's newsletters come ([NewsletterSenders.cadence]). */
enum class Cadence { Daily, Weekly, EveryTwoWeeks, Monthly, NowAndThen }

/** One sender as the apps show it. */
data class NewsletterSender(
    val feedUrl: String,
    /** Its key ([NewsletterSenders.keyOf]). */
    val key: String,
    val name: String,
    /** The sender itself, with the name it sends as (a list, as it was when senders could be combined). */
    val members: List<SenderMember>,
    val count: Int,
    val unread: Int,
    val newestAt: Long,
    /** When each issue came, newest first ([Article.sortTime]); what [NewsletterSenders.cadence] reads. */
    val times: List<Long> = emptyList(),
    /** The newest issue's [Article.key]; null when there is none. */
    val newestKey: String? = null,
) {
    val memberKeys: Set<String> get() = members.mapTo(LinkedHashSet()) { it.key }

    /** What stands in for it, in a chip and a list: its name, with its website's icon (none: [feed]'s, the envelope). */
    fun shownAs(feed: Feed?): Feed {
        val host = NewsletterSenders.siteOf(key) ?: return (feed ?: Feed(url = feedUrl, title = name)).copy(title = name)
        return Feed(url = "https://$host", title = name, iconUrl = "https://$host/favicon.ico", folderId = feed?.folderId)
    }
}

data class SenderMember(
    val key: String,
    val name: String,
)

class NewsletterSenders(
    val rules: List<SenderRule> = emptyList(),
) {
    private val byKey = rules.associateBy { it.key }

    fun rule(key: String): SenderRule? = byKey[key]

    /**
     * Who sent [article] of newsletter feed [feed], shown in its place: [NewsletterAddresses.sender],
     * with the name given to it.
     */
    fun shown(
        feed: Feed?,
        article: Article,
    ): Feed? {
        val sender = NewsletterAddresses.sender(feed, article) ?: return null
        val own = keyOf(article) ?: return sender
        return sender.copy(title = byKey[own]?.name ?: sender.title)
    }

    /**
     * The senders of newsletter feed [feedUrl] among [articles], by name (case ignored): each
     * sender with its own name (or the newest issue's sender name).
     */
    fun list(
        articles: List<Article>,
        feedUrl: String,
    ): List<NewsletterSender> {
        val groups = LinkedHashMap<String, MutableList<Pair<String, Article>>>()
        for (article in articles) {
            if (article.feedUrl != feedUrl) continue
            val own = keyOf(article) ?: continue
            groups.getOrPut(own) { mutableListOf() } += own to article
        }
        return groups
            .map { (group, entries) ->
                val newest = entries.sortedByDescending { it.second.sortTime }
                val members =
                    newest
                        .distinctBy { it.first }
                        .map { (key, a) -> SenderMember(key, a.author!!.trim()) }
                        .sortedBy { if (it.key == group) 0 else 1 }
                NewsletterSender(
                    feedUrl = feedUrl,
                    key = group,
                    name = byKey[group]?.name ?: members.first().name,
                    members = members,
                    count = entries.size,
                    unread = entries.count { !it.second.read },
                    newestAt = newest.first().second.sortTime,
                    times = newest.map { it.second.sortTime },
                    newestKey = newest.first().second.key,
                )
            }.sortedBy { it.name.lowercase() }
    }

    /**
     * The sender [article] belongs to, as [list] gives it; null
     * for an article that isn't a newsletter with a sender. What the list's long press, a sender
     * chip's long press and the reader's sender line open.
     */
    fun senderOf(
        articles: List<Article>,
        article: Article,
    ): NewsletterSender? {
        val own = keyOf(article) ?: return null
        return list(articles, article.feedUrl).firstOrNull { it.key == own }
    }

    /**
     * Feeds' sender chips: every newsletter feed's senders, with something in the tab shown
     * ([unreadOnly]: something unread); the one picked ([selection]) stays.
     */
    fun chips(
        library: Library,
        selection: FeedSelection,
        unreadOnly: Boolean,
    ): List<NewsletterSender> {
        val picked = selection as? FeedSelection.Sender
        return library.feeds
            .filter { NewsletterAddresses.isNewsletterFeed(it.url) }
            .flatMap { feed -> list(library.articles, feed.url) }
            .filter { !unreadOnly || it.unread > 0 || (it.feedUrl == picked?.feedUrl && it.key == picked.key) }
    }

    /** The sender in [senders] that [selection] shows, if it shows one. */
    fun chosen(
        senders: List<NewsletterSender>,
        selection: FeedSelection,
    ): NewsletterSender? {
        val sender = selection as? FeedSelection.Sender ?: return null
        return senders.firstOrNull { it.feedUrl == sender.feedUrl && it.key == sender.key }
    }

    /** Sender [key] with name [name] (blank: its own again). */
    fun renamed(
        key: String,
        name: String,
    ): NewsletterSenders = edit(key) { it.copy(name = name.clean().ifEmpty { null }) }

    private fun edit(
        key: String,
        change: (SenderRule) -> SenderRule,
    ): NewsletterSenders {
        // In its place, so the stored text changes only where it must.
        val changed = change(byKey[key] ?: SenderRule(key))
        val edited = if (key in byKey) rules.map { if (it.key == key) changed else it } else rules + changed
        return NewsletterSenders(edited.filterNot { it.isEmpty })
    }

    /**
     * How the names are stored and synced: one sender a line, key and name separated by tabs, then
     * an empty field and 0, where senders combined into another and muted ones were until 2026-11-15
     * (an app from before reads four fields). Those choices, and a fifth field (moved to another
     * tag), are ignored when read, so they no longer apply.
     */
    fun encode(): String = rules.filterNot { it.isEmpty }.joinToString("\n") { "${it.key}\t${it.name.orEmpty()}\t\t0" }

    override fun equals(other: Any?): Boolean = other is NewsletterSenders && other.rules == rules

    override fun hashCode(): Int = rules.hashCode()

    companion object {
        /**
         * Websites many unrelated newsletters send from, or that anyone mails from: there a sender is
         * the website and the name. The feed loader keeps only the domain without its mail
         * subdomain (mail.beehiiv.com becomes beehiiv.com).
         */
        val SHARED_SITES =
            setOf(
                "substack.com", "beehiiv.com", "buttondown.email", "buttondown.com", "ghost.io", "convertkit.com", "kit.com",
                "mailchimp.com", "mcsv.net", "list-manage.com", "mailerlite.com", "sendinblue.com", "brevo.com", "mailjet.com",
                "sendgrid.net", "medium.com", "patreon.com", "tinyletter.com", "revue.email", "getrevue.co", "linkedin.com",
                "gmail.com", "googlemail.com", "outlook.com", "hotmail.com", "live.com", "icloud.com", "me.com", "yahoo.com",
                "proton.me", "protonmail.com", "gmx.com", "gmx.net",
            )

        private val SITE = Regex("""^https://([a-z0-9-]+(\.[a-z0-9-]+)+)/?$""", RegexOption.IGNORE_CASE)
        private val SPACE = Regex("""\s+""")

        /** Which sender [article] of a newsletter feed is from (see the class); null when it names none. */
        fun keyOf(article: Article): String? {
            val name =
                article.author
                    ?.trim()
                    ?.replace(SPACE, " ")
                    ?.lowercase()
                    ?.takeIf { it.isNotEmpty() }
            val host =
                article.authorUrl
                    ?.trim()
                    ?.let { SITE.matchEntire(it) }
                    ?.groupValues
                    ?.get(1)
                    ?.lowercase()
            return when {
                host != null && host !in SHARED_SITES -> host
                name == null -> null
                host != null -> "$host/$name"
                else -> "name:$name"
            }
        }

        /** The website of sender [key], for its icon; null for one known by name alone. */
        fun siteOf(key: String): String? = if (key.startsWith("name:")) null else key.substringBefore('/')

        /** The names stored as [encode] has them; lines it can't read are left out. */
        fun decode(text: String?): NewsletterSenders =
            NewsletterSenders(
                text
                    .orEmpty()
                    .lines()
                    .mapNotNull { line ->
                        val parts = line.split('\t')
                        val key = parts[0].trim()
                        if (key.isEmpty() || parts.size < 4) return@mapNotNull null
                        SenderRule(key, parts[1].ifEmpty { null }).takeUnless { it.isEmpty }
                    }.distinctBy { it.key },
            )

        /**
         * How often a sender's newsletters come, from when its issues did ([times], any order): the
         * median gap between the newest 10. At most 2 days: daily; up to 10 days: weekly; up to 21:
         * every two weeks; up to 45: monthly; longer: now and then. Null with fewer than 3 issues.
         */
        fun cadence(times: List<Long>): Cadence? {
            if (times.size < 3) return null
            val newest = times.sortedDescending().take(10)
            val gaps = newest.zipWithNext { a, b -> a - b }.sorted()
            val middle = gaps.size / 2
            val median = if (gaps.size % 2 == 1) gaps[middle].toDouble() else (gaps[middle - 1] + gaps[middle]) / 2.0
            val days = median / DAY_MS
            return when {
                days <= 2 -> Cadence.Daily
                days <= 10 -> Cadence.Weekly
                days <= 21 -> Cadence.EveryTwoWeeks
                days <= 45 -> Cadence.Monthly
                else -> Cadence.NowAndThen
            }
        }

        private const val DAY_MS = 24 * 60 * 60 * 1000.0

        private fun String.clean(): String = replace(SPACE, " ").trim()
    }
}
