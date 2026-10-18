package be.wimvandonck.reader.instapaper

/** A link shared to Reader from another app (#97), to be saved like an article. */
data class SharedLink(
    val url: String,
    /** The page's title when the other app sent one, else null (Instapaper reads it from the page). */
    val title: String?,
) {
    companion object {
        /** A title longer than this is not a title but text around the link. */
        const val MAX_TITLE = 200

        private val LINK = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

        /**
         * Reads a share (Android's send action with text): the first web link in [text], else in
         * [subject]. Apps send "Title" as the subject and the link as the text, or "Title link" in
         * the text; the title is the subject, else the text without the link when that is one
         * short line. Null when there is no web link.
         */
        fun of(
            subject: String?,
            text: String?,
        ): SharedLink? {
            val body = text.orEmpty().trim()
            val heading = subject.orEmpty().trim()
            val match = LINK.find(body) ?: LINK.find(heading) ?: return null
            val url = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', '\'', '"')
            val inBody = LINK.find(body) != null
            val rest = if (inBody) body.removeRange(match.range).trim() else body
            val title =
                listOf(if (inBody) heading else "", rest)
                    .map { it.trim() }
                    .firstOrNull { it.any(Char::isLetterOrDigit) && '\n' !in it && it.length <= MAX_TITLE && LINK.find(it) == null }
            return SharedLink(url, title)
        }
    }
}
