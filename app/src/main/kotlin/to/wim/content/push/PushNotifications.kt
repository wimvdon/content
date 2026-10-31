package to.wim.content.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import to.wim.content.BuildConfig
import to.wim.content.R
import to.wim.content.data.SettingsStore
import to.wim.content.sync.SyncManager
import to.wim.content.ui.MainActivity
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Notifications when something is shared with you (#235): Firebase Cloud Messaging. Off until the user
 * turns it on (Settings › Account); then this phone's Firebase token goes to the feed loader, which
 * pokes it when someone shares with the account (worker/src/push.ts). The poke carries a preview
 * ([SharedPreview]: the first article's title, intro and picture address, and who shared it), which
 * the notification shows with the picture as its thumbnail, downloaded from the article's site; an
 * older feed loader sends none, and it says "Something was shared with you". Tapping it opens the
 * inbox. It arrives silently (its channel is quiet; Android's settings can make it louder). Without Firebase settings in the build
 * (app/google-services.json) there are no notifications and the setting isn't shown.
 */
class PushNotifications(
    private val context: Context,
    private val settings: SettingsStore,
    private val sync: SyncManager,
) {
    /** The build has Firebase settings. */
    val available: Boolean get() = BuildConfig.FIREBASE_APP_ID.isNotEmpty()

    /** Starts Firebase with the build's settings (no google-services plugin), once. */
    fun start() {
        if (!available || FirebaseApp.getApps(context).isNotEmpty()) return
        val options =
            FirebaseOptions
                .Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build()
        FirebaseApp.initializeApp(context, options)
    }

    /** Turns notifications on: this phone's token to the feed loader. False when that didn't work. */
    suspend fun turnOn(): Boolean {
        if (!available) return false
        start()
        val messaging = FirebaseMessaging.getInstance()
        messaging.isAutoInitEnabled = true
        val token = token(messaging) ?: return false
        if (!sync.setPush(token)) return false
        settings.setNotifyShared(true)
        return true
    }

    /** Turns notifications off: the feed loader forgets this phone, and Firebase its token. */
    suspend fun turnOff() {
        settings.setNotifyShared(false)
        if (!available) return
        sync.setPush(null)
        forgetToken()
    }

    /** Signed out: the feed loader forgot this phone already (with its key); Firebase forgets the token. */
    suspend fun signedOut() {
        if (settings.settings.value?.notifyShared != true) return
        settings.setNotifyShared(false)
        forgetToken()
    }

    /**
     * While notifications are on, gives the feed loader this phone's token again (it keeps one per
     * device, so this changes nothing when it has it). Run when the app starts: a phone the feed loader
     * forgot (a refused message once counted as a gone phone) gets its notifications back.
     */
    suspend fun refresh() {
        if (!available || settings.settings.value?.notifyShared != true) return
        start()
        token(FirebaseMessaging.getInstance())?.let { sync.setPush(it) }
    }

    /** Firebase gave this phone a new token: the feed loader gets it while notifications are on. */
    suspend fun tokenChanged(token: String) {
        if (settings.settings.value?.notifyShared == true) sync.setPush(token)
    }

    /**
     * Shows what was shared ([preview]: its title, intro, thumbnail and who shared it), or "Something
     * was shared with you" without one; tapping it opens the inbox. Runs on Firebase's background
     * thread, which may wait for the thumbnail a few seconds.
     */
    fun showShared(preview: SharedPreview?) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        // Silent by default (a channel's sound can't change once made, so the old one goes).
        manager.deleteNotificationChannel(OLD_CHANNEL)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.notify_shared_channel), NotificationManager.IMPORTANCE_LOW))
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_ARTICLE, MainActivity.OPEN_SHARED)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val builder =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_group)
                .setContentIntent(open)
                .setAutoCancel(true)
        if (preview == null) {
            builder.setContentTitle(context.getString(R.string.app_name)).setContentText(context.getString(R.string.notify_shared_text))
        } else {
            val more = preview.count - 1
            val text =
                listOfNotNull(
                    preview.intro.takeIf { it.isNotEmpty() },
                    if (more > 0) context.resources.getQuantityString(R.plurals.notify_shared_more, more, more) else null,
                ).joinToString(" · ")
            builder
                .setContentTitle(preview.title)
                .setContentText(text.ifEmpty { null })
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            if (preview.from.isNotEmpty()) builder.setSubText(context.getString(R.string.notify_shared_by, preview.from))
            preview.imageUrl?.let(::thumbnail)?.let(builder::setLargeIcon)
        }
        val notification = builder.build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The permission was taken away in the meantime.
        }
    }

    /** The article's picture, small, for the notification; null when it doesn't load in time. */
    private fun thumbnail(url: String): Bitmap? =
        runBlocking {
            withTimeoutOrNull(THUMBNAIL_TIMEOUT_MS) {
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .size(THUMBNAIL_PX)
                        .allowHardware(false)
                        .build()
                (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
            }
        }

    private suspend fun token(messaging: FirebaseMessaging): String? =
        suspendCancellableCoroutine { done ->
            messaging.token.addOnCompleteListener { task -> done.resume(if (task.isSuccessful) task.result else null) }
        }

    private suspend fun forgetToken() {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val messaging = FirebaseMessaging.getInstance()
        messaging.isAutoInitEnabled = false
        suspendCancellableCoroutine { done -> messaging.deleteToken().addOnCompleteListener { done.resume(Unit) } }
    }

    private companion object {
        const val CHANNEL = "shared_quiet"

        /** The first channel, which made a sound. */
        const val OLD_CHANNEL = "shared"
        const val THUMBNAIL_PX = 256
        const val THUMBNAIL_TIMEOUT_MS = 5_000L
        const val NOTIFICATION_ID = 235
    }
}
