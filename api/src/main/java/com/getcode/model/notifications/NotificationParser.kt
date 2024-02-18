package com.getcode.model.notifications

import com.codeinc.gen.chat.v1.ChatService
import com.getcode.model.MessageContent
import com.getcode.network.repository.decodeBase64
import com.google.firebase.messaging.RemoteMessage
import timber.log.Timber

private const val NOTIFICATION_TYPE_KEY = "code_notification_type"
private const val NOTIFICATION_TITLE_KEY = "chat_title"
private const val NOTIFICATION_CONTENT_KEY = "message_content"

fun RemoteMessage.parse(): CodeNotification? {
    Timber.d("data=$data")
    val type = data[NOTIFICATION_TYPE_KEY].let {
        if (it == null) {
            Timber.e("$NOTIFICATION_TYPE_KEY unspecified")
            return null
        }
        it
    }
    val chatTitle = data[NOTIFICATION_TITLE_KEY].let {
        if (it == null) {
            Timber.e("$NOTIFICATION_TITLE_KEY unspecified")
            return null
        }
        it
    }
    val messageContent = data[NOTIFICATION_CONTENT_KEY].let {
        if (it == null) {
            Timber.e("$NOTIFICATION_CONTENT_KEY unspecified")
            return null
        }
        it
    }

    val messageData = messageContent.decodeBase64()
    val rawContent = runCatching {
        ChatService.Content.parseFrom(messageData) }.getOrNull().let {
        if (it == null) {
            Timber.e("unable to parse message content")
            return null
        }
        it
    }
    val content = MessageContent.invoke(rawContent).let {
        if (it == null) {
            Timber.e("failed to convert MessageContent")
            return null
        }
        it
    }
    return CodeNotification(type, chatTitle, content)
}