package com.flipcash.services.models.chat

sealed interface ChatMutation {
    val message: ChatMessage

    data class MessageSent(override val message: ChatMessage) : ChatMutation
    data class MessageEdited(override val message: ChatMessage) : ChatMutation
    data class MessageDeleted(override val message: ChatMessage) : ChatMutation
}
