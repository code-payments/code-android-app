package com.getcode.model.notifications

import com.getcode.model.MessageContent

data class CodeNotification(
    val type: NotificationType,
    val title: String,
    val body: MessageContent,
)

enum class NotificationType {
    ChatMessage,
    Unknown;

    companion object {
        fun tryValueOf(value: String): NotificationType? {
            return runCatching { valueOf(value) }.getOrNull()
        }
    }
}