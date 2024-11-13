package xyz.flipchat.services.data

import com.getcode.model.ID
import com.getcode.model.chat.ChatType
import kotlinx.serialization.Serializable

@Serializable
data class RoomWithMemberCount(
    val room: Room,
    val members: Int
)

data class RoomWithMembers(
    val room: Room,
    val members: List<Member>
)

@Serializable
data class Room(
    val id: ID,
    val type: ChatType,
    private val _title: String?,
    val ownerId: ID,
    val roomNumber: Long,
    private val muted: Boolean,
    val muteable: Boolean,
    private val unread: Int,
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
            return unread
        }

    val isMuted: Boolean
        get() {
            return muted
        }
}

