package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId

/** The system notifications chat posts, as far as chat needs to take them back. */
interface ChatNotifications {
    /** Removes the notification shown for [chatId], if any. */
    fun dismiss(chatId: ChatId)

    /** Does nothing. The default for a caller with no notification surface, as in unit tests. */
    object None : ChatNotifications {
        override fun dismiss(chatId: ChatId) = Unit
    }
}
