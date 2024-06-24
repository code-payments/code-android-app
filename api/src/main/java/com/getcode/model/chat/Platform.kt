package com.getcode.model.chat

import com.codeinc.gen.chat.v2.ChatService

enum class Platform {
    Unknown,
    Twitter;

    companion object {
        operator fun invoke(proto: ChatService.Platform): Platform {
            return runCatching { entries[proto.ordinal] }.getOrNull() ?: Unknown
        }
    }
}