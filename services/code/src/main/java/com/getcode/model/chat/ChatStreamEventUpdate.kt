package com.getcode.model.chat

import com.getcode.model.PointerStatus

data class ChatStreamEventUpdate(
    val messages: List<ChatMessage>,
    val pointers: List<PointerStatus>,
    val isTyping: Boolean,
)