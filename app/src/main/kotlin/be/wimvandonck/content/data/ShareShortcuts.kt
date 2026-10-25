package be.wimvandonck.content.data

/**
 * Direct share (#235): the people shared with appear in Android's share menu, as sharing shortcuts.
 * This is the plain part: which people, and the shortcut id that says who was picked. Unit tested.
 */
object ShareShortcuts {
    /** The share target's category (res/xml/shortcuts.xml). */
    const val CATEGORY = "be.wimvandonck.content.category.SHARE_TO_USER"

    /** How many people the share menu gets; it shows only the first few of an app anyway. */
    const val MAX = 4

    private const val PREFIX = "share:"

    fun idOf(email: String): String = PREFIX + email.trim().lowercase()

    /** The address a shortcut id stands for, or null when it isn't one of these. */
    fun emailOf(id: String?): String? = id?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.takeIf { it.contains('@') }

    /** The people to offer: the most recent recipients first, at most [MAX]. */
    fun pick(recipients: List<String>): List<String> = recipients.take(MAX)
}

/**
 * The launcher's shortcuts (#251): long-pressing Content's icon offers the first feed folders, which
 * open the app on that folder. Unit tested.
 */
object LauncherFolders {
    /** Launchers show four or five; the people share the app's shortcuts too. */
    const val MAX = 4

    private const val PREFIX = "folder:"

    /** The folders offered: the first ones, in the order of the feed list. */
    fun pick(folders: List<Folder>): List<Folder> = folders.take(MAX)

    fun idOf(folderId: String): String = PREFIX + folderId

    /** What the shortcut asks the app to open (MainActivity.EXTRA_OPEN_ARTICLE). */
    fun requestOf(folderId: String): String = PREFIX + folderId

    /** The folder an open request stands for, or null when it isn't one of these. */
    fun folderOf(request: String?): String? = request?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.takeIf { it.isNotEmpty() }
}
