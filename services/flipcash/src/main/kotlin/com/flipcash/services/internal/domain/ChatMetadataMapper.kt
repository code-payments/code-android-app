package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel
import com.flipcash.services.internal.domain.mapper.Mapper
import com.flipcash.services.internal.network.extensions.toChatId
import com.flipcash.services.internal.network.extensions.toChatMessage
import com.flipcash.services.internal.network.extensions.toChatRules
import com.flipcash.services.internal.network.extensions.toChatType
import com.flipcash.services.internal.network.extensions.toId
import com.flipcash.services.internal.network.extensions.toMediaItem
import com.flipcash.services.internal.network.extensions.toPointer
import com.flipcash.services.internal.network.extensions.toRosterSummary
import com.flipcash.services.internal.network.extensions.toViewerState
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
                    // The server sets the id on the member and usually not again inside the nested
                    // profile, and this profile is by definition that member's. Leaving it null
                    // costs callers the id that authorizes re-minting the profile picture's
                    // download URL, so the avatar can never recover once the stored URL expires.
                    userProfile = userProfileMapper.map(member.userProfile)
                        .let { if (it.userId == null) it.copy(userId = member.userId.toId()) else it },
                    pointers = member.pointersList.map { it.toPointer() },
                    joinedAt = if (member.hasJoinedAt()) {
                        Instant.fromEpochSeconds(member.joinedAt.seconds, member.joinedAt.nanos)
                    } else {
                        null
                    },
                    version = member.version,
                )
            },
            lastMessage = if (from.hasLastMessage()) from.lastMessage.toChatMessage() else null,
            lastActivity = Instant.fromEpochSeconds(from.lastActivity.seconds, from.lastActivity.nanos),
            latestEventSequence = from.latestEventSequence,
            isHidden = from.isHidden,
            title = from.title.takeIf { it.isNotEmpty() },
            picture = if (from.hasPicture()) from.picture.toMediaItem() else null,
            rosterSummary = from.rosterSummary.toRosterSummary(),
            rules = if (from.hasRules()) from.rules.toChatRules() else null,
            viewerState = if (from.hasViewerState()) from.viewerState.toViewerState() else null,
            creator = if (from.hasCreator()) from.creator.toId() else null,
            // Transitional flag (see chat/v1 model.proto doc); ignored behaviourally for now.
            useE2ee = from.useE2Ee,
        )
    }
}
