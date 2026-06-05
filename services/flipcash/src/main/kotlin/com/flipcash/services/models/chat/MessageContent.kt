package com.flipcash.services.models.chat

sealed interface MessageContent {
    data class Text(val text: String) : MessageContent
}
