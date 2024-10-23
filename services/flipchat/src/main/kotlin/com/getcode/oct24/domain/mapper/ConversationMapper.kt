package com.getcode.oct24.domain.mapper

import com.getcode.oct24.data.Room
import com.getcode.oct24.domain.model.chat.Conversation
import com.getcode.services.mapper.Mapper
import com.getcode.utils.base58
import javax.inject.Inject

class ConversationMapper @Inject constructor(
) : Mapper<Room, Conversation> {
    override fun map(from: Room): Conversation {
        return Conversation(
            idBase58 = from.id.base58,
            title = from.title,
            imageUri = from.imageData,
            members = from.members,
            unreadCount = from.unreadCount,
            isMuted = from.isMuted,
            lastActivity = null, // TODO: ?
        )
    }
}