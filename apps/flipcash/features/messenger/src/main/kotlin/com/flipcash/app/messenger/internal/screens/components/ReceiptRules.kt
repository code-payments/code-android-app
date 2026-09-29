package com.flipcash.app.messenger.internal.screens.components

import androidx.paging.compose.LazyPagingItems
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.ReceiptStatus

/**
 * A tombstone anchors nothing. The receipt describes the delivery of a message that is no longer
 * there, so leaving it attached would caption a deleted bubble with "Read".
 */
private val ChatListItem.ContentBubble.carriesReceipt: Boolean
    get() = isFromSelf && content !is MessageContent.Deleted

internal fun effectiveReceiptStatus(
    bubble: ChatListItem.ContentBubble,
    otherReadPointer: MessagePointer?,
): ReceiptStatus? {
    if (!bubble.carriesReceipt) return null
    val base = bubble.receiptStatus ?: return null
    val pointerValue = otherReadPointer?.value ?: 0L
    if (base == ReceiptStatus.SENT && bubble.messageId in 1..pointerValue) {
        return ReceiptStatus.READ
    }
    return base
}

/**
 * Show a receipt label below a self-message only within the last 2 contiguous
 * self-message groups. In reverseLayout, index 0 is the newest message.
 *
 * Within a group, labels appear at status boundaries (SENT↔READ).
 * At group boundaries, labels are suppressed when the nearest self-group
 * below already shows the same status (avoids duplicate "Read" labels).
 */
/**
 * The nearest bubble below [index], stepping over the viewer's own tombstones and the send that is
 * still settling ([settlingKey]): neither has taken the receipt yet, so the line stays above them.
 */
private fun receiptNeighbourBelow(
    index: Int,
    peek: (Int) -> ChatListItem?,
    settlingKey: Any?,
): ChatListItem.ContentBubble? {
    for (i in (index - 1) downTo 0) {
        val bubble = peek(i) as? ChatListItem.ContentBubble ?: return null
        if (bubble.isFromSelf && bubble.content is MessageContent.Deleted) continue
        if (bubble.isSettling(settlingKey)) continue
        return bubble
    }
    return null
}

private fun ChatListItem.ContentBubble.isSettling(settlingKey: Any?): Boolean =
    settlingKey != null && messageKey == settlingKey

internal fun shouldShowReceiptLabel(
    index: Int,
    item: ChatListItem.ContentBubble,
    messages: LazyPagingItems<ChatListItem>,
    otherReadPointer: MessagePointer?,
    settlingKey: Any? = null,
): Boolean = shouldShowReceiptLabel(index, item, messages::peek, otherReadPointer, settlingKey)

/**
 * [settlingKey] is the viewer's newest send while it settles: from the moment it is sent until it
 * is confirmed and the settle floor has passed. It shows no line of its own in that time, and the
 * line above it stays put, so the receipt moves down in one step when the hold releases rather
 * than dropping at confirmation and reappearing a beat later.
 */
internal fun shouldShowReceiptLabel(
    index: Int,
    item: ChatListItem.ContentBubble,
    peek: (Int) -> ChatListItem?,
    otherReadPointer: MessagePointer?,
    settlingKey: Any? = null,
): Boolean {
    if (!item.carriesReceipt) return false
    // A split message's receipt goes under its last row only. The rows above share its status, so
    // the rules below would already hide them -- except a failure, which shows everywhere it can.
    if (!item.isLastRow) return false
    val status = effectiveReceiptStatus(item, otherReadPointer) ?: return false
    if (status == ReceiptStatus.FAILED) return true
    if (item.isSettling(settlingKey)) return false
    if (status != ReceiptStatus.SENT && status != ReceiptStatus.READ) return false

    // index - 1 is the item below (newer) in reverseLayout. A tombstone still belongs to the self
    // group for bubble shaping, so it is stepped over here rather than treated as a group boundary.
    val belowBubble = receiptNeighbourBelow(index, peek, settlingKey)

    // Within a self-group: show at intra-group status boundaries only
    if (belowBubble != null && belowBubble.isFromSelf) {
        return effectiveReceiptStatus(belowBubble, otherReadPointer) != status
    }

    // At a group boundary — count which self-group this is.
    var selfGroups = 0
    var prevWasSelf = false
    for (i in 0 until index) {
        val bubble = (peek(i) ?: break) as? ChatListItem.ContentBubble
        val isSelf = bubble != null && bubble.isFromSelf
        if (isSelf && !prevWasSelf) selfGroups++
        prevWasSelf = isSelf
    }
    if (!prevWasSelf) selfGroups++ // current item starts a new group
    if (selfGroups > 2) return false

    // Bottommost self-group always shows its label
    if (selfGroups == 1) return true

    // For group 2: show only if status differs from the nearest self-group below
    for (i in (index - 1) downTo 0) {
        val bubble = (peek(i) ?: break) as? ChatListItem.ContentBubble ?: continue
        if (bubble.isSettling(settlingKey)) continue
        if (bubble.carriesReceipt) return effectiveReceiptStatus(bubble, otherReadPointer) != status
    }
    return true
}
