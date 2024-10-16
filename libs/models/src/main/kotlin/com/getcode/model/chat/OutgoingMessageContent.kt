package com.getcode.model.chat

sealed interface OutgoingMessageContent {
    data class Text(val text: String): OutgoingMessageContent
}