package com.flipcash.app.messenger.internal

import androidx.paging.PagingData
import androidx.paging.insertSeparators
import com.flipcash.shared.chat.UnreadBoundary
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.SeparatorConfig

/** How long the unread divider stays once a chat is open. iOS carries the same switch. */
internal enum class UnreadDividerLifetime {
    /** The whole visit. */
    UntilClose,

    /** Until the viewer sends a message. */
    UntilSend,
}

internal val UNREAD_DIVIDER_LIFETIME = UnreadDividerLifetime.UntilClose

/**
 * Whether the unread divider goes in the gap between [newer] and [older]. [older] is `null` at the
 * end of pagination, when every stored message is unread.
 *
 * Compares ranges rather than matching an id, so a read-through message that was deleted or never
 * stored does not matter. The rows of one message share a `messageId`, so the divider never lands
 * inside a message.
 */
internal fun unreadDividerBetween(
    newer: ChatListItem.ContentBubble,
    older: ChatListItem.ContentBubble?,
    boundary: UnreadBoundary,
): Boolean {
    if (boundary !is UnreadBoundary.At || newer.isFromSelf) return false
    val olderId = older?.messageId ?: Long.MIN_VALUE
    return olderId <= boundary.readThrough && boundary.readThrough < newer.messageId
}

/** The one item that goes in the gap between [newer] and [older] in the newest-first list, if any. */
internal fun separatorBetween(
    newer: ChatListItem.ContentBubble?,
    older: ChatListItem.ContentBubble?,
    boundary: UnreadBoundary,
    config: SeparatorConfig,
): ChatListItem? {
    newer ?: return null
    if (boundary is UnreadBoundary.At && unreadDividerBetween(newer, older, boundary)) {
        // The oldest stored message has no separator of its own; the list draws its date as a
        // trailing header. The divider sits there instead, so it carries that date.
        val dayChanges = older == null || config.shouldSeparate(newer.timestamp, older.timestamp)
        return ChatListItem.UnreadDivider(boundary.count, date = newer.timestamp.takeIf { dayChanges })
    }
    older ?: return null
    return if (config.shouldSeparate(newer.timestamp, older.timestamp)) {
        ChatListItem.DateSeparator(newer.timestamp)
    } else null
}

internal fun PagingData<ChatListItem.ContentBubble>.withSeparators(
    boundary: UnreadBoundary,
    config: SeparatorConfig,
): PagingData<ChatListItem> =
    insertSeparators { newer: ChatListItem.ContentBubble?, older: ChatListItem.ContentBubble? ->
        separatorBetween(newer, older, boundary, config)
    }

/** The count the divider shows: past 99 it reads "99+". */
internal fun unreadCountLabel(count: Int): String = if (count > 99) "99+" else count.toString()
