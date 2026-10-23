package be.wimvandonck.content.sync

import java.security.MessageDigest

/** The account's picture (#166) and name (#234): Gravatar's for the email address, if it has them. Plain Kotlin, unit tested. */
object Gravatar {
    /** The picture's address: a SHA-256 of the trimmed, lower-case address; 404 when there is none. */
    fun url(
        email: String,
        size: Int = 96,
    ): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(email.trim().lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
        return "https://gravatar.com/avatar/$hash?s=$size&d=404"
    }

    /** The name in the feed loader's answer to /account/profile (#234): `{"name"}`, or "" when there is none. */
    fun nameFrom(answer: String): String = ((runCatching { Json.parse(answer) }.getOrNull() as? Map<*, *>)?.get("name") as? String)?.trim().orEmpty()
}
