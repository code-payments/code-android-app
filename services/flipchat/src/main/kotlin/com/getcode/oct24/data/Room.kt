package com.getcode.oct24.data

import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.ChatType
import kotlinx.serialization.Serializable

@Serializable
data class RoomWithMembers(
    val room: Room,
    val members: List<Member>
)

@Serializable
data class Room(
    val id: ID,
    val type: ChatType,
    private val _title: String?,
    val roomNumber: Long,
    private val _muted: Boolean,
    val muteable: Boolean,
    private val _unread: Int,
    val messages: List<ChatMessage> = emptyList(),
) {
    val title: String?
        get() {
            val providedTitle = _title
            if (providedTitle != null) {
                return providedTitle
            }
            return null
        }

    val imageData: String?
        get() {
            // TODO:
            return null
        }

    val unreadCount: Int
        get() {
            return _unread
        }

    fun resetUnreadCount(): Room {
        return copy(_unread = 0)
    }

    val isMuted: Boolean
        get() {
            return _muted
        }

    fun setMuteState(muted: Boolean): Room {
        return copy(_muted = muted)
    }

    val newestMessage: ChatMessage?
        get() = messages.maxByOrNull { it.dateMillis }

    val lastMessageMillis: Long?
        get() = newestMessage?.dateMillis
}

