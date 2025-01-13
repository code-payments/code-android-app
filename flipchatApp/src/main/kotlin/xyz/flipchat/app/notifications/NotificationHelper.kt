package xyz.flipchat.app.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.base58
import kotlinx.datetime.Clock
import xyz.flipchat.app.MainActivity
import xyz.flipchat.app.R
import xyz.flipchat.app.notifications.FcNotificationService.Companion.KEY_NOTIFICATION_ID
import xyz.flipchat.app.notifications.FcNotificationService.Companion.KEY_ROOM_ID
import xyz.flipchat.app.notifications.FcNotificationService.Companion.KEY_TEXT_REPLY
import xyz.flipchat.app.theme.FC_Primary
import xyz.flipchat.notifications.FcNotificationType
import java.security.SecureRandom

internal fun NotificationManagerCompat.buildChatNotification(
    context: Context,
    resources: ResourceHelper,
    type: FcNotificationType.ChatMessage,
    roomNumber: Long?,
    title: String,
    content: String,
    canReply: Boolean,
): Pair<Int, NotificationCompat.Builder> {
    val sender = content.substringBefore(":").trim().ifEmpty { type.sender } ?: "Sender"
    val messageBody = content.substringAfter(":").trim()
    val person = Person.Builder()
        .setName(sender)
        .build()

    val message = NotificationCompat.MessagingStyle.Message(
        messageBody,
        Clock.System.now().toEpochMilliseconds(),
        person
    )

    val notificationId = type.id?.base58.hashCode()

    val style = getActiveNotification(notificationId)?.let {
        NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it)
    } ?: NotificationCompat.MessagingStyle(person)
        .setConversationTitle(title)
        .setGroupConversation(true)

    val updatedStyle = style.addMessage(message)

    val replyAction = if (type.id != null && canReply) {
        // build direct reply action
        val replyLabel: String = resources.getString(R.string.action_reply)
        val remoteInput: RemoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).run {
            setLabel(replyLabel)
            build()
        }

        val resultIntent = Intent(context, FcNotificationReceiver::class.java).apply {
            putExtra(KEY_ROOM_ID, type.id!!.base58)
            putExtra(KEY_NOTIFICATION_ID, notificationId)
        }

        val replyPendingIntent: PendingIntent =
            PendingIntent.getBroadcast(
                context,
                type.id.hashCode(),
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

        NotificationCompat.Action.Builder(
            R.drawable.ic_reply,
            context.getString(R.string.action_reply),
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()
    } else {
        null
    }

    val notificationBuilder: NotificationCompat.Builder =
        NotificationCompat.Builder(context, type.name)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(updatedStyle)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setSmallIcon(R.drawable.ic_flipchat_notification)
            .setColor(FC_Primary.toArgb())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(context.buildContentIntent(type.copy(roomNumber = roomNumber)))

    if (replyAction != null) {
        notificationBuilder.addAction(replyAction)
    }

    return notificationId to notificationBuilder
}

internal fun NotificationManagerCompat.buildMiscNotification(
    context: Context,
    type: FcNotificationType,
    title: String,
    content: String
): Pair<Int, NotificationCompat.Builder> {
    val notificationBuilder: NotificationCompat.Builder =
        NotificationCompat.Builder(context, type.name)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setSmallIcon(R.drawable.ic_flipchat_notification)
            .setColor(FC_Primary.toArgb())
            .setAutoCancel(true)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(context.buildContentIntent(type))

    val random = SecureRandom()
    val notificationId = random.nextInt(256)

    return notificationId to notificationBuilder
}

internal fun NotificationManagerCompat.getActiveNotification(notificationId: Int): Notification? {
    val barNotifications = activeNotifications
    for (notification in barNotifications) {
        if (notification.id == notificationId) {
            return notification.notification
        }
    }
    return null
}

internal fun Context.buildContentIntent(
    type: FcNotificationType
): PendingIntent {
    val launchIntent = when (type) {
        is FcNotificationType.ChatMessage -> Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://app.flipchat.xyz/room/${type.roomNumber}")
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