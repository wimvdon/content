package to.wim.content.data

/**
 * Newsletter senders (2026-11-15, docs/newsletters.md): a tag's Newsletters feed mixes many
 * newsletters, so each sender is a source of its own inside it, with its own chip, name and mute.
 *
 * - **Who is one sender** ([keyOf]): the sender's website (bozar.be), so newsletter@bozar.be and
 *   events@bozar.be are one. On a platform many newsletters send from ([SHARED_SITES]: substack.com,
 *   gmail.com, …) the website says nothing, so there it is the website and the sender's name. An
 *   email without a website (from before 2026-11-15) goes by the name alone.
 * - **The user's choices** ([SenderRule]): a name of its own, merged into another sender (the
 *   rule's [SenderRule.into]), muted (its newsletters are left out of the lists, their counts
 *   and their chips, as hidden words do), or moved to another tag ([SenderRule.tag], see
 *   [withMovedSenders]). They are stored and synced as one text ([encode]).
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
    /** The sender it is merged into; null: none. */
    val into: String? = null,
    val muted: Boolean = false,
    /**
     * The tag its newsletters show under (the tag's id, or [NewsletterAddresses.NO_TAG] for No tag)
     * instead of the tag of the address they were sent to; null: where they were sent.
     */
    val tag: String? = null,
) {
    val isEmpty: Boolean get() = name == null && into == null && !muted && tag == null
}

/** How often a sender's newsletters come ([NewsletterSenders.cadence]). */
enum class Cadence { Daily, Weekly, EveryTwoWeeks, Monthly, NowAndThen }

/** One sender as the apps show it: a sender, with the ones merged into it. */
data class NewsletterSender(
    val feedUrl: String,
    /** Its key; the senders merged into it point here. */
    val key: String,
    val name: String,
    /** Every sender in it (its own first, then the merged ones), each with its own name. */
    val members: List<SenderMember>,
    val count: Int,
    val unread: Int,
    val muted: Boolean,
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

    /** The sender [key] ends up in: itself, or the one it is merged into (a loop stops where it started). */
    fun groupOf(key: String): String {
        var current = key
        val seen = HashSet<String>()
        while (seen.add(current)) {
            current = byKey[current]?.into ?: return current
        }
        return key
    }

    /** The sender [article] is shown as (merges followed); null when its sender is unknown. */
    fun groupOf(article: Article): String? = keyOf(article)?.let(::groupOf)

    fun rule(key: String): SenderRule? = byKey[key]

    fun isMuted(article: Article): Boolean = groupOf(article)?.let { byKey[it]?.muted } == true

    /**
     * Who sent [article] of newsletter feed [feed], shown in its place: [NewsletterAddresses.sender],
     * with the name given to it (or to the sender it is merged into) and, merged, that sender's icon.
     */
    fun shown(
        feed: Feed?,
        article: Article,
    ): Feed? {
        val sender = NewsletterAddresses.sender(feed, article) ?: return null
        val own = keyOf(article) ?: return sender
        val group = groupOf(own)
        val name = byKey[group]?.name ?: sender.title
        if (group == own) return sender.copy(title = name)
        val host = siteOf(group) ?: return feed!!.copy(title = name)
        return Feed(url = "https://$host", title = name, iconUrl = "https://$host/favicon.ico", folderId = feed?.folderId)
    }

    /**
     * The senders of newsletter feed [feedUrl] among [articles], by name (case ignored): each
     * sender with the ones merged into it, its own name (or the newest issue's sender name).
     */
    fun list(
        articles: List<Article>,
        feedUrl: String,
    ): List<NewsletterSender> {
        val groups = LinkedHashMap<String, MutableList<Pair<String, Article>>>()
        for (article in articles) {
            if (article.feedUrl != feedUrl) continue
            val own = keyOf(article) ?: continue
            groups.getOrPut(groupOf(own)) { mutableListOf() } += own to article
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
                    muted = byKey[group]?.muted == true,
                    newestAt = newest.first().second.sortTime,
                    times = newest.map { it.second.sortTime },
                    newestKey = newest.first().second.key,
                )
            }.sortedBy { it.name.lowercase() }
    }

    /**
     * The sender [article] belongs to, with the senders combined into it, as [list] gives it; null
     * for an article that isn't a newsletter with a sender. What the list's long press, a sender
     * chip's long press and the reader's sender line open.
     */
    fun senderOf(
        articles: List<Article>,
        article: Article,
    ): NewsletterSender? {
        val group = groupOf(article) ?: return null
        // Under the feed it is listed in, which is another one for a sender moved to another tag.
        val feedUrl = articles.firstOrNull { it.key == article.key }?.feedUrl ?: article.feedUrl
        return list(articles, feedUrl).firstOrNull { it.key == group }
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

    /** Sender [key] muted or not. */
    fun muted(
        key: String,
        muted: Boolean,
    ): NewsletterSenders = edit(key) { it.copy(muted = muted) }

    /**
     * Sender [from] (with everything merged into it) merged into [into], which keeps [intoName], the
     * name it shows, so it doesn't change when a newer issue of [from] comes in. Nothing when [into]
     * is in [from] already.
     */
    fun merged(
        from: String,
        into: String,
        intoName: String,
    ): NewsletterSenders {
        val target = groupOf(into)
        val source = groupOf(from)
        if (target == source) return this
        val moved = rules.map { if (it.into != null && groupOf(it.key) == source) it.copy(into = target) else it }
        val withSource = NewsletterSenders(moved).edit(source) { it.copy(into = target, muted = false, name = null, tag = null) }
        return withSource.edit(target) { it.copy(name = it.name ?: intoName.clean().ifEmpty { null }) }
    }

    /**
     * Sender [key] shown under tag [tag] (an id, or [NewsletterAddresses.NO_TAG]) instead of where
     * its newsletters are sent. [original], the tag of the address they are sent to, or [tag] null
     * clears the move.
     */
    fun movedTo(
        key: String,
        tag: String?,
        original: String?,
    ): NewsletterSenders = edit(key) { it.copy(tag = tag.takeIf { t -> t != original }) }

    /** Sender [key] out of the one it was merged into, a sender of its own again. */
    fun split(key: String): NewsletterSenders = edit(key) { it.copy(into = null) }

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
     * How the choices are stored and synced: one sender a line, key, name, into and muted (1 or 0)
     * separated by tabs, and, only for a sender moved to another tag, a fifth field: `-` for No tag,
     * else `t:` and the tag's id. An app from before it reads the first four and ignores the rest.
     */
    fun encode(): String =
        rules.filterNot { it.isEmpty }.joinToString("\n") {
            val base = "${it.key}\t${it.name.orEmpty()}\t${it.into.orEmpty()}\t${if (it.muted) 1 else 0}"
            it.tag?.let { tag -> "$base\t${encodeTag(tag)}" } ?: base
        }

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

        /** The choices stored as [encode] has them; lines it can't read are left out. */
        fun decode(text: String?): NewsletterSenders =
            NewsletterSenders(
                text
                    .orEmpty()
                    .lines()
                    .mapNotNull { line ->
                        val parts = line.split('\t')
                        val key = parts[0].trim()
                        if (key.isEmpty() || parts.size < 4) return@mapNotNull null
                        SenderRule(key, parts[1].ifEmpty { null }, parts[2].ifEmpty { null }, parts[3] == "1", parts.getOrNull(4)?.let(::decodeTag)).takeUnless { it.isEmpty }
                    }.distinctBy { it.key },
            )

        private fun encodeTag(tag: String): String = if (tag == NewsletterAddresses.NO_TAG) "-" else "t:$tag"

        /** The tag a stored fifth field names; null for an empty or unknown one. */
        private fun decodeTag(field: String): String? =
            when {
                field == "-" -> NewsletterAddresses.NO_TAG
                field.startsWith("t:") && field.length > 2 -> field.substring(2)
                else -> null
            }

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

        /**
         * The tag of the address sender [key] (its group) was sent to, in the stored [library]: an id,
         * [NewsletterAddresses.NO_TAG] for No tag, null when no newsletter of it is here. Where it is
         * shown under another tag, this is the tag it goes back to.
         */
        fun originalTag(
            library: Library,
            senders: NewsletterSenders,
            key: String,
        ): String? {
            val newest =
                library.articles
                    .filter { NewsletterAddresses.isNewsletterFeed(it.feedUrl) && senders.groupOf(it) == senders.groupOf(key) }
                    .maxByOrNull { it.sortTime } ?: return null
            return library.feed(newest.feedUrl)?.let { it.folderId ?: NewsletterAddresses.NO_TAG }
        }

        private fun String.clean(): String = replace(SPACE, " ").trim()
    }
}

