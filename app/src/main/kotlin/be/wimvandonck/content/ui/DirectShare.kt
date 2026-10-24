package be.wimvandonck.content.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import be.wimvandonck.content.data.ShareShortcuts
import be.wimvandonck.content.sync.Gravatar
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap

/**
 * Direct share (#235, #246): Send with Content in Android's share menu (the SendActivity alias, only
 * while signed in), and the people shared with as sharing shortcuts at the top of it, in every app's
 * share menu and in Content's own. Picking one opens Send with Content with their address filled in.
 * Pictures come from Gravatar when Show images is on, else the first letter of the name.
 */
object DirectShare {
    /** The alias that takes links to send to a Content user (AndroidManifest.xml). */
    const val SEND_ACTIVITY = "be.wimvandonck.content.ui.SendActivity"

    /** A person offered in the share menu: their address, name (else address) and picture. */
    class Recipient(
        val email: String,
        val label: String,
        val picture: Bitmap,
    )

    /** Signed in, as last told to [showSendTarget]: Content's own share menu then offers Send with Content (#248). */
    @Volatile
    var signedIn: Boolean = false
        private set

    /** The people last published, most recent first, for the buttons in Content's own share menu (#248). */
    @Volatile
    var people: List<Recipient> = emptyList()
        private set

    /** Shows Send with Content in the share menu while signed in, and hides it otherwise. */
    fun showSendTarget(
        context: Context,
        signedIn: Boolean,
    ) {
        this.signedIn = signedIn
        val state = if (signedIn) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val component = ComponentName(context, SEND_ACTIVITY)
        if (context.packageManager.getComponentEnabledSetting(component) != state) {
            context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }

    suspend fun publish(
        context: Context,
        recipients: List<String>,
        showImages: Boolean,
        nameOf: suspend (String) -> String?,
    ) {
        val picked =
            ShareShortcuts.pick(recipients).map { email ->
                val label = nameOf(email)?.takeIf { it.isNotBlank() } ?: email
                Recipient(email, label, picture(context, email, label, showImages))
            }
        people = picked
        val shortcuts =
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
                    .build()
            }
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
        if (shortcuts.isNotEmpty()) ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts)
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
                val request = ImageRequest.Builder(context).data(Gravatar.url(email, 144)).build()
                (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
            } else {
                null
            }
        if (gravatar != null) {
            val inner = size * 2 / 3
            val left = (size - inner) / 2f
            canvas.drawBitmap(Bitmap.createScaledBitmap(gravatar, inner, inner, true), left, left, null)
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
}
