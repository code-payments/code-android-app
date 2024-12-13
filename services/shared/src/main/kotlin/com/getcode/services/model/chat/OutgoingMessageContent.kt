package com.getcode.services.model.chat

import com.getcode.model.ID

sealed interface OutgoingMessageContent {
    data class Text(val text: String): OutgoingMessageContent
    data class Reply(val messageId: ID, val text: String): OutgoingMessageContent
}