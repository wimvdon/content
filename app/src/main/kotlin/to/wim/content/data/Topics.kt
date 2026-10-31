package to.wim.content.data

/*
 * Topics: one way to group feeds and saved articles. A topic is a [Folder] (a name and an icon, in
 * the user's order): a feed is in one topic at most ([Feed.folderId]), a saved article has any
 * number of them, as its tags ([Item.tags], by name, case ignored). The sync file keeps folders and
 * tags as they were, so nothing changes in its format: these rules keep the two in step. Plain
 * Kotlin, unit tested, the same as the web app's topics.ts.
 */

/** The id of the topic made for a tag that has none yet: the same on every device, so two devices make one. */
fun topicIdFor(name: String): String = "topic:" + name.trim().lowercase()

/**
 * Every tag on a saved article has its topic, and no two topics share a name. A tag without one
 * (from Instapaper, an older app or another device) gets a topic of its own at the end, with the
 * default icon; topics with the same name (made on two devices at once) become the first of them,
 * with the feeds of all. The same library when nothing changes. Applied when the library is read
 * and after every change, like [Saved.foldersToTags].
 */
fun Library.topicsForTags(): Library {
    var topics = folders
    var feedsNow = feeds
    // Same name: the first one stays, the others' feeds move to it.
    val firstByName = LinkedHashMap<String, Folder>()
    val moved = HashMap<String, String>()
    for (topic in topics) {
        val key = topic.title.trim().lowercase()
        val first = firstByName[key]
        if (first == null) firstByName[key] = topic else moved[topic.id] = first.id
    }
    if (moved.isNotEmpty()) {
        topics = topics.filterNot { it.id in moved }
        feedsNow = feedsNow.map { f -> moved[f.folderId]?.let { f.copy(folderId = it) } ?: f }
    }
    val missing =
        items
            .flatMap { it.tags }
            .map(String::trim)
            .filter { it.isNotEmpty() && it.lowercase() !in firstByName }
            .distinctBy { it.lowercase() }
    if (missing.isEmpty() && moved.isEmpty()) return this
    val ids = topics.mapTo(HashSet()) { it.id }
    val added = missing.mapNotNull { name -> Folder(topicIdFor(name), name).takeIf { ids.add(it.id) } }
    return copy(folders = topics + added, feeds = feedsNow)
}

/** The topic with this name (case ignored), or null. */
fun Library.topicNamed(name: String): Folder? = folders.firstOrNull { it.title.trim().equals(name.trim(), ignoreCase = true) }

/**
 * A new topic at the end, or the library as it is when the name is blank or taken (case ignored):
 * a topic's name is what a saved article's tag refers to, so it is never there twice.
 */
fun Library.addTopic(
    id: String,
    title: String,
    icon: String = IconCatalog.DEFAULT,
): Library = if (title.isBlank() || topicNamed(title) != null) this else addFolder(Folder(id, title.trim(), icon))

/**
 * Renames a topic and sets its icon, on its feeds and on every saved article that has it. Renaming
 * it to the name of another topic joins the two: the other one stays (with its icon), and gets
 * this one's feeds and articles.
 */
fun Library.renameTopic(
    id: String,
    title: String,
    icon: String,
): Library {
    val topic = folder(id) ?: return this
    val clean = title.trim()
    if (clean.isEmpty()) return this
    val other = folders.firstOrNull { it.id != id && it.title.trim().equals(clean, ignoreCase = true) }
    if (other == null) return updateFolder(id, clean, icon).renameTag(topic.title, clean).feedFoldersToTags()
    return copy(
        folders = folders.filterNot { it.id == id },
        feeds = feeds.map { if (it.folderId == id) it.copy(folderId = other.id) else it },
    ).renameTag(topic.title, other.title).feedFoldersToTags()
}

/** Removes a topic: its feeds stay, without a topic, and its saved articles stay, without it. */
fun Library.removeTopic(id: String): Library {
    val topic = folder(id) ?: return this
    return removeFolder(id).removeTag(topic.title).feedFoldersToTags()
}

/** How many feeds and saved articles (To read and Archive) a topic has, and how many of those are still to read. */
data class TopicCount(
    val feeds: Int,
    val saved: Int,
    val toRead: Int = 0,
)

