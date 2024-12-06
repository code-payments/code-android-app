package xyz.flipchat.services.domain.model.chat.db

import xyz.flipchat.services.domain.model.chat.Conversation
import xyz.flipchat.services.domain.model.chat.ConversationMember
import xyz.flipchat.services.domain.model.chat.ConversationMessage

data class ChatDbUpdate(
    val conversation: Conversation?,
    val message: ConversationMessage?,
    val members: List<ConversationMember> = emptyList(),
)
