package to.wim.content.data

/**
 * + › Subscribe to newsletter: two ways to get newsletters into Content, each a page. Your own
 * address: the account's feed loader makes an email address for a tag whose newsletters arrive as a
 * feed under that tag ([NewsletterAddresses], docs/newsletters.md); it needs a Content account. Or
 * straight into To read, through the user's own Save to Instapaper address (the emails come into
 * Saved through Instapaper, Newsletters). Instapaper's API doesn't give that address, so the page
 * links to Instapaper's Email page, which shows it once signed in. Plain Kotlin, unit tested; the
 * web app's `newsletterSubscribe.ts` is the same.
 */
object NewsletterSubscribe {
    /** Instapaper's Email page: signed in, it shows the Save to Instapaper address (and the folders' own). */
    const val INSTAPAPER_EMAIL = "https://www.instapaper.com/docs/save/email"

    enum class Way { Own, Instapaper }

    /** What the Instapaper page offers: connect first, or find the address and choose the setting. */
    enum class InstapaperStep { Connect, FindAddress }

    fun instapaperStep(connected: Boolean): InstapaperStep = if (connected) InstapaperStep.FindAddress else InstapaperStep.Connect

    /** What the own-address page offers: sign in to a Content account first, or make addresses. */
    enum class OwnStep { SignIn, Addresses }

    fun ownStep(signedIn: Boolean): OwnStep = if (signedIn) OwnStep.Addresses else OwnStep.SignIn

    /** Whether a tag can be given to a new address (the feed loader takes up to 100 characters). */
    fun canCreate(
        tag: String,
        busy: Boolean,
    ): Boolean = !busy && tag.trim().isNotEmpty() && tag.trim().length <= 100
}
