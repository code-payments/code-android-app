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

private fun ChatListItem.ContentBubble.isSettling(settlingKey: Any?): Boolean =
    settlingKey != null && messageKey == settlingKey

/** A confirmed send of the viewer's that can hold a line: not a tombstone, not settling. */
private fun ChatListItem.confirmedStatus(
    otherReadPointer: MessagePointer?,
    settlingKey: Any?,
): ReceiptStatus? {
    val bubble = this as? ChatListItem.ContentBubble ?: return null
    if (bubble.isSettling(settlingKey)) return null
    return effectiveReceiptStatus(bubble, otherReadPointer)
        ?.takeIf { it == ReceiptStatus.SENT || it == ReceiptStatus.READ }
}

internal fun shouldShowReceiptLabel(
    index: Int,
    item: ChatListItem.ContentBubble,
    messages: LazyPagingItems<ChatListItem>,
    otherReadPointer: MessagePointer?,
    settlingKey: Any? = null,
): Boolean = shouldShowReceiptLabel(index, item, messages::peek, otherReadPointer, settlingKey)

/**
 * Whether [item] carries a status line, following iOS: the viewer's newest confirmed send shows
 * its status, and the newest one the counterpart has read shows "Read" even when a newer one is
 * only delivered, so the two lines sit under their own bubbles. A failed send always shows its own.
 *
 * [settlingKey] is the viewer's newest send while it settles: from the moment it is sent until it
 * is confirmed and the settle floor has passed. It takes no line in that time, and the line above
 * it stays put, so the receipt moves down in one step when the hold releases rather than dropping
 * at confirmation and reappearing a beat later.
 *
 * Rows are newest first, as the reversed transcript holds them: [peek] of `index - 1` is newer.
 */
internal fun shouldShowReceiptLabel(
    index: Int,
    item: ChatListItem.ContentBubble,
    peek: (Int) -> ChatListItem?,
    otherReadPointer: MessagePointer?,
    settlingKey: Any? = null,
): Boolean {
    if (!item.carriesReceipt) return false
    // A split message's receipt goes under its last row only; the rows above share its status.
    if (!item.isLastRow) return false
    val status = effectiveReceiptStatus(item, otherReadPointer) ?: return false
    if (status == ReceiptStatus.FAILED) return true
    val confirmed = item.confirmedStatus(otherReadPointer, settlingKey) ?: return false

    // Walking toward the newest: it keeps its line while nothing newer takes it -- no newer send
    // at all, or, for a read one, only newer sends that are delivered.
    for (i in index - 1 downTo 0) {
        val newer = peek(i)?.confirmedStatus(otherReadPointer, settlingKey) ?: continue
        if (newer == ReceiptStatus.READ || confirmed != ReceiptStatus.READ) return false
    }
    return true
}
