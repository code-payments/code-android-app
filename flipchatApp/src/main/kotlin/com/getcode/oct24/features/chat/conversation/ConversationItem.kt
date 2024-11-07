package com.getcode.oct24.features.chat.conversation

import com.getcode.model.chat.MessageContent
import com.getcode.oct24.domain.model.chat.ConversationMember
import com.getcode.oct24.domain.model.chat.ConversationMessage

data class ConversationMessageIndice(
    val message: ConversationMessage,
    val sender: ConversationMember?,
    val messageContent: MessageContent,
)