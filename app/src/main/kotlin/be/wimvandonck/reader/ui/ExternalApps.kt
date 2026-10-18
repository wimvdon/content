package be.wimvandonck.reader.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.net.toUri
import be.wimvandonck.reader.data.ShareTarget

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

    /** The system share sheet with the article's link, without Reader itself (it takes shares, #97). */
    fun share(
        context: Context,
        title: String,
        url: String,
        chooserTitle: String,
    ): Boolean =
        start(
            context,
            Intent
                .createChooser(shareIntent(title, url), chooserTitle)
                .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java))),
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
