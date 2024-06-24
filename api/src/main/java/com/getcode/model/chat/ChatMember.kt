package com.getcode.model.chat

import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.ID

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
data class ChatMember(
    val id: ID,
    val isSelf: Boolean,
    val identity: Identity?,
    val pointers: List<Pointer>,
    val numUnread: Int,
    val isMuted: Boolean,
    val isSubscribed: Boolean,
)

/**
 * Identity to an external social platform that can be linked to a Code account
 *
 * @param platform The external social platform linked to this chat member
 * @param username The chat member's username on the external social platform
 */
data class Identity(
    val platform: Platform,
    val username: String,
) {
    companion object {
        operator fun invoke(proto: ChatService.ChatMemberIdentity): Identity {
            return Identity(
                platform = Platform(proto.platform),
                username = proto.username
            )
        }
    }
}