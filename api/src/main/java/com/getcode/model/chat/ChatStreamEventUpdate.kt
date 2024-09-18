package com.getcode.model.chat

import com.getcode.mapper.PointerStatus

data class ChatStreamEventUpdate(
    val messages: List<ChatMessage>,
    val pointers: List<PointerStatus>,
    val isTyping: Boolean,
)