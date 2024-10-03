package com.getcode.model.chat

import com.getcode.model.Cursor
import com.getcode.model.ID
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Chat domain model for On-Chain messaging. This serves as a reference to a collection of messages.
 *
 * @param id Unique chat identifier ([ID])
 * @param type The type of chat
 * @param title The chat title, which will be localized by server when applicable
 * @param members The members in this chat
 * For [ChatType.Notification], this list has exactly 1 item
 * For [ChatType.TwoWay], this list has exactly 2 items
 * @param canMute Can the user mute this chat?
 * @param canUnsubscribe Can the user unsubscribe from this chat?
 * @param cursor [Cursor] value for this chat for reference in subsequent GetChatsRequest
 * @param messages List of messages within this chat
 */
@Serializable
data class Chat(
    val id: ID,
    val type: ChatType,
    val title: Title?,
    val members: List<ChatMember> = emptyList(),
    private val _unreadCount: Int = 0,
    private val _isMuted: Boolean = false,
    val canMute: Boolean,
    private val _isSubscribed: Boolean = false,
    val canUnsubscribe: Boolean,
    val cursor: Cursor,
    val messages: List<ChatMessage>
) {
    val imageData: Any
        get() {
            return when (type) {
                ChatType.Unknown -> id
                ChatType.Notification -> id
                ChatType.TwoWay -> {
                    members
                        .filterNot { it.isSelf }
                        .firstNotNullOf {
                            if (it.identity != null) {
                                it.identity.imageUrl.orEmpty()
                            } else {
                                it.id
                            }
                        }
                }
            }
        }

    val unreadCount: Int
        get() {
            return _unreadCount
        }

    fun resetUnreadCount(): Chat {
        return copy(_unreadCount = 0)
    }

    val isMuted: Boolean
        get() {
            return _isMuted
        }

    fun setMuteState(muted: Boolean): Chat {
        return copy(_isMuted = muted)
    }

    val isSubscribed: Boolean
        get() {
            return _isSubscribed
        }

    fun setSubscriptionState(subscribed: Boolean): Chat {
        return copy(_isSubscribed = subscribed)
    }

    val newestMessage: ChatMessage?
        get() = messages.maxByOrNull { it.dateMillis }

    val lastMessageMillis: Long?
        get() = newestMessage?.dateMillis
}

val Chat.isV2: Boolean
    get() = members.isNotEmpty()

val Chat.isNotification: Boolean
    get() = type == ChatType.Notification

val Chat.isConversation: Boolean
    get() = type == ChatType.TwoWay

val Chat.self: ChatMember?
    get() = members.firstOrNull { it.isSelf }

val Chat.selfId: UUID?
    get() = self?.id
