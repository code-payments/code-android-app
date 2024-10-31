package com.getcode.oct24.domain.mapper

import com.getcode.oct24.data.Room
import com.getcode.oct24.domain.model.chat.Conversation
import com.getcode.oct24.extensions.titleOrFallback
import com.getcode.oct24.services.R
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
            title = from.titleOrFallback(resources),
            imageUri = from.imageData,
            unreadCount = from.unreadCount,
            isMuted = from.isMuted,
            lastActivity = null, // TODO: ?
        )
    }
}