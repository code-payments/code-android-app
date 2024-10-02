package com.getcode.model.chat

import com.codeinc.gen.chat.v2.ChatService

enum class ChatType {
    Unknown,
    Notification,
    TwoWay;

    companion object {
        operator fun invoke(proto: ChatService.ChatType): ChatType {
            return runCatching { entries[proto.ordinal] }.getOrNull() ?: Unknown
        }
    }
}