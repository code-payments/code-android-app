package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.displayText
import kotlin.time.Instant

/**
 * The transcript row for one System content of a message, or `null` when it has no text to draw.
 * The null case is a notice stored before the mapper dropped blank ones; it produces no row at all.
 */
internal fun systemMessageItem(
    messageId: Long,
    contentIndex: Int,
    timestamp: Instant,
    content: MessageContent.System,
): ChatListItem.SystemMessage? {
    val text = content.displayText() ?: return null
    return ChatListItem.SystemMessage(
        messageId = messageId,
        contentIndex = contentIndex,
        timestamp = timestamp,
        text = text,
    )
}
