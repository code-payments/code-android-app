package xyz.flipchat.notifications

import com.getcode.model.ID
import com.getcode.model.chat.MessageContent
import com.getcode.utils.base58
import com.getcode.utils.decodeBase64

data class FcNotification(
    val type: FcNotificationType,
    val title: String,
    val body: MessageContent,
)

private enum class TypeValue {
    Unknown, ChatMessage
}

sealed interface FcNotificationType {
    val ordinal: Int
    val name: String

    data object Unknown: FcNotificationType {
        override val ordinal: Int = 99
        override val name: String = "Misc"
    }

    data class ChatMessage(val id: ID?): FcNotificationType {
        override val ordinal: Int = 1
        override val name: String = "Chat Messages"
    }

    fun isNotifiable() = true

    companion object {
        private const val TYPE = "type"
        private const val CHAT_ID = "chat_id"
        fun resolve(value: MutableMap<String, String>): FcNotificationType {
            val type = value[TYPE]
            var notificationType = runCatching { TypeValue.valueOf(type.orEmpty()) }.getOrNull()
            if (notificationType == null) {
                // fallback to chat
                notificationType = TypeValue.ChatMessage
            }

            return when (notificationType) {
                TypeValue.ChatMessage -> {
                    val chatId = value[CHAT_ID]?.decodeBase64()?.toList()
                    ChatMessage(chatId)
                }
                else -> Unknown
            }
        }
    }
}