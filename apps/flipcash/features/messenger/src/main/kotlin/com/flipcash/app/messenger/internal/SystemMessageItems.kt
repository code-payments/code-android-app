package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.displayText
import com.flipcash.shared.chat.models.userLinks
import com.getcode.opencode.model.core.ID
import com.getcode.util.resources.ResourceHelper
import kotlin.time.Instant

/**
 * The transcript row for one System content of a message, or `null` when it has no text to draw.
 * The null case is a notice stored before the mapper dropped blank ones; it produces no row at all.
 * [nameOf] names the users the notice is about; see [displayText].
 */
internal fun systemMessageItem(
    messageId: Long,
    contentIndex: Int,
    timestamp: Instant,
    content: MessageContent.System,
    resources: ResourceHelper,
    selfId: ID?,
    nameOf: (ID) -> String?,
): ChatListItem.SystemMessage? {
    val text = content.displayText(resources, selfId, nameOf) ?: return null
    return ChatListItem.SystemMessage(
        messageId = messageId,
        contentIndex = contentIndex,
        timestamp = timestamp,
        text = text,
        userLinks = content.userLinks(resources, text, selfId, nameOf),
    )
}
