package be.wimvandonck.content.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import be.wimvandonck.content.BuildConfig
import be.wimvandonck.content.R
import be.wimvandonck.content.data.SettingsStore
import be.wimvandonck.content.sync.SyncManager
import be.wimvandonck.content.ui.MainActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Notifications when something is shared with you (#235): Firebase Cloud Messaging. Off until the user
 * turns it on (Settings › Account); then this phone's Firebase token goes to the feed loader, which
 * pokes it when someone shares with the account (worker/src/push.ts). The poke says only that
 * something was shared; this shows "Something was shared with you", and tapping it opens Saved ›
 * Shared with me. Nothing is downloaded in the background. Without Firebase settings in the build
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

    /** Firebase gave this phone a new token: the feed loader gets it while notifications are on. */
    suspend fun tokenChanged(token: String) {
        if (settings.settings.value?.notifyShared == true) sync.setPush(token)
    }

    /** Shows "Something was shared with you"; tapping it opens Saved › Shared with me. */
    fun showShared() {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.notify_shared_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_ARTICLE, MainActivity.OPEN_SHARED)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_group)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notify_shared_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The permission was taken away in the meantime.
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
        const val CHANNEL = "shared"
        const val NOTIFICATION_ID = 235
    }
}
