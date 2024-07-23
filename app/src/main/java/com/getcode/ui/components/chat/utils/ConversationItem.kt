package com.getcode.ui.components.chat.utils

import com.getcode.model.ConversationMessage
import com.getcode.model.ConversationMessageContent
import com.getcode.model.chat.MessageContent

data class ConversationMessageIndice(
    val message: ConversationMessage,
    val messageContent: MessageContent,
)