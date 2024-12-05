package com.getcode.services.model.chat

sealed interface OutgoingMessageContent {
    data class Text(val text: String): OutgoingMessageContent
}