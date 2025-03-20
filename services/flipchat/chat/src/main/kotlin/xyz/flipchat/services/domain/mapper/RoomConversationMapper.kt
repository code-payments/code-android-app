package xyz.flipchat.services.domain.mapper

import xyz.flipchat.services.data.Room
import xyz.flipchat.services.domain.model.chat.Conversation
import com.getcode.services.mapper.Mapper
import com.getcode.utils.base58
import javax.inject.Inject

class RoomConversationMapper @Inject constructor() : Mapper<Room, Conversation> {
    override fun map(from: Room): Conversation {
        return Conversation(
            idBase58 = from.id.base58,
            ownerIdBase58 = from.ownerId.base58,
            title = from.title.orEmpty(),
            description = from.description,
            imageUri = from.imageData,
            unreadCount = from.unreadCount,
            hasMoreUnread = from.hasMoreUnread,
            isMuted = from.isMuted,
            canMute = from.canMute,
            roomNumber = from.roomNumber,
            messagingFee = from.messagingFee.quarks,
            lastActivity = from.lastActivity?.toEpochMilliseconds(),
            isOpen = from.isOpen
        )
    }
}