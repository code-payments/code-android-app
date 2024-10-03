package com.getcode.model.chat

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.ID
import com.getcode.utils.serializer.UUIDSerializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A user in a chat
 *
 * @param id Globally unique ID for this chat member
 * @param isSelf Is this chat member yourself? This enables client to identify which member_id
 * is themselves.
 * @param identity The chat member's identity if it has been revealed.
 * @param pointers  Chat message state for this member. This list will have DELIVERED and READ
 * pointers, if they exist. SENT pointers should be inferred by persistence
 * on server.
 * @param numUnread Estimated number of unread messages for the chat member in this chat
 * Only valid when `isSelf = true`
 * @param isMuted Has the chat member muted this chat?
 * Only valid when `isSelf = true`
 * @param isSubscribed Is the chat member subscribed to this chat?
 * Only valid when `isSelf = true`
 */
@Serializable
data class ChatMember(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val isSelf: Boolean,
    val identity: Identity?,
    val pointers: List<Pointer>,
)

/**
 * Identity to an external social platform that can be linked to a Code account
 *
 * @param platform The external social platform linked to this chat member
 * @param username The chat member's username on the external social platform
 */
@Serializable
data class Identity(
    val platform: Platform,
    val username: String,
    val imageUrl: String?
) {
    companion object {
        operator fun invoke(proto: ChatService.MemberIdentity): Identity? {
            val platform = Platform(proto.platform).takeIf { it != Platform.Unknown } ?: return null
            return Identity(
                platform = platform,
                username = proto.username,
                imageUrl = null,
            )
        }
    }
}