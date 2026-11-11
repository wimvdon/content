package to.wim.content.data

/**
 * + › Subscribe to newsletter: two ways to get newsletters into Content, each a page of steps.
 * As a feed, through Kill the Newsletter (an email address whose mail becomes an Atom feed, added in
 * Feeds like any feed); or straight into To read, through the user's own Save to Instapaper address
 * (the emails come into Saved through Instapaper, Newsletters). Instapaper's API doesn't give that
 * address, so the page links to Instapaper's Email page, which shows it once signed in. Content
 * itself never contacts either: the links open in the browser. Plain Kotlin, unit tested; the web
 * app's `newsletterSubscribe.ts` is the same.
 */
object NewsletterSubscribe {
    /** Where a Kill the Newsletter inbox is made. */
    const val KILL_THE_NEWSLETTER = "https://kill-the-newsletter.com/"

    /** Instapaper's Email page: signed in, it shows the Save to Instapaper address (and the folders' own). */
    const val INSTAPAPER_EMAIL = "https://www.instapaper.com/docs/save/email"

    enum class Way { Feed, Instapaper }

    /** What the Instapaper page offers: connect first, or find the address and choose the dot. */
    enum class InstapaperStep { Connect, FindAddress }

    fun instapaperStep(connected: Boolean): InstapaperStep = if (connected) InstapaperStep.FindAddress else InstapaperStep.Connect

    /** The page a way opens on: the site to visit. */
    fun site(way: Way): String =
        when (way) {
            Way.Feed -> KILL_THE_NEWSLETTER
            Way.Instapaper -> INSTAPAPER_EMAIL
        }
}
