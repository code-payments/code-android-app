package com.flipcash.services.models.chat

data class ChatUpdate(
    val chatId: ChatId,
    val newMessages: List<ChatMessage>,
    val pointerUpdates: List<MessagePointer>,
    val typingNotifications: List<TypingNotification>,
    val metadataUpdates: List<MetadataUpdate>,
)
