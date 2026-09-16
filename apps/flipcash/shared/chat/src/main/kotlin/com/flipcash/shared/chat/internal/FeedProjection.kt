package com.flipcash.shared.chat.internal

import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.isDmAddressable
import com.getcode.opencode.model.core.ID

/**
 * The two rules a conversation row has to pass to be shown, kept apart from the delegate so the
 * list and its paged form apply exactly the same ones.
 */

private fun isSelf(member: ChatMember, selfId: ID?, selfPhone: String?): Boolean =
    member.userId == selfId ||
        (selfPhone != null && member.userProfile.verifiedPhoneNumber == selfPhone)

/**
 * Whether [metadata] belongs in the conversation list.
 *
 * A DM is its counterparty — with no addressable other member there is no name to draw, so it is
 * dropped. A group carries its own title and picture and is renderable on its own, however much of
 * its roster the device happens to hold.
 */
internal fun isRenderable(metadata: ChatMetadata, selfId: ID?, selfPhone: String?): Boolean {
    // Hidden chats (e.g. a DM the user blocked) must not surface in the feed.
    if (metadata.isHidden) return false
    if (metadata.type == ChatType.GROUP) return true

    val otherMember = metadata.members.firstOrNull { !isSelf(it, selfId, selfPhone) } ?: return false
    // Contact DMs require a resolvable identity (phone / display name). Tip DMs are identified by
    // user id and have no phone by design, so they are never dropped.
    return isDmAddressable(metadata.type, otherMember.userProfile)
}

/**
 * 1 when the newest visible message is past the signed-in user's READ pointer and someone else
 * sent it, 0 otherwise. The feed shows a splat rather than a number, so counting past one buys
 * nothing.
 *
 * A group's roster is paged: the members the device holds are a slice, and the signed-in user's
 * own row may not be in it. Falling back to a pointer of zero there would splat every group until
 * the slice caught up, so an absent self row means "not known yet" for a group. On a DM both
 * members always arrive together, so absence keeps its old meaning and the count is unchanged.
 */
internal fun unreadCount(metadata: ChatMetadata, selfId: ID?): Int {
    val self = metadata.members.firstOrNull { it.userId == selfId }
    if (self == null && metadata.type == ChatType.GROUP) return 0

    val readPointer = self?.pointers?.firstOrNull { it.type == PointerType.READ }?.value ?: 0L
    val lastMessage = metadata.lastMessage ?: return 0
    return if (lastMessage.messageId > readPointer && lastMessage.senderId != selfId) 1 else 0
}
