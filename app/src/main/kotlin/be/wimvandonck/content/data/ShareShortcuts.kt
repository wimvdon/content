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

/**
 * What a link shared from another app is for: Content shows separate targets in Android's share
 * menu (AndroidManifest.xml), each its own alias of the save screen. Unit tested.
 */
object ShareTargets {
    enum class Target {
        /** Save and share: the save sheet, which can also send it with Content. */
        Save,

        /** Send with Content only: nothing is saved. Also a person picked in the share menu (#235). */
        Send,
    }

    /** The alias other apps share links to for saving. */
    const val SAVE_ALIAS = "be.wimvandonck.content.ui.ShareActivity"

    /** The alias other apps share links to for sending with Content. */
    const val SEND_ALIAS = "be.wimvandonck.content.ui.SendActivity"

    /** The target that [component] (the alias the share menu opened) and [shortcutId] (a person picked there) ask for, or null for neither. */
    fun of(
        component: String?,
        shortcutId: String?,
    ): Target? =
        when {
            ShareShortcuts.emailOf(shortcutId) != null -> Target.Send
            component == SEND_ALIAS -> Target.Send
            component == SAVE_ALIAS -> Target.Save
            else -> null
        }
}
