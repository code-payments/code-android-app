package com.flipcash.shared.chat.ui

import com.flipcash.services.models.chat.ChatId

/** Presentation state derived from an existing DM with a contact. */
data class ConversationReference(
    val chatId: ChatId,
    val lastMessagePreview: String? = null,
    val unreadCount: Int = 0,
    val isTyping: Boolean = false,
)