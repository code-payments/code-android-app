package xyz.flipchat.services.domain.model.chat.db

import xyz.flipchat.services.domain.model.chat.Conversation
import xyz.flipchat.services.domain.model.chat.ConversationMember
import xyz.flipchat.services.domain.model.chat.ConversationMessageWithContent

data class ChatDbUpdate(
    val conversations: List<Conversation> = emptyList(),
    val messages: List<ConversationMessageWithContent> = emptyList(),
    val members: List<ConversationMember> = emptyList(),
)
