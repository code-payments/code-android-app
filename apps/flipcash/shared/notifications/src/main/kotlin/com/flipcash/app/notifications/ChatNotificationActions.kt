package com.flipcash.app.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.notifications.R

/**
 * The reply and mark-as-read actions on a chat notification, built in one place so the push and
 * the post-reply re-post carry the same ones.
 *
 * Both carry a semantic action. Android Auto only shows a messaging notification's actions when
 * they are tagged [NotificationCompat.Action.SEMANTIC_ACTION_REPLY] and
 * [NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ], and it won't open the app to run
 * them, hence `setShowsUserInterface(false)`: [NotificationActionReceiver] handles both headlessly.
 */
@OptIn(ExperimentalStdlibApi::class)
internal fun buildChatReplyAction(
    context: Context,
    chatId: ChatId,
    notificationId: Int,
): NotificationCompat.Action {
    val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_TEXT_REPLY)
        .setLabel(context.getString(R.string.notification_action_reply))
        .build()

    val intent = Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationActionReceiver.ACTION_REPLY
        putExtra(NotificationActionReceiver.KEY_CHAT_ID_HEX, chatId.bytes.toHexString())
        putExtra(NotificationActionReceiver.KEY_NOTIFICATION_ID, notificationId)
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        chatId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    return NotificationCompat.Action.Builder(
        R.drawable.ic_reply,
        context.getString(R.string.notification_action_reply),
        pendingIntent,
    )
        .addRemoteInput(remoteInput)
        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
        .setShowsUserInterface(false)
        .build()
}

@OptIn(ExperimentalStdlibApi::class)
internal fun buildChatMarkAsReadAction(context: Context, chatId: ChatId): NotificationCompat.Action {
    val intent = Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationActionReceiver.ACTION_MARK_READ
        putExtra(NotificationActionReceiver.KEY_CHAT_ID_HEX, chatId.bytes.toHexString())
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        chatId.hashCode() + 1,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    return NotificationCompat.Action.Builder(
        R.drawable.ic_mark_read,
        context.getString(R.string.notification_action_mark_as_read),
        pendingIntent,
    )
        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
        .setShowsUserInterface(false)
        .build()
}
