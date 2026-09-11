package com.flipcash.services.models.chat

data class ChatUpdate(
    val chatId: ChatId,
    val pointerUpdates: List<MessagePointer> = emptyList(),
    val typingNotifications: List<TypingNotification> = emptyList(),
    val metadataUpdates: List<MetadataUpdate> = emptyList(),
    val events: List<ChatEvent> = emptyList(),
    val reactionUpdates: List<ReactionUpdate> = emptyList(),
)
