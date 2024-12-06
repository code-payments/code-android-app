package xyz.flipchat.app.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
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
import xyz.flipchat.app.theme.FC_Primary
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.controllers.CodeController
import xyz.flipchat.controllers.PushController
import xyz.flipchat.notifications.FcNotificationType
import xyz.flipchat.notifications.parse
import xyz.flipchat.services.user.UserManager
import java.security.SecureRandom
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class FcNotificationService : FirebaseMessagingService(),
    CoroutineScope by CoroutineScope(Dispatchers.IO) {

    companion object {
        const val KEY_NOTIFICATION_ID = "key_notification_id"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val KEY_ROOM_ID = "key_room_id"
    }

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

    @Inject
    lateinit var notificationManager: NotificationManagerCompat

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        authenticateIfNeeded { handleMessage(message) }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
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
            Timber.d("Message data payload: ${remoteMessage.data}")
            val notification = remoteMessage.parse()

            if (notification != null) {
                val (type, titleKey, messageContent) = notification
                if (type.isNotifiable()) {
                    val title = titleKey.localizedStringByKey(resources) ?: titleKey
                    val body = messageContent.localizedText(
                        resources = resources,
                        currencyUtils = currencyUtils
                    )

                    val result = buildNotification(type, title, body)
                    if (result != null) {
                        notify(result.first, result.second, type)
                    }
                }

                when (type) {
                    is FcNotificationType.ChatMessage -> {
                        val roomId = type.id
                        if (roomId != null) {
                            launch { chatsController.updateRoom(roomId) }
                        }
                    }

                    FcNotificationType.Unknown -> Unit
                }
            } else {
                val result = buildNotification(
                    FcNotificationType.Unknown,
                    resources.getString(R.string.app_name),
                    "You have a new message."
                )
                if (result != null) {
                    notify(result.first, result.second, FcNotificationType.Unknown)
                }
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

    private fun buildNotification(
        type: FcNotificationType,
        title: String,
        content: String,
    ): Pair<Int, Notification>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    type.name,
                    type.name,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        if (type is FcNotificationType.ChatMessage && type.id == userManager.openRoom) {
            return null
        }

        val (id, notification) = when (type) {
            is FcNotificationType.ChatMessage -> buildChatNotification(type, title, content)
            FcNotificationType.Unknown -> buildMiscNotification(type, title, content)
        }

        return id to notification.build()
    }

    private fun buildChatNotification(
        type: FcNotificationType.ChatMessage,
        title: String,
        content: String
    ): Pair<Int, NotificationCompat.Builder> {
        val sender = content.substringBefore(":")
        val messageBody = content.substringAfter(":")
        val person = Person.Builder()
            .setName(sender)
            .build()

        val message = NotificationCompat.MessagingStyle.Message(
            messageBody,
            Clock.System.now().toEpochMilliseconds(),
            person
        )

        val notificationId = type.id?.base58.hashCode()

        val style = notificationManager.getActiveNotification(notificationId)?.let {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it)
        } ?: NotificationCompat.MessagingStyle(person)
            .setConversationTitle(title)
            .setGroupConversation(true)

        val updatedStyle = style.addMessage(message)

        val replyAction = if (type.id != null) {
            // build direct reply action
            val replyLabel: String = resources.getString(R.string.action_reply)
            val remoteInput: RemoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).run {
                setLabel(replyLabel)
                build()
            }

            val resultIntent = Intent(applicationContext, FcNotificationReceiver::class.java).apply {
                putExtra(KEY_ROOM_ID, type.id!!.base58)
                putExtra(KEY_NOTIFICATION_ID, notificationId)
            }

            val replyPendingIntent: PendingIntent =
                PendingIntent.getBroadcast(
                    applicationContext,
                    type.id.hashCode(),
                    resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )

            NotificationCompat.Action.Builder(
                R.drawable.ic_reply,
                getString(R.string.action_reply),
                replyPendingIntent
            ).addRemoteInput(remoteInput).build()
        } else {
            null
        }

        val notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, type.name)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setStyle(updatedStyle)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setSmallIcon(R.drawable.ic_flipchat_notification)
                .setColor(FC_Primary.toArgb())
                .setAutoCancel(true)
                .setContentIntent(buildContentIntent(type))

        if (replyAction != null) {
            notificationBuilder.addAction(replyAction)
        }

        return notificationId to notificationBuilder
    }

    private fun buildMiscNotification(
        type: FcNotificationType,
        title: String,
        content: String
    ): Pair<Int, NotificationCompat.Builder> {
        val notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, type.name)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setSmallIcon(R.drawable.ic_flipchat_notification)
                .setColor(FC_Primary.toArgb())
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(buildContentIntent(type))

        val random = SecureRandom()
        val notificationId = random.nextInt(256)

        return notificationId to notificationBuilder
    }

    private fun notify(
        id: Int,
        notification: Notification,
        type: FcNotificationType,
    ) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(id, notification)
            trace(
                tag = "Push",
                message = "Push notification shown",
                metadata = {
                    "category" to type.name
                },
                type = TraceType.Process
            )
        } else {
            trace(
                tag = "Push",
                message = "Push notification NOT shown - missing permission",
                metadata = {
                    "category" to type.name
                },
                type = TraceType.Process
            )
        }
    }
}

private fun NotificationManagerCompat.getActiveNotification(notificationId: Int): Notification? {
    val barNotifications = activeNotifications
    for (notification in barNotifications) {
        if (notification.id == notificationId) {
            return notification.notification
        }
    }
    return null
}

private fun Context.buildContentIntent(type: FcNotificationType): PendingIntent {
    val launchIntent = when (type) {
        is FcNotificationType.ChatMessage -> Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://app.flipchat.xyz/room?r=${type.id?.base58}")
        }

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