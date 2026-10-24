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

    /** Android shows at most five buttons of an app in its share sheet (#248): Send with Content, then people. */
    const val MAX_BUTTONS = 5

    /** The people with a button of their own in Content's share sheet (#248), after Send with Content. */
    fun <T> buttons(people: List<T>): List<T> = people.take(MAX_BUTTONS - 1)

    private const val PREFIX = "share:"

    fun idOf(email: String): String = PREFIX + email.trim().lowercase()

    /** The address a shortcut id stands for, or null when it isn't one of these. */
    fun emailOf(id: String?): String? = id?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.takeIf { it.contains('@') }

    /** The people to offer: the most recent recipients first, at most [MAX]. */
    fun pick(recipients: List<String>): List<String> = recipients.take(MAX)
}
