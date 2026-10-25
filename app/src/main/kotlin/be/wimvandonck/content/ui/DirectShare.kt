package be.wimvandonck.content.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.drawable.IconCompat
import be.wimvandonck.content.data.Folder
import be.wimvandonck.content.data.IconCatalog
import be.wimvandonck.content.data.LauncherFolders
import be.wimvandonck.content.data.ShareShortcuts
import be.wimvandonck.content.sync.Gravatar
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.SuccessResult
import coil3.toBitmap

/**
 * Direct share (#235, #256): the people shared with as sharing shortcuts at the top of other apps'
 * share menus. Picking one opens Save and share on Don't save, with them picked to send to. Pictures
 * come from Gravatar when Show images is on, else the first letter of the name. Content's own share
 * sheet has them itself (#251).
 *
 * The same dynamic shortcuts hold the launcher's: long-pressing Content's icon offers the feed
 * folders (#251); the people are kept out of the launcher there.
 */
object DirectShare {
    /** A person offered in the share menu: their address, name (else address) and picture. */
    private class Recipient(
        val email: String,
        val label: String,
        val picture: Bitmap,
    )

    /**
     * Publishes the people (for the share menu, not the launcher) and the feed [folders] (for the
     * launcher, #251) as the app's dynamic shortcuts, in place of the ones before.
     */
    suspend fun publish(
        context: Context,
        recipients: List<String>,
        showImages: Boolean,
        nameOf: suspend (String) -> String?,
        folders: List<Folder>,
        iconOf: (String) -> IconCatalog.Icon?,
    ) {
        val picked =
            ShareShortcuts.pick(recipients).map { email ->
                val label = nameOf(email)?.takeIf { it.isNotBlank() } ?: email
                Recipient(email, label, picture(context, email, label, showImages))
            }
        val people =
            picked.mapIndexed { rank, person ->
                val email = person.email
                val label = person.label
                ShortcutInfoCompat
                    .Builder(context, ShareShortcuts.idOf(email))
                    .setShortLabel(label)
                    .setLongLabel(email)
                    .setIcon(IconCompat.createWithAdaptiveBitmap(person.picture))
                    .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW))
                    .setCategories(setOf(ShareShortcuts.CATEGORY))
                    .setPerson(Person.Builder().setName(label).setKey(email).build())
                    .setLongLived(true)
                    .setRank(rank)
                    // Only in share menus: "Wim" in the launcher's menu was no use (#251).
                    .setExcludedFromSurfaces(ShortcutInfoCompat.SURFACE_LAUNCHER)
                    .build()
            }
        val launcher =
            LauncherFolders.pick(folders).mapIndexed { rank, folder ->
                ShortcutInfoCompat
                    .Builder(context, LauncherFolders.idOf(folder.id))
                    .setShortLabel(folder.title)
                    .setIcon(IconCompat.createWithAdaptiveBitmap(folderPicture(iconOf(folder.icon))))
                    .setIntent(
                        Intent(context, MainActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .putExtra(MainActivity.EXTRA_OPEN_ARTICLE, LauncherFolders.requestOf(folder.id)),
                    ).setRank(rank)
                    .build()
            }
        ShortcutManagerCompat.setDynamicShortcuts(context, people + launcher)
    }

    /** A folder's icon (Material Symbols, else the folder) on the letters' colours, as an adaptive icon. */
    private fun folderPicture(icon: IconCatalog.Icon?): Bitmap {
        val size = 216
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawColor(LETTER_BACKGROUND)
        val paths = icon?.paths ?: listOf(FOLDER_PATH)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LETTER_COLOR }
        // The 24-unit icon in the middle third, the part every launcher's mask shows.
        val scale = size / 3f / 24f
        canvas.translate(size / 3f, size / 3f)
        canvas.scale(scale, scale)
        for (data in paths) runCatching { PathParser.createPathFromPathData(data) }.getOrNull()?.let { canvas.drawPath(it, paint) }
        return bitmap
    }

    /** Their Gravatar picture, else their first letter, as an adaptive icon (the middle two thirds show). */
    private suspend fun picture(
        context: Context,
        email: String,
        label: String,
        showImages: Boolean,
    ): Bitmap {
        val size = 216
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawColor(LETTER_BACKGROUND)
        val gravatar =
            if (showImages) {
                // A software bitmap: Coil's hardware ones can't be drawn on this canvas, which threw
                // and left the share menu without people (#249). Any other failure: the letter.
                runCatching {
                    val request = ImageRequest.Builder(context).data(Gravatar.url(email, 144)).allowHardware(false).build()
                    (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
                }.getOrNull()
            } else {
                null
            }
        if (gravatar != null) {
            val inner = size * 2 / 3
            val left = (size - inner) / 2f
            canvas.drawBitmap(gravatar.scale(inner, inner), left, left, null)
        } else {
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = LETTER_COLOR
                    textSize = size / 3f
                    textAlign = Paint.Align.CENTER
                }
            val y = size / 2f - (paint.descent() + paint.ascent()) / 2
            canvas.drawText(label.trim().take(1).uppercase(), size / 2f, y, paint)
        }
        return bitmap
    }

    // The light theme's primary container colours (ui/theme/Theme.kt), fixed: a shortcut icon has no theme.
    private const val LETTER_BACKGROUND = 0xFFD0E4FF.toInt()
    private const val LETTER_COLOR = 0xFF00497A.toInt()

    /** Material Symbols folder (res/drawable/ic_folder.xml), for a folder without a known icon. */
    private const val FOLDER_PATH = "M4 20q-.825 0-1.412-.587T2 18V6q0-.825.588-1.412T4 4h5.175q.4 0 .763.15t.637.425L12 6h8q.825 0 1.413.588T22 8v10q0 .825-.587 1.413T20 20zm0-2h16V8h-8.825l-2-2H4zm0 0V6z"
}
