package com.flipcash.services.models.chat

data class ChatUpdate(
    val chatId: ChatId,
    @Deprecated("Use events instead", replaceWith = ReplaceWith("events"))
    val newMessages: List<ChatMessage> = emptyList(),
    val pointerUpdates: List<MessagePointer> = emptyList(),
    val typingNotifications: List<TypingNotification> = emptyList(),
    val metadataUpdates: List<MetadataUpdate> = emptyList(),
    val events: List<ChatEvent> = emptyList(),
    val reactionUpdates: List<ReactionUpdate> = emptyList(),
)
