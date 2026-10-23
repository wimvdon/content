package be.wimvandonck.content.data

import be.wimvandonck.content.sync.Json

/**
 * The people you shared with (#235), most recent first, offered as one-tap choices when sending to a
 * Content user. Addresses in lower case, at most [MAX]. Synced with the account as the setting
 * "sharing.recipients" (a JSON list), so every device offers the same people. Plain Kotlin, unit tested.
 */
object Recipients {
    const val MAX = 12

    /** [list] with [email] first: it was just shared with. */
    fun remember(
        list: List<String>,
        email: String,
    ): List<String> {
        val clean = email.trim().lowercase()
        if (clean.isEmpty()) return list
        return (listOf(clean) + list.filter { it != clean }).take(MAX)
    }

    fun encode(list: List<String>): String = Json.write(list)

    /** The list in [text], or null when it isn't one. */
    fun decode(text: String?): List<String>? {
        val parsed = text?.let { runCatching { Json.parse(it) }.getOrNull() } as? List<*> ?: return null
        return parsed.filterIsInstance<String>().map { it.trim().lowercase() }.filter { it.contains('@') }.distinct().take(MAX)
    }
}
