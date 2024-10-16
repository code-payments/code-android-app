package com.getcode.model.chat

import com.getcode.model.ID
import kotlinx.serialization.Serializable

/**
 * A user in a chat
 *
 * @param id Public AccountId (for is self...is derived via deposit address)
 * @param isSelf Is this chat member yourself? This enables client to identify which member_id
 * is themselves.
 * @param identity The chat member's identity if it has been revealed.
 * @param pointers  Chat message state for this member. This list will have DELIVERED and READ
 * pointers, if they exist. SENT pointers should be inferred by persistence
 * on server.
 */
@Serializable
data class ChatMember(
    val id: ID,
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
    companion object
}