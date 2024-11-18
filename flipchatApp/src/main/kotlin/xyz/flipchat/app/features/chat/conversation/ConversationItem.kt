package xyz.flipchat.app.features.chat.conversation

import com.getcode.model.chat.MessageContent
import xyz.flipchat.services.domain.model.chat.ConversationMember
import xyz.flipchat.services.domain.model.chat.ConversationMessage

data class ConversationMessageIndice(
    val message: ConversationMessage,
    val sender: ConversationMember?,
    val messageContent: MessageContent,
)