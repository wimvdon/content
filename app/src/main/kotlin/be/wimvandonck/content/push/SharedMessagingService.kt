package be.wimvandonck.content.push

import be.wimvandonck.content.rssReaderApp
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch

/** Firebase's messages to this phone (#235): a poke that something was shared, and new tokens. */
class SharedMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["kind"] == "shared") rssReaderApp.push.showShared()
    }

    override fun onNewToken(token: String) {
        val app = rssReaderApp
        app.appScope.launch { app.push.tokenChanged(token) }
    }
}
