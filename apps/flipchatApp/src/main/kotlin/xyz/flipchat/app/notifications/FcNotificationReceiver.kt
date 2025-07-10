package xyz.flipchat.app.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.getcode.vendor.Base58
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import xyz.flipchat.app.auth.AuthManager
import xyz.flipchat.chat.RoomController
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@AndroidEntryPoint
class FcNotificationReceiver : BroadcastReceiver() {

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var roomController: RoomController

    @Inject
    lateinit var notificationManager: NotificationManagerCompat

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        if (remoteInput != null) {
            val roomId = runCatching {
                Base58.decode(
                    intent.getStringExtra(FcNotificationService.KEY_ROOM_ID).orEmpty()
                ).toList()
            }.getOrNull()

            val notificationId = intent.getIntExtra(FcNotificationService.KEY_NOTIFICATION_ID, -1).takeIf { it > 0 }
            if (notificationId != null) {
                val activeNotification = notificationManager.getActiveNotification(notificationId)
                if (activeNotification != null) {
                    if (roomId != null) {
                        val message =
                            remoteInput.getCharSequence(FcNotificationService.KEY_TEXT_REPLY).toString()
                        authenticateIfNeeded {
                            goAsync {
                                roomController.sendMessage(roomId, message)
                                    .onFailure {
                                        it.printStackTrace()
                                    }.onSuccess {
                                        println("Message sent via notification!")
                                        addReply(context, message, notificationId, activeNotification)
                                    }
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun addReply(
        context: Context,
        text: String,
        notificationId: Int,
        activeNotification: Notification
    ) {
        val activeStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(activeNotification) ?: return

        // Recover builder from the active notification.
        val recoveredBuilder = NotificationCompat.Builder(context, activeNotification)

        val person = Person.Builder()
            .setName("You")
            .build()

        val message = NotificationCompat.MessagingStyle.Message(
            text,
            Clock.System.now().toEpochMilliseconds(),
            person
        )

        val newStyle = NotificationCompat.MessagingStyle(person)
            .setConversationTitle(activeStyle.conversationTitle)

        activeStyle.messages.onEach { newStyle.addMessage(it) }

        newStyle.addMessage(message)

        // Set the new style to the recovered builder.
        recoveredBuilder.setStyle(newStyle)

        // Update the active notification.
        NotificationManagerCompat.from(context).notify(notificationId, recoveredBuilder.build())
    }

    private fun authenticateIfNeeded(block: () -> Unit) {
        if (userManager.userId == null) {
            authManager.init { block() }
        } else {
            block()
        }
    }
}

fun BroadcastReceiver.goAsync(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> Unit
) {
    val pendingResult = goAsync()
    @OptIn(DelicateCoroutinesApi::class) // Must run globally; there's no teardown callback.
    GlobalScope.launch(context) {
        try {
            block()
        } finally {
            pendingResult.finish()
        }
    }
}