package io.github.impca201.rssreader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri

/** Hand-offs to other apps: browser, share sheet, Instapaper and the Play Store. */
object ExternalApps {
    const val INSTAPAPER_PACKAGE = "com.instapaper.android"

    /** Opens a web page. Only http(s) and mailto links, so a feed can't start other apps. */
    fun openLink(
        context: Context,
        url: String,
    ): Boolean {
        val scheme = url.substringBefore(':').lowercase()
        if (scheme !in setOf("http", "https", "mailto")) return false
        return start(context, Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    /** The system share sheet with the article's link. */
    fun share(
        context: Context,
        title: String,
        url: String,
        chooserTitle: String,
    ): Boolean = start(context, Intent.createChooser(shareIntent(title, url), chooserTitle))

    /**
     * Sends the article to Instapaper through its share target, as if the user picked
     * Instapaper in the share sheet. False when Instapaper is not installed.
     */
    fun saveToInstapaper(
        context: Context,
        title: String,
        url: String,
    ): Boolean = start(context, shareIntent(title, url).setPackage(INSTAPAPER_PACKAGE))

    fun isInstapaperInstalled(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo(INSTAPAPER_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    fun openStoreListing(
        context: Context,
        packageName: String,
    ) {
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
        val web = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri())
        if (!start(context, market)) start(context, web)
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
