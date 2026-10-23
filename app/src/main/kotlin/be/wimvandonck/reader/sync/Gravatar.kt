package be.wimvandonck.reader.sync

import java.security.MessageDigest

/** The account's picture (#166): Gravatar's for the email address, if it has one. Plain Kotlin, unit tested. */
object Gravatar {
    /** The picture's address: a SHA-256 of the trimmed, lower-case address; 404 when there is none. */
    fun url(
        email: String,
        size: Int = 96,
    ): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(email.trim().lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
        return "https://gravatar.com/avatar/$hash?s=$size&d=404"
    }
}
