package xyz.flipchat.app.features.chat.conversation

import com.getcode.model.ID
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.Sender

data class MessageReplyAnchor(
    val id: ID,
    val sender: Sender,
    val message: MessageContent
)