/** The library without the newsletters of muted senders: the lists, counts and chips. */
fun Library.withoutMuted(senders: NewsletterSenders): Library {
    if (senders.rules.none { it.muted }) return this
    return copy(articles = articles.filterNot { NewsletterAddresses.isNewsletterFeed(it.feedUrl) && senders.isMuted(it) })
}

/**
 * The library with the newsletters of senders moved to another tag ([SenderRule.tag]) listed under
 * that tag's Newsletters feed: their [Article.feedUrl] is the target's, so the lists, chips and
 * counts of the tag show them, while [Article.storedFeedUrl] keeps the feed they are stored under,
 * so [Article.key] stays the same and read marks, opening and syncing keep working. A tag without a
 * Newsletters feed (or one that is gone) leaves them where they are. For lists only: never store it.
 */
fun Library.withMovedSenders(senders: NewsletterSenders): Library {
    if (senders.rules.none { it.tag != null }) return this
    val targets = feeds.filter { NewsletterAddresses.isNewsletterFeed(it.url) }.associate { (it.folderId ?: NewsletterAddresses.NO_TAG) to it.url }
    return copy(
        articles =
            articles.map { article ->
                if (!NewsletterAddresses.isNewsletterFeed(article.feedUrl)) return@map article
                val tag = senders.groupOf(article)?.let { senders.rule(it)?.tag } ?: return@map article
                val target = targets[tag] ?: return@map article
                if (target == article.feedUrl) article else article.copy(feedUrl = target, storedFeedUrl = article.storedFeedUrl ?: article.feedUrl)
            },
    )
}
