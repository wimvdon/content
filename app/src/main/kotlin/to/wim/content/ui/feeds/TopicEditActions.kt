package to.wim.content.ui.feeds

import to.wim.content.data.Folder

/** What the topic chips in the Add feed and Organise sheets (#307, #314) can do with the topics; each is stored at once. */
class TopicEditActions(
    val onNew: (title: String, icon: String) -> Unit,
    /** A new name and icon; the name of another topic joins the two. */
    val onEdit: (topic: Folder, title: String, icon: String) -> Unit,
    val onRemove: (Folder) -> Unit,
    val onReorder: (ids: List<String>) -> Unit,
)
