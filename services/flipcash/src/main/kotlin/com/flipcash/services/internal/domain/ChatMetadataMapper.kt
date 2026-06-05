package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel
import com.flipcash.services.internal.domain.mapper.Mapper
import com.flipcash.services.internal.network.extensions.toChatId
import com.flipcash.services.internal.network.extensions.toChatMessage
import com.flipcash.services.internal.network.extensions.toChatType
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.internal.network.extensions.toPointer
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMetadata
import kotlin.time.Instant
import javax.inject.Inject

class ChatMetadataMapper @Inject constructor(
    private val userProfileMapper: UserProfileMapper,
) : Mapper<ChatModel.Metadata, ChatMetadata> {
    override fun map(from: ChatModel.Metadata): ChatMetadata {
        return ChatMetadata(
            chatId = from.chatId.toChatId(),
            type = from.type.toChatType(),
            members = from.membersList.map { member ->
                ChatMember(
                    userId = member.userId.toId(),
                    userProfile = userProfileMapper.map(member.userProfile),
                    pointers = member.pointersList.map { it.toPointer() },
                )
            },
            lastMessage = if (from.hasLastMessage()) from.lastMessage.toChatMessage() else null,
            lastActivity = Instant.fromEpochSeconds(from.lastActivity.seconds, from.lastActivity.nanos),
        )
    }
}
