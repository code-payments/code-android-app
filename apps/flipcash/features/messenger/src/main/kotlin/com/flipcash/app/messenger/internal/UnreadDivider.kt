package com.flipcash.app.messenger.internal

import androidx.paging.PagingData
import androidx.paging.flatMap
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
 *
 * An [older] row without a server id is a pending or failed send, stored as `-(now)` and sorted
 * among the newest rows, so its id says nothing about the boundary. The gap above it never takes
 * the divider; otherwise a transcript would draw a second one under the same fixed key.
 */
internal fun unreadDividerBetween(
    newer: ChatListItem.TranscriptMessage,
    older: ChatListItem.TranscriptMessage?,
    boundary: UnreadBoundary,
): Boolean {
    if (boundary !is UnreadBoundary.At || newer.isFromSelf) return false
    // A notice is treated as already read: it is never counted, so it never opens the unread run.
    if (newer is ChatListItem.SystemMessage) return false
    if (older != null && older.messageId < 1) return false
    val olderId = older?.messageId ?: Long.MIN_VALUE
    return olderId <= boundary.readThrough && boundary.readThrough < newer.messageId
}

/**
 * The one item that goes in the gap between [newer] and [older] in the newest-first list, if any.
 *
 * [oldestEncryptedId] is the oldest stored message that arrived end-to-end encrypted. The gap below
 * its last row takes the Encrypted marker, carrying whatever separator the gap would have had.
 */
internal fun separatorBetween(
    newer: ChatListItem.TranscriptMessage?,
    older: ChatListItem.TranscriptMessage?,
    boundary: UnreadBoundary,
    config: SeparatorConfig,
    oldestEncryptedId: Long? = null,
): ChatListItem? {
    newer ?: return null
    val separator = plainSeparatorBetween(newer, older, boundary, config)
    val marksEncryption = newer.messageId == oldestEncryptedId && older?.messageId != newer.messageId
    if (!marksEncryption) return separator
    // At the head the list draws the oldest date as a trailing header unless the oldest item
    // already carries one, and the marker is now the oldest item.
    val above = separator ?: if (older == null) ChatListItem.DateSeparator(newer.timestamp) else null
    return ChatListItem.EncryptedMarker(above)
}

private fun plainSeparatorBetween(
    newer: ChatListItem.TranscriptMessage,
    older: ChatListItem.TranscriptMessage?,
    boundary: UnreadBoundary,
    config: SeparatorConfig,
): ChatListItem? {
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

/**
 * [separatorBetween] over the transcript once request notices are in it. A notice is never unread
 * and never the oldest encrypted message, so it only takes a date header, by its own time. The gap
 * under a run of notices is where the divider or marker for the message above them goes: they are
 * decided for that message, as if the notices were not there.
 */
internal fun separatorBetweenItems(
    newer: ChatListItem?,
    older: ChatListItem?,
    boundary: UnreadBoundary,
    config: SeparatorConfig,
    oldestEncryptedId: Long? = null,
): ChatListItem? = when (newer) {
    null -> null
    is ChatListItem.TranscriptMessage -> when (older) {
        is ChatListItem.RequestNotice -> dateBetween(newer.timestamp, older.timestamp, config)
        else -> separatorBetween(newer, older as? ChatListItem.TranscriptMessage, boundary, config, oldestEncryptedId)
    }
    is ChatListItem.RequestNotice -> {
        val olderMessage = older as? ChatListItem.TranscriptMessage
        val olderTime = olderMessage?.timestamp ?: (older as? ChatListItem.RequestNotice)?.timestamp
        val date = olderTime?.let { dateBetween(newer.timestamp, it, config) }
        val above = newer.above
        if (olderMessage == null || above == null) {
            date
        } else {
            // Decided for the message above the notices. Every branch replaces the date it comes
            // back with: the row above this gap is the notice, so the date is the notice's own.
            when (val separator = separatorBetween(above, olderMessage, boundary, config, oldestEncryptedId)) {
                is ChatListItem.UnreadDivider -> separator.copy(date = date?.timestamp)
                is ChatListItem.EncryptedMarker -> when (val inner = separator.above) {
                    is ChatListItem.UnreadDivider -> separator.copy(above = inner.copy(date = date?.timestamp))
                    else -> separator.copy(above = date)
                }
                else -> date
            }
        }
    }
    else -> null
}

private fun dateBetween(newer: kotlin.time.Instant, older: kotlin.time.Instant, config: SeparatorConfig) =
    if (config.shouldSeparate(newer, older)) ChatListItem.DateSeparator(newer) else null

internal fun PagingData<ChatListItem.TranscriptMessage>.withSeparators(
    boundary: UnreadBoundary,
    config: SeparatorConfig,
    oldestEncryptedId: Long? = null,
    noticesBetween: (ChatListItem.TranscriptMessage?, ChatListItem.TranscriptMessage?) -> List<ChatListItem.RequestNotice> =
        { _, _ -> emptyList() },
): PagingData<ChatListItem> =
    // One item per gap is all a separator pass gives, so a gap's notices ride in one holder that
    // the flatMap opens, and the second pass then sees them as rows.
    insertSeparators<ChatListItem.TranscriptMessage, Any> { newer, older ->
        noticesBetween(newer, older).takeIf { it.isNotEmpty() }?.let(::NoticeGap)
    }
        .flatMap<Any, ChatListItem> { if (it is NoticeGap) it.notices else listOf(it as ChatListItem) }
        .insertSeparators { newer: ChatListItem?, older: ChatListItem? ->
            separatorBetweenItems(newer, older, boundary, config, oldestEncryptedId)
        }

private class NoticeGap(val notices: List<ChatListItem.RequestNotice>)
