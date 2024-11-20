package xyz.flipchat.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.getcode.model.ID
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.ResourceType
import com.getcode.utils.CurrencyUtils
import com.getcode.utils.TraceType
import com.getcode.utils.base58
import com.getcode.utils.trace
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import timber.log.Timber
import xyz.flipchat.app.MainActivity
import xyz.flipchat.app.R
import xyz.flipchat.app.auth.AuthManager
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.controllers.CodeController
import xyz.flipchat.controllers.PushController
import xyz.flipchat.notifications.FcNotificationType
import xyz.flipchat.notifications.parse
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

@AndroidEntryPoint
class FcNotificationService : FirebaseMessagingService(),
    CoroutineScope by CoroutineScope(Dispatchers.IO) {

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var pushController: PushController

    @Inject
    lateinit var codeController: CodeController

    @Inject
    lateinit var chatsController: ChatsController

    @Inject
    lateinit var resources: ResourceHelper

    @Inject
    lateinit var currencyUtils: CurrencyUtils

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        authenticateIfNeeded { handleMessage(message) }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        println("token=$token")
        authenticateIfNeeded {
            launch {
                pushController.addToken(token)
                    .onSuccess { trace("push token updated", type = TraceType.Process) }
            }
        }
    }

    private fun handleMessage(remoteMessage: RemoteMessage) {
        trace("handling received message", type = TraceType.Silent)
        if (remoteMessage.data.isNotEmpty()) {
            // TODO: remove this for release
            println("Message data payload: ${remoteMessage.data}")
            val notification = remoteMessage.parse()

            if (notification != null) {
                val (type, titleKey, messageContent) = notification
                if (type.isNotifiable()) {
                    val title = titleKey.localizedStringByKey(resources) ?: titleKey
                    val body = messageContent.localizedText(
                        resources = resources,
                        currencyUtils = currencyUtils
                    )

                    when (type) {
                        is FcNotificationType.ChatMessage -> {
                            if (type.id != userManager.openRoom) {
                                // only notify when not for current room
                                notify(type, title, body)
                            }
                        }
                        FcNotificationType.Unknown -> {
                            notify(type, title, body)
                        }
                    }
                }

                when (type) {
                    is FcNotificationType.ChatMessage -> {
                        launch { chatsController.fetchLatestChatsAndMessage() }
                        launch { codeController.fetchBalance() }
                    }

                    FcNotificationType.Unknown -> Unit
                }
            } else {
                notify(
                    FcNotificationType.Unknown,
                    resources.getString(R.string.app_name),
                    "You have a new message."
                )
            }
        }
    }

    private fun authenticateIfNeeded(block: () -> Unit) {
        if (userManager.userId == null) {
            authManager.init { block() }
        } else {
            block()
        }
    }

    private fun notify(
        type: FcNotificationType,
        title: String,
        content: String,
    ) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    type.name,
                    type.name,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val person = Person.Builder()
            .setName(title)
            .build()

        val message = NotificationCompat.MessagingStyle.Message(
            content,
            Clock.System.now().toEpochMilliseconds(),
            person
        )

        val notificationId = when (type) {
            is FcNotificationType.ChatMessage -> (type.id?.base58 ?: title).hashCode()
            FcNotificationType.Unknown -> title.hashCode()
        }

        val style = notificationManager.getActiveNotification(notificationId)?.let {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it)
        } ?: NotificationCompat.MessagingStyle(person)

        val notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, type.name)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setStyle(style.addMessage(message))
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setSmallIcon(R.drawable.ic_flipchat_notification)
                .setAutoCancel(true)
                .setContentIntent(buildContentIntent(type))

        notificationManager.notify(title.hashCode(), notificationBuilder.build())

        trace(
            tag = "Push",
            message = "Push notification shown",
            metadata = {
                "category" to type.name
            },
            type = TraceType.Process
        )
    }
}

private fun NotificationManager.getActiveNotification(notificationId: Int): Notification? {
    val barNotifications = getActiveNotifications()
    for (notification in barNotifications) {
        if (notification.id == notificationId) {
            return notification.notification
        }
    }
    return null
}

fun NotificationManager.getRoomNotifications(roomId: ID, roomName: String): List<Notification> {
    val barNotifications = getActiveNotifications()
    val roomNotifications = barNotifications.mapNotNull { notification ->
        val roomIdHash = roomId.base58.hashCode()
        val roomNameHash = roomName.hashCode()

        val isMatch = notification.id == roomIdHash || notification.id == roomNameHash

        if (isMatch) {
            notification.notification
        } else {
            null
        }
    }

    return roomNotifications
}

private fun Context.buildContentIntent(type: FcNotificationType): PendingIntent {
    val launchIntent = when (type) {
        is FcNotificationType.ChatMessage -> Intent("https://app.flipchat.xyz/chat/${type.id?.base58}")
        FcNotificationType.Unknown -> Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
    }

    return PendingIntent.getActivity(
        this,
        type.ordinal,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun String.localizedStringByKey(resources: ResourceHelper): String? {
    val name = this.replace(".", "_")
    val resId = resources.getIdentifier(
        name,
        ResourceType.String,
    ).let { if (it == 0) null else it }

    return resId?.let { resources.getString(it) }
}