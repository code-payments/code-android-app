package com.getcode.model.chat

import com.codeinc.gen.chat.v2.ChatService

enum class Platform {
    Unknown,
    Twitter;

    companion object {
        fun named(name: String): Platform {
            val normalizedName = name.lowercase()
            return entries.firstOrNull {
                it.name.lowercase() == normalizedName ||
                        (normalizedName == "x" && it.name.lowercase() == "twitter")
            } ?: Unknown
        }
    }
}