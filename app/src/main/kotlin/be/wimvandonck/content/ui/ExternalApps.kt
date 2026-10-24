package be.wimvandonck.content.ui

import android.app.PendingIntent
import android.service.chooser.ChooserAction
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import be.wimvandonck.content.R
import be.wimvandonck.content.data.ShareShortcuts
import be.wimvandonck.content.data.ShareTarget

/** Hand-offs to other apps: browser, share sheet and direct sharing to one app. */
object ExternalApps {
    /** Opens a web page. Only http(s) and mailto links, so a feed can't start other apps. */
    fun openLink(
        context: Context,
        url: String,
    ): Boolean {
        val scheme = url.substringBefore(':').lowercase()
        if (scheme !in setOf("http", "https", "mailto")) return false
        return start(context, Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    /** Action of the intent a custom tab's Save button sends to [SaveFromTabActivity] (#95). */
    const val ACTION_SAVE_FROM_TAB = "be.wimvandonck.content.action.SAVE_FROM_TAB"
    const val EXTRA_OPENED_URL = "be.wimvandonck.content.extra.OPENED_URL"
    const val EXTRA_OPENED_TITLE = "be.wimvandonck.content.extra.OPENED_TITLE"

    /**
     * The alias other apps share links to for saving (AndroidManifest.xml, #103); left out of
     * Content's own share menu, which has a Save button of its own. Send with Content stays (#246).
     */
    private const val SHARE_ACTIVITY = "be.wimvandonck.content.ui.ShareActivity"

    /**
     * Opens a web page in a custom tab (#95): the browser's page on top of Reader, with a Save
     * button that saves the page shown to Saved and opens the save sheet over the tab
     * ([SaveFromTabActivity]). [title] is the article's,
     * used when the page saved is the one opened. Other links (mailto) go to [openLink].
     */
    fun openInTab(
        context: Context,
        url: String,
        title: String?,
    ): Boolean {
        val scheme = url.substringBefore(':').lowercase()
        if (scheme != "http" && scheme != "https") return openLink(context, url)
        val save =
            Intent(context, SaveFromTabActivity::class.java)
                .setAction(ACTION_SAVE_FROM_TAB)
                .putExtra(EXTRA_OPENED_URL, url)
                .putExtra(EXTRA_OPENED_TITLE, title)
        // Mutable: the browser fills in the address of the page shown when Save is tapped.
        val pending = PendingIntent.getActivity(context, 0, save, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val icon = ContextCompat.getDrawable(context, R.drawable.ic_bookmark_add)?.toBitmap()
        val builder =
            CustomTabsIntent
                .Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
        if (icon != null) builder.setActionButton(icon, context.getString(R.string.action_save), pending, true)
        return try {
            builder.build().launchUrl(context, url.toUri())
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Action of the intent Send with Content's buttons in Content's own share menu send (#248). */
    const val ACTION_SEND_TO_USER = "be.wimvandonck.content.action.SEND_TO_USER"
    const val EXTRA_EMAIL = "be.wimvandonck.content.extra.EMAIL"

    /**
     * The system share sheet with the article's link, without Content's save alias (it has its own
     * Save button, #97). Signed in, on Android 14 and later, Send with Content and the people shared
     * with are buttons of their own in the sheet (#248), so the Send with Content alias and its people
     * at the top are left out; before Android 14 those stay (#246).
     */
    fun share(
        context: Context,
        title: String,
        url: String,
        chooserTitle: String,
    ): Boolean {
        val buttons = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && DirectShare.signedIn
        val excluded = listOfNotNull(ComponentName(context, SHARE_ACTIVITY), if (buttons) ComponentName(context, DirectShare.SEND_ACTIVITY) else null)
        val chooser =
            Intent
                .createChooser(shareIntent(title, url), chooserTitle)
                .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excluded.toTypedArray())
        if (buttons) chooser.putExtra(Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS, sendButtons(context, title, url))
        return start(context, chooser)
    }

    /** Send with Content, then the people shared with (at most five buttons in all, Android's limit). */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun sendButtons(
        context: Context,
        title: String,
        url: String,
    ): Array<ChooserAction> {
        fun open(
            request: Int,
            email: String?,
        ): PendingIntent {
            val intent =
                Intent(context, SaveFromTabActivity::class.java)
                    .setAction(ACTION_SEND_TO_USER)
                    .putExtra(EXTRA_OPENED_URL, url)
                    .putExtra(EXTRA_OPENED_TITLE, title)
                    .putExtra(EXTRA_EMAIL, email)
            return PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val send = ChooserAction.Builder(Icon.createWithResource(context, R.drawable.ic_group), context.getString(R.string.share_send_with_content), open(REQUEST_SEND, null)).build()
        val people =
            ShareShortcuts.buttons(DirectShare.people).mapIndexed { i, person ->
                ChooserAction.Builder(Icon.createWithAdaptiveBitmap(person.picture), person.label, open(REQUEST_SEND + 1 + i, person.email)).build()
            }
        return (listOf(send) + people).toTypedArray()
    }

    /** Request codes of the buttons' PendingIntents (one each, so their extras don't overwrite each other). */
    private const val REQUEST_SEND = 100

    /**
     * Sends the article where [target] says: the share sheet, or straight to one app's share
     * target, as if picked in the share sheet. False when that app is not installed (any more).
     */
    fun send(
        context: Context,
        target: ShareTarget,
        title: String,
        url: String,
        chooserTitle: String,
    ): Boolean =
        when (target) {
            // Saved is handled in the app; the share menu is the fallback should it get here.
            ShareTarget.Sheet, ShareTarget.Saved -> share(context, title, url, chooserTitle)
            is ShareTarget.App -> {
                val intent = shareIntent(title, url)
                if (target.activityName != null) {
                    intent.setClassName(target.packageName, target.activityName)
                } else {
                    intent.setPackage(target.packageName)
                }
                start(context, intent)
            }
        }

    /** An app that accepts shared links, for the Save and Share button settings. */
    class ShareApp(
        val target: ShareTarget.App,
        val icon: Drawable,
    )

    /** Every installed app that accepts shared text, by name. */
    fun shareApps(context: Context): List<ShareApp> {
        val pm = context.packageManager
        return pm
            .queryIntentActivities(shareIntent("", ""), 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { info ->
                ShareApp(
                    target = ShareTarget.App(info.activityInfo.packageName, info.activityInfo.name, info.loadLabel(pm).toString()),
                    icon = info.loadIcon(pm),
                )
            }.sortedBy { it.target.label.lowercase() }
    }

    fun isInstalled(
        context: Context,
        packageName: String,
    ): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun shareIntent(
        title: String,
        url: String,
    ): Intent =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, url)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TITLE, title)

    private fun start(
        context: Context,
        intent: Intent,
    ): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
}
