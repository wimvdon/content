package to.wim.content.sync

import to.wim.content.instapaper.InstapaperSession

/**
 * The Instapaper sign-in, kept in the Content account's synced settings ([SyncSettings.INSTAPAPER_TOKEN])
 * so Instapaper is connected once for every device of the account, the web app's included
 * (docs/instapaper.md). The value is a JSON object with the token, the Instapaper user's id and
 * name; "" means it was disconnected (on any device, or Instapaper refused the token), which
 * disconnects every device.
 */
object SharedInstapaper {
    /** What happens on this device after a sync. */
    sealed interface Action {
        data object None : Action

        /** Disconnected on another device. */
        data object SignOut : Action

        /** Connected (or with a new token) on another device. */
        data class SignIn(
            val session: InstapaperSession,
        ) : Action
    }

    fun encode(session: InstapaperSession): String =
        Json.write(
            buildMap {
                put("token", session.token)
                put("username", session.username)
                if (session.userId != null) put("userId", session.userId)
            },
        )

    /** The session in [text], or null when it is empty (signed out) or can't be read. */
    fun decode(text: String?): InstapaperSession? {
        if (text.isNullOrEmpty()) return null
        val map = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: return null
        val token = (map["token"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
        val userId = (map["userId"] as? Number)?.toLong()
        return InstapaperSession(token, userId, map["username"] as? String ?: "")
    }

    /**
     * What this device puts in the file: its sign-in; "" when it was signed in at the last sync and
     * isn't now (it disconnected); null (the file's stays) when it has never had one, so a device
     * that simply isn't connected yet doesn't disconnect the others.
     */
    fun local(
        session: InstapaperSession?,
        base: String?,
    ): String? =
        when {
            session != null -> encode(session)
            !base.isNullOrEmpty() -> ""
            else -> base
        }

    /** Whether [a] and [b] are the same Instapaper user (known ids that match), so a new token keeps what was synced. */
    fun sameUser(
        a: InstapaperSession?,
        b: InstapaperSession?,
    ): Boolean = a?.userId != null && a.userId == b?.userId

    /** What to do with the [merged] value, given this device's [session]. */
    fun action(
        session: InstapaperSession?,
        merged: String?,
    ): Action {
        if (merged == null) return Action.None
        val shared = decode(merged)
        return when {
            shared == null -> if (merged.isEmpty() && session != null) Action.SignOut else Action.None
            session?.token == shared.token -> Action.None
            else -> Action.SignIn(shared)
        }
    }
}
