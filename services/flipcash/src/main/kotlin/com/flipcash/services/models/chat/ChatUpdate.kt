package com.flipcash.services.models.chat

data class ChatUpdate(
    val chatId: ChatId,
    val pointerUpdates: List<MessagePointer> = emptyList(),
    val typingNotifications: List<TypingNotification> = emptyList(),
    val metadataUpdates: List<MetadataUpdate> = emptyList(),
    val events: List<ChatEvent> = emptyList(),
    val reactionUpdates: List<ReactionUpdate> = emptyList(),
    // Convergent, like reactionUpdates and unlike events: applied by RosterSummary.version,
    // never gap-filled. See RosterChange.
    val rosterUpdates: List<RosterChange> = emptyList(),
)
