package com.getcode.oct24.data

import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.ChatType
import kotlinx.serialization.Serializable

@Serializable
data class Room(
    val id: ID,
    val type: ChatType,
    private val _title: String?,
    val roomNumber: Long = -1,
    val members: List<Member>,
    private val _muted: Boolean,
    val muteable: Boolean,
    private val _unread: Int,
    val messages: List<ChatMessage> = emptyList(),
) {
    val title: String
        get() {
            val providedTitle = _title
            if (providedTitle != null) {
                return providedTitle
            }

            val nonSelf = members.filterNot { it.isSelf }
            return nonSelf.mapNotNull { it.identity?.displayName }
                .joinToString()
        }
    val imageData: String?
        get() {
            val nonSelf = members.filterNot { it.isSelf }
            if (nonSelf.count() == 1) {
                return nonSelf.first().identity?.imageUrl
            }

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

val Room.self: Member?
    get() = members.firstOrNull { it.isSelf }

val Room.selfId: ID?
    get() = self?.id

