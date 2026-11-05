package com.flipcash.shared.chat.models

import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.media.ChatMediaGroups
import com.flipcash.shared.chat.ui.mediaBody

/**
 * The card stack a photo row belongs to, resolved from the rows around it.
 *
 * A stack is several photo messages that [ChatMediaGroups] says were sent together. Each is still
 * its own row, so the stack is drawn by splitting the work between them: the oldest [Role.Front]
 * draws the cards, the [Role.Middle] ones draw nothing, and the newest [Role.Last] draws only its
 * caption and receipt. That keeps a long press, a reaction and a receipt on the message they
 * belong to, with no row reaching into another's.
 *
 * [members] is the whole set, oldest first, so `members.first()` is the front photo and
 * `members.last()` owns the caption.
 */
data class PhotoStack(val role: Role, val members: List<ChatListItem.ContentBubble>) {
    enum class Role { Front, Middle, Last }

    val front: ChatListItem.ContentBubble get() = members.first()
    val count: Int get() = members.size

    /**
     * The newest member the server has confirmed, which is the id the read pointer moves to when the
     * stack is on screen: the photos behind the front have no row of their own to report. Members
     * still sending have no server id yet, so they are skipped. A stack never holds a failed photo.
     */
    val newestConfirmedId: Long?
        get() = members.lastOrNull { it.receiptStatus != ReceiptStatus.SENDING }?.messageId
}

/**
 * What [ChatMediaGroups] reads of this row, or null when the row cannot be part of a stack: not a
 * photo, failed, a tombstone, or from a sender we cannot tell from anyone else.
 *
 * A row still sending joins with a placeholder id, which the rule leaves out of its id check, so a
 * send draws as its stack from the moment it is sent. A confirmed row keeps its
 * [pendingClientIdHex] as its list key, so the receipt says whether it is confirmed, not that id.
 */
internal fun ChatListItem.ContentBubble.stackCandidate(): ChatMediaGroups.Candidate? {
    if (receiptStatus.isFailed() || part != null) return null
    val body = content.mediaBody() ?: return null
    // Every outgoing message is the viewer's. An incoming one the backend did not attribute could
    // be anyone, so it never joins.
    val sender: Any = if (isFromSelf) SELF_SENDER else authorId ?: return null
    return ChatMediaGroups.Candidate(
        messageId = messageId,
        senderKey = sender,
        isPhoto = true,
        timestamp = timestamp,
        hasCaption = !body.caption?.text.isNullOrEmpty(),
        isReply = content is MessageContent.Reply,
        confirmed = receiptStatus != ReceiptStatus.SENDING,
    )
}

private object SELF_SENDER

private fun ReceiptStatus?.isFailed(): Boolean =
    this == ReceiptStatus.FAILED || this == ReceiptStatus.DENIED

/**
 * The stack the row [item] at [index] is in, or null when it stands alone.
 *
 * Rows are newest first, as the reversed transcript holds them: [peek] of `index + 1` is older and
 * `index - 1` is newer, and [itemCount] bounds the walk because `peek` throws off the end. The walk
 * is bounded by the set itself, so it costs a few peeks per photo row.
 */
fun photoStackOf(
    index: Int,
    item: ChatListItem,
    itemCount: Int,
    peek: (Int) -> ChatListItem?,
): PhotoStack? {
    val self = item as? ChatListItem.ContentBubble ?: return null
    var candidate = self.stackCandidate() ?: return null

    val older = ArrayList<ChatListItem.ContentBubble>()
    var i = index + 1
    while (i < itemCount) {
        val row = peek(i) as? ChatListItem.ContentBubble ?: break
        val above = row.stackCandidate() ?: break
        if (!ChatMediaGroups.joins(older = above, newer = candidate)) break
        older += row
        candidate = above
        i++
    }

    candidate = self.stackCandidate() ?: return null
    val newer = ArrayList<ChatListItem.ContentBubble>()
    i = index - 1
    while (i >= 0) {
        val row = peek(i) as? ChatListItem.ContentBubble ?: break
        val below = row.stackCandidate() ?: break
        if (!ChatMediaGroups.joins(older = candidate, newer = below)) break
        newer += row
        candidate = below
        i--
    }

    if (older.isEmpty() && newer.isEmpty()) return null
    val members = older.asReversed() + self + newer
    val role = when {
        older.isEmpty() -> PhotoStack.Role.Front
        newer.isEmpty() -> PhotoStack.Role.Last
        else -> PhotoStack.Role.Middle
    }
    return PhotoStack(role, members)
}
