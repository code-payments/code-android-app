package com.getcode.model.chat

import com.getcode.model.ID

sealed interface OutgoingMessageContent {
    data class Text(val text: String): OutgoingMessageContent
    data class ThankYou(val tipIntentId: ID): OutgoingMessageContent
}