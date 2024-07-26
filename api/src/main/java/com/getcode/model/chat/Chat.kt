package com.getcode.model.chat

import com.getcode.model.Cursor
import com.getcode.model.ID
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
            if (!isV2) return _unreadCount

            val self = members.firstOrNull { it.isSelf } ?: return 0
            return self.numUnread
        }

    fun resetUnreadCount(): Chat {
        if (!isV2) {
            return copy(_unreadCount = 0)
        }

        val self = members.firstOrNull { it.isSelf } ?: return this
        val updatedSelf = self.copy(numUnread = 0)
        val updatedMembers = members.map {
            if (it.id == self.id) {
                updatedSelf
            } else {
                it
            }
        }
        return copy(members = updatedMembers)
    }

    val isMuted: Boolean
        get() {
            if (!isV2) return _isMuted

            val self = members.firstOrNull { it.isSelf } ?: return false
            return self.isMuted
        }

    fun setMuteState(muted: Boolean): Chat {
        if (!isV2) {
            return copy(_isMuted = muted)
        }

        val self = members.firstOrNull { it.isSelf } ?: return this
        val updatedSelf = self.copy(isMuted = muted)
        val updatedMembers = members.map {
            if (it.id == self.id) {
                updatedSelf
            } else {
                it
            }
        }
        return copy(members = updatedMembers)
    }

    val isSubscribed: Boolean
        get() {
            if (!isV2) return _isSubscribed

            val self = members.firstOrNull { it.isSelf } ?: return false
            return self.isSubscribed
        }

    fun setSubscriptionState(subscribed: Boolean): Chat {
        if (!isV2) {
            return copy(_isSubscribed = subscribed)
        }

        val self = members.firstOrNull { it.isSelf } ?: return this
        val updatedSelf = self.copy(isSubscribed = subscribed)
        val updatedMembers = members.map {
            if (it.id == self.id) {
                updatedSelf
            } else {
                it
            }
        }
        return copy(members = updatedMembers)
    }

    val newestMessage: ChatMessage?
        get() = messages.maxByOrNull { it.dateMillis }

    val lastMessageMillis: Long?
        get() = newestMessage?.dateMillis
}

val Chat.isV2: Boolean
    get() = members.isNotEmpty()

val Chat.isConversation: Boolean
    get() = type == ChatType.TwoWay

val Chat.self: ChatMember?
    get() = members.firstOrNull { it.isSelf }

val Chat.selfId: UUID?
    get() = self?.id
