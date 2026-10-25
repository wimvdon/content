package be.wimvandonck.content.ui

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import be.wimvandonck.content.R
import be.wimvandonck.content.data.ShareApps
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

    /**
     * Android's own share sheet with the article's link: More in Content's share sheet (#251). Without
     * Content's save and Send with Content aliases: Content's sheet has both already.
     */
    fun share(
        context: Context,
        title: String,
        url: String,
        chooserTitle: String,
    ): Boolean {
        val excluded = arrayOf(ComponentName(context, SHARE_ACTIVITY))
        val chooser =
            Intent
                .createChooser(shareIntent(title, url), chooserTitle)
                .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excluded)
        return start(context, chooser)
    }

    /** Selected text (the reader's selection menu) to Android's share sheet. */
    fun shareText(
        context: Context,
        text: String,
        chooserTitle: String,
    ): Boolean = start(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), chooserTitle))

    /** An app that acts on selected text (Translate, …), as Android's own selection menu offers it. */
    class TextApp(
        val component: ComponentName,
        val label: String,
    )

    /** The apps that act on selected text, in the order Android gives them. */
    fun processTextApps(context: Context): List<TextApp> {
        val pm = context.packageManager
        return pm
            .queryIntentActivities(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"), 0)
            .filter { it.activityInfo.exported && it.activityInfo.packageName != context.packageName }
            .map { TextApp(ComponentName(it.activityInfo.packageName, it.activityInfo.name), it.loadLabel(pm).toString()) }
    }

    /** Hands [text] to [app], read only, as Android's own selection menu does. */
    fun processText(
        context: Context,
        app: TextApp,
        text: String,
    ): Boolean =
        start(
            context,
            Intent(Intent.ACTION_PROCESS_TEXT)
                .setType("text/plain")
                .setComponent(app.component)
                .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true),
        )

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

    /** An app that accepts shared links, for the Save and Share button settings and Content's share sheet. */
    class ShareApp(
        val target: ShareTarget.App,
        val icon: Drawable,
    ) {
        /** What a pick in Content's share sheet is counted under (#251). */
        val id: String get() = ShareApps.idOf(target.packageName, target.activityName)
    }

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
