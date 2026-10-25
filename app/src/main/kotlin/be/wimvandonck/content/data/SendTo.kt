package be.wimvandonck.content.data

import be.wimvandonck.content.sync.ShareResult

/**
 * Sending an article with Content from the Save and share sheet (#256), besides saving it or
 * instead: the people it offers, picking them, what its button does, and how the sending went, in
 * one message. Plain Kotlin, unit tested.
 */
object SendTo {
    private val EMAIL = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

    /** Looks like an email address (the account says whether it has a Content account). */
    fun isEmail(text: String): Boolean = EMAIL.matches(text.trim())

    /** The people offered: those shared with before, then the ones picked here (an address added in the sheet), each once. */
    fun people(
        recent: List<String>,
        picked: List<String>,
    ): List<String> = (recent + picked).distinctBy { it.trim().lowercase() }

    fun has(
        picked: List<String>,
        email: String,
    ): Boolean = picked.any { it.equals(email.trim(), ignoreCase = true) }

    /** Picks someone, or unpicks them. */
    fun toggle(
        picked: List<String>,
        email: String,
    ): List<String> = if (has(picked, email)) picked.filterNot { it.equals(email.trim(), ignoreCase = true) } else picked + email.trim()

    /** What the sheet's button does, and says. */
    enum class Action { Save, SaveAndSend, Send }

    fun action(choice: SaveChoice): Action =
        when {
            !choice.keep -> Action.Send
            choice.sendTo.isEmpty() -> Action.Save
            else -> Action.SaveAndSend
        }

    /** The button can be tapped: a new save needs its link, and Don't save needs someone to send it to. */
    fun canConfirm(
        choice: SaveChoice,
        hasLink: Boolean,
    ): Boolean = hasLink && (choice.keep || choice.sendTo.isNotEmpty())

    /** How sending went: to everyone, or the first one it didn't reach and why. */
    sealed interface Outcome {
        data class Sent(
            val to: List<String>,
        ) : Outcome

        data class Failed(
            val email: String,
            val result: ShareResult,
        ) : Outcome
    }

    /** One outcome for the message after sending; null when nothing was sent. */
    fun outcome(results: List<Pair<String, ShareResult>>): Outcome? {
        if (results.isEmpty()) return null
        val failed = results.firstOrNull { it.second != ShareResult.Sent }
        return if (failed == null) Outcome.Sent(results.map { it.first }) else Outcome.Failed(failed.first, failed.second)
    }
}
