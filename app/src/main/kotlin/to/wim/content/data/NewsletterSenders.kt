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
 *   rule's [SenderRule.into]), or muted (its newsletters are left out of the lists, their counts
 *   and their chips, as hidden words do). They are stored and synced as one text ([encode]).
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
) {
    val isEmpty: Boolean get() = name == null && into == null && !muted
}

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
        return list(articles, article.feedUrl).firstOrNull { it.key == group }
    }

    /**
     * Feeds' sender chips: while a newsletter feed (or one of its senders) is picked, its senders
     * with something in the tab shown ([unreadOnly]: something unread); the one picked stays.
     */
    fun chips(
        library: Library,
        selection: FeedSelection,
        unreadOnly: Boolean,
    ): List<NewsletterSender> {
        val feedUrl =
            when (selection) {
                is FeedSelection.Single -> selection.feedUrl
                is FeedSelection.Sender -> selection.feedUrl
                else -> return emptyList()
            }
        if (!NewsletterAddresses.isNewsletterFeed(feedUrl)) return emptyList()
        val picked = (selection as? FeedSelection.Sender)?.key
        return list(library.articles, feedUrl).filter { !unreadOnly || it.unread > 0 || it.key == picked }
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
        val withSource = NewsletterSenders(moved).edit(source) { it.copy(into = target, muted = false, name = null) }
        return withSource.edit(target) { it.copy(name = it.name ?: intoName.clean().ifEmpty { null }) }
    }

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

    /** How the choices are stored and synced: one sender a line, key, name, into and muted (1 or 0) separated by tabs. */
    fun encode(): String = rules.filterNot { it.isEmpty }.joinToString("\n") { "${it.key}\t${it.name.orEmpty()}\t${it.into.orEmpty()}\t${if (it.muted) 1 else 0}" }

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
                        SenderRule(key, parts[1].ifEmpty { null }, parts[2].ifEmpty { null }, parts[3] == "1").takeUnless { it.isEmpty }
                    }.distinctBy { it.key },
            )

        private fun String.clean(): String = replace(SPACE, " ").trim()
    }
}

/** The library without the newsletters of muted senders: the lists, counts and chips. */
fun Library.withoutMuted(senders: NewsletterSenders): Library {
    if (senders.rules.none { it.muted }) return this
    return copy(articles = articles.filterNot { NewsletterAddresses.isNewsletterFeed(it.feedUrl) && senders.isMuted(it) })
}
