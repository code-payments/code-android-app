package xyz.flipchat.services.domain.mapper

import xyz.flipchat.services.data.Room
import xyz.flipchat.services.domain.model.chat.Conversation
import xyz.flipchat.services.extensions.titleOrFallback
import com.getcode.services.mapper.Mapper
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.base58
import javax.inject.Inject

class RoomConversationMapper @Inject constructor(
    private val resources: ResourceHelper,
) : Mapper<Room, Conversation> {
    override fun map(from: Room): Conversation {
        return Conversation(
            idBase58 = from.id.base58,
            ownerIdBase58 = from.ownerId.base58,
            title = from.titleOrFallback(resources),
            imageUri = from.imageData,
            unreadCount = from.unreadCount,
            hasMoreUnread = from.hasMoreUnread,
            isMuted = from.isMuted,
            canMute = from.canMute,
            roomNumber = from.roomNumber,
            coverChargeQuarks = from.coverCharge.quarks,
            lastActivity = from.lastActivity?.toEpochMilliseconds(),
            isOpen = from.isOpen
        )
    }
}