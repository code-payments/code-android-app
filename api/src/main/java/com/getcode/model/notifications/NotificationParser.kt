package com.getcode.model.notifications

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.chat.MessageContent
import com.getcode.network.repository.decodeBase64
import com.getcode.utils.ErrorUtils
import com.google.firebase.messaging.RemoteMessage
import timber.log.Timber

private const val NOTIFICATION_TYPE_KEY = "code_notification_type"
private const val NOTIFICATION_TITLE_KEY = "chat_title"
private const val NOTIFICATION_CONTENT_KEY = "message_content"

fun RemoteMessage.parse(): CodeNotification? {
    Timber.d("data=$data")
    val typeString = data[NOTIFICATION_TYPE_KEY].let {
        if (it == null) {
            Timber.e("$NOTIFICATION_TYPE_KEY unspecified")
            ErrorUtils.handleError(Throwable("$NOTIFICATION_TYPE_KEY unspecified"))
            return null
        }
        it
    }

    val type = NotificationType.tryValueOf(typeString)
    if (type == NotificationType.Unknown) {
        Timber.e("Unknown notification type: $typeString")
        ErrorUtils.handleError(Throwable("Unknown notification type: $typeString"))
        return null
    }

    if (!type.isNotifiable()) return CodeNotification(type, "", MessageContent.Localized("", false))

    val chatTitle = data[NOTIFICATION_TITLE_KEY].let {
        if (it == null) {
            Timber.e("$NOTIFICATION_TITLE_KEY unspecified")
            ErrorUtils.handleError(Throwable("$NOTIFICATION_TITLE_KEY unspecified"))
            return null
        }
        it
    }
    val messageContent = data[NOTIFICATION_CONTENT_KEY].let {
        if (it == null) {
            Timber.e("$NOTIFICATION_CONTENT_KEY unspecified")
            ErrorUtils.handleError(Throwable("$NOTIFICATION_CONTENT_KEY unspecified"))
            return null
        }
        it
    }

    val messageData = messageContent.decodeBase64()
    val rawContent = runCatching {
        ChatService.Content.parseFrom(messageData) }.getOrNull().let {
        if (it == null) {
            Timber.e("unable to parse message content")
            ErrorUtils.handleError(Throwable("unable to parse message content"))
            return null
        }
        it
    }
    val content = MessageContent.invoke(rawContent).let {
        if (it == null) {
            Timber.e("failed to convert MessageContent")
            ErrorUtils.handleError(Throwable("failed to convert MessageContent"))
            return null
        }
        it
    }
    return CodeNotification(type, chatTitle, content)
}