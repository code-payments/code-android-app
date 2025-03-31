package xyz.flipchat.services.data

import com.getcode.model.ID
import com.getcode.model.Kin
import com.getcode.model.chat.ChatType
import com.getcode.utils.serializer.KinQuarksSerializer
import kotlinx.datetime.Instant
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
    val description: String?,
    val ownerId: ID,
    val roomNumber: Long,
    private val canDisablePush: Boolean,
    private val isPushEnabled: Boolean,
    private val unread: Int,
    private val moreUnread: Boolean,
    @Serializable(with = KinQuarksSerializer::class)
    val messagingFee: Kin,
    private val lastActive: Long?,
    val isOpen: Boolean,
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

    val hasMoreUnread: Boolean
        get() {
            return moreUnread
        }

    val canMute: Boolean
        get() = canDisablePush

    val isMuted: Boolean
        get() = !isPushEnabled

    val lastActivity: Instant?
        get() = lastActive?.let { Instant.fromEpochMilliseconds(it) }
}

