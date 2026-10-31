package to.wim.content.data

/**
 * Feeds' chip rows, now that there is no ☰ menu: every place in Feeds is one tap away from the list.
 *
 * - The first row: the state chip, the [topics] (those with something in the state shown), then the
 *   [loose] feeds (the ones without a topic), each a chip of its own.
 * - The second row, only while a topic is chosen (or one of its feeds): that topic's [topicFeeds].
 *   Tapping one shows that feed alone; tapping it again goes back to the whole topic.
 *
 * [chosenTopic] is the topic chip shown chosen: the topic picked, or the topic of the feed picked.
 * [chosenFeed] is the feed picked, if any. Plain Kotlin, unit tested.
 */
data class FeedChips(
    val topics: List<String>,
    val chosenTopic: String?,
    val topicFeeds: List<Feed>,
    val loose: List<Feed>,
    val chosenFeed: String?,
) {
    companion object {
        fun of(
            feeds: List<Feed>,
            folders: List<Folder>,
            topics: List<String>,
            selection: FeedSelection,
        ): FeedChips {
            val chosenFeed = (selection as? FeedSelection.Single)?.feedUrl
            val single = chosenFeed?.let { url -> feeds.firstOrNull { it.url == url } }
            val chosenTopic =
                selection.chipTag(folders)
                    ?: single?.let { feed -> topicOf(feed, folders, topics) }
            val topicFeeds = chosenTopic?.let { topic -> feeds.filter { feed -> feed.tags.any { it.equals(topic, ignoreCase = true) } } }.orEmpty()
            // A feed picked that is in no topic chip stays a loose chip, so it can be seen chosen.
            val loose = feeds.filter { feed -> isLoose(feed, folders) || (feed == single && chosenTopic == null) }
            // The chosen topic stays a chip even if it has nothing in the state shown.
            val shownTopics = if (chosenTopic != null && topics.none { it.equals(chosenTopic, ignoreCase = true) }) topics + chosenTopic else topics
            return FeedChips(shownTopics, chosenTopic, topicFeeds, loose, chosenFeed)
        }

        /** A feed without a topic: none set, or one that no longer exists. */
        fun isLoose(
            feed: Feed,
            folders: List<Folder>,
        ): Boolean = feed.tags.isEmpty() && (feed.folderId == null || folders.none { it.id == feed.folderId })

        /** The topic a feed is in, as a chip's name: its folder's, else its first tag that is a chip. */
        private fun topicOf(
            feed: Feed,
            folders: List<Folder>,
            topics: List<String>,
        ): String? =
            folders.firstOrNull { it.id == feed.folderId }?.title
                ?: feed.tags.firstOrNull { tag -> topics.any { it.equals(tag, ignoreCase = true) } }
                ?: feed.tags.firstOrNull()
    }
}
