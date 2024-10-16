package com.getcode.model.chat

enum class MessageStatus {
    Sent, Delivered, Read, Unknown;

    fun isOutgoing() = when (this) {
        Sent,
        Delivered -> true

        else -> false
    }

    fun isValid() = this != Unknown
}