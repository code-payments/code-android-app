package com.getcode.services.model.chat

import com.getcode.model.ID
import com.getcode.model.KinAmount

sealed interface OutgoingMessageContent {
    data class Text(val text: String, val intentId: ID? = null): OutgoingMessageContent
    data class Reply(val messageId: ID, val text: String): OutgoingMessageContent
    data class Reaction(val messageId: ID, val emoji: String): OutgoingMessageContent
    data class Tip(val messageId: ID, val amount: KinAmount, val intentId: ID): OutgoingMessageContent
    data class DeleteRequest(val messageId: ID): OutgoingMessageContent
}