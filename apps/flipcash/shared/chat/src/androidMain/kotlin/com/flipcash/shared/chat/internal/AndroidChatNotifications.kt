package com.flipcash.shared.chat.internal

import androidx.core.app.NotificationManagerCompat
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatNotifications
import javax.inject.Inject
import javax.inject.Singleton

/** [ChatNotifications] over the system notification manager. */
@Singleton
internal class AndroidChatNotifications @Inject constructor(
    private val notificationManager: NotificationManagerCompat,
) : ChatNotifications {

    override fun dismiss(chatId: ChatId) {
        notificationManager.cancel(chatId.hashCode())
    }
}
