package to.wim.content.data

/**
 * The address Content gives an Instapaper article that has none of its own: a forwarded email, an
 * uploaded PDF (Instapaper's "private content", whose `url` is null). It is Instapaper's own page for
 * the article, so it opens there in the browser, and it is a web address like any other, so the
 * article syncs and is kept like any other. Its text doesn't come from that page (it needs the
 * Instapaper sign-in) but from Instapaper's parser, through the feed loader (`/instapaper/text`).
 * Plain Kotlin, unit tested.
 */
object InstapaperLinks {
    fun readLink(bookmarkId: Long): String = "https://www.instapaper.com/read/$bookmarkId"

    private val READ = Regex("""^https://(?:www\.)?instapaper\.com/read/(\d+)/?$""", RegexOption.IGNORE_CASE)

    /** The Instapaper article [url] stands for, when it is such an address; else null. */
    fun bookmarkId(url: String?): Long? = url?.trim()?.let { READ.matchEntire(it) }?.groupValues?.get(1)?.toLongOrNull()
}