fun Library.topicCounts(): Map<String, TopicCount> =
    folders.associate { topic ->
        topic.id to
            TopicCount(
                feeds = feeds.count { it.folderId == topic.id },
                saved = items.count { item -> item.readingList != null && item.tags.any { it.trim().equals(topic.title.trim(), ignoreCase = true) } },
                toRead = items.count { item -> item.readingList?.state == ReadingState.ToRead && item.tags.any { it.trim().equals(topic.title.trim(), ignoreCase = true) } },
            )
    }

/**
 * [names] (tags) in the order of their topics, as Manage and ☰ list them; a name without a topic
 * comes after them, alphabetically. Case is ignored; each name keeps its own spelling.
 */
fun Library.inTopicOrder(names: List<String>): List<String> {
    val position = HashMap<String, Int>()
    folders.forEachIndexed { i, f -> position.putIfAbsent(f.title.trim().lowercase(), i) }
    return names.sortedWith(compareBy<String> { position[it.trim().lowercase()] ?: Int.MAX_VALUE }.thenBy { it.lowercase() })
}

/** The topic of each tag name, for its icon. */
fun Library.topicIcons(): Map<String, String> = folders.associate { it.title.trim().lowercase() to it.icon }

/** Every topic's name, in the user's order: what the save and Organise sheets offer. */
fun Library.topicNames(): List<String> = folders.map { it.title }

/** The topics that have feeds, in the user's order: Feeds' places in ☰ and its chips. */
fun feedTopics(
    feeds: List<Feed>,
    topics: List<Folder>,
): List<Folder> = topics.filter { t -> feeds.any { it.folderId == t.id } }

/**
 * Where Back goes from a selection in Feeds: from one feed to its topic (or All without one), from
 * a topic to All; null on All (Back leaves the app).
 */
fun FeedSelection.up(feeds: List<Feed>): FeedSelection? =
    when (this) {
        FeedSelection.All -> null
        is FeedSelection.Single -> feeds.firstOrNull { it.url == feedUrl }?.folderId?.let { FeedSelection.InFolder(it) } ?: FeedSelection.All
        else -> FeedSelection.All
    }

/** The topics in a new order, by id (Manage topics' drag); topics not named keep their place at the end. */
fun Library.reorderTopics(ids: List<String>): Library {
    val byId = folders.associateBy { it.id }
    val ordered = ids.mapNotNull(byId::get).distinctBy { it.id }
    val rest = folders.filter { t -> ordered.none { it.id == t.id } }
    val result = ordered + rest
    return if (result == folders) this else copy(folders = result)
}

/**
 * The feeds of one topic (null: those without one) in a new order, by address (a topic page's
 * drag). The other feeds keep their places: this topic's feeds take the same slots in the list.
 */
fun Library.reorderFeedsIn(
    topicId: String?,
    urls: List<String>,
): Library {
    val slots = feeds.indices.filter { feeds[it].folderId == topicId }
    val inTopic = slots.map { feeds[it] }
    val ordered = (urls.mapNotNull { u -> inTopic.firstOrNull { it.url == u } } + inTopic).distinctBy { it.url }
    if (ordered == inTopic) return this
    val result = feeds.toMutableList()
    slots.forEachIndexed { i, slot -> result[slot] = ordered[i] }
    return copy(feeds = result)
}

/**
 * Merges topic [fromId] into [intoId]: the other one stays, with its name and icon, and gets this
 * one's feeds and saved articles (a topic page's Merge with…).
 */
fun Library.mergeTopic(
    fromId: String,
    intoId: String,
): Library {
    val into = folder(intoId) ?: return this
    if (fromId == intoId || folder(fromId) == null) return this
    return renameTopic(fromId, into.title, into.icon)
}


/**
 * Feeds' topic chips: the topics with articles in the state shown (unread ones with Unread), in the
 * user's order; a topic with nothing to show is left out, except the one chosen ([chosen]), so a
 * chip never disappears under your finger.
 */
fun Library.feedChips(
    unreadOnly: Boolean,
    chosen: String?,
): List<String> =
    feedTags().filter { tag ->
        tag.equals(chosen, ignoreCase = true) ||
            articles.any { a -> (!unreadOnly || !a.read) && feed(a.feedUrl)?.tags.orEmpty().any { it.equals(tag, ignoreCase = true) } }
    }
