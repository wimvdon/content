package be.wimvandonck.reader.instapaper

/** A tab under Instapaper: its lists and folders, like the feeds' folders, and one tab for all tags. */
sealed interface InstapaperTab {
    data object Unread : InstapaperTab

    data object Liked : InstapaperTab

    data object Archive : InstapaperTab

    data class Folder(
        val folderId: Long,
    ) : InstapaperTab

    /** Every tag behind one tab, picked with a row of chips: tags come and go too often for a tab each. */
    data object Tags : InstapaperTab
}

object InstapaperTabs {
    /** Unread, Liked, Archive, each folder in Instapaper's order, then Tags when there are any. */
    fun of(
        folders: List<InstapaperFolder>,
        tags: List<InstapaperTag>,
    ): List<InstapaperTab> =
        buildList {
            add(InstapaperTab.Unread)
            add(InstapaperTab.Liked)
            add(InstapaperTab.Archive)
            folders.forEach { add(InstapaperTab.Folder(it.id)) }
            if (tags.isNotEmpty()) add(InstapaperTab.Tags)
        }

    /** The tab that shows [view]. */
    fun tabOf(view: InstapaperView): InstapaperTab =
        when (view) {
            InstapaperView.Home -> InstapaperTab.Unread
            InstapaperView.Liked -> InstapaperTab.Liked
            InstapaperView.Archive -> InstapaperTab.Archive
            is InstapaperView.InFolder -> InstapaperTab.Folder(view.folderId)
            is InstapaperView.WithTag -> InstapaperTab.Tags
        }

    /** The list a tap on [tab] shows. Tags keeps the tag already shown, else the first tag (Unread without tags). */
    fun viewOf(
        tab: InstapaperTab,
        tags: List<InstapaperTag>,
        current: InstapaperView?,
    ): InstapaperView =
        when (tab) {
            InstapaperTab.Unread -> InstapaperView.Home
            InstapaperTab.Liked -> InstapaperView.Liked
            InstapaperTab.Archive -> InstapaperView.Archive
            is InstapaperTab.Folder -> InstapaperView.InFolder(tab.folderId)
            InstapaperTab.Tags ->
                (current as? InstapaperView.WithTag)
                    ?: tags.firstOrNull()?.let { InstapaperView.WithTag(it.name) }
                    ?: InstapaperView.Home
        }
}
