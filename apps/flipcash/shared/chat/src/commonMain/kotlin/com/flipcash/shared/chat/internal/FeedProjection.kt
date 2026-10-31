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

/** The signed-in user's READ pointer in [metadata], or null when there is none. */
internal fun selfReadPointer(metadata: ChatMetadata, selfId: ID?): Long? =
    metadata.members
        .firstOrNull { it.userId == selfId }
        ?.pointers
        ?.firstOrNull { it.type == PointerType.READ }
        ?.value

/**
 * How many messages past the signed-in user's READ pointer the chat holds: 0 when it's read, null
 * when it's unread but the number can't be known.
 *
 * The server stamps each message with a running count of unread-eligible messages (`unreadSeq`),
 * so the count is the newest message's stamp less the stamp on the message the pointer names,
 * looked up through [unreadSeqAt]. With no pointer nothing has been read and the newest stamp is
 * the count. A pointer whose message isn't stored (after a database rebuild, say) leaves the count
 * unknown, as does a newest stamp at or below the pointer's: then the newest message isn't
 * unread-eligible, and a guess would be wrong either way.
 *
 * The chat is read when the newest visible message is the user's own or isn't past the pointer. A
 * group's roster is paged: the members the device holds are a slice, and the signed-in user's own
 * row may not be in it. Falling back to no pointer there would mark every group unread until the
 * slice caught up, so an absent self row means "not known yet" for a group. On a DM both members
 * always arrive together, so absence means nothing has been read.
 */
internal fun unreadCount(
    metadata: ChatMetadata,
    selfId: ID?,
    unreadSeqAt: (messageId: Long) -> Long?,
): Int? {
    if (metadata.type == ChatType.GROUP && metadata.members.none { it.userId == selfId }) return 0

    val lastMessage = metadata.lastMessage ?: return 0
    if (lastMessage.senderId == selfId) return 0
    val readPointer = selfReadPointer(metadata, selfId)
    if (lastMessage.messageId <= (readPointer ?: 0L)) return 0

    val readStamp = if (readPointer == null) 0L else unreadSeqAt(readPointer) ?: return null
    return (lastMessage.unreadSeq - readStamp).takeIf { it > 0 }?.toInt()
}
