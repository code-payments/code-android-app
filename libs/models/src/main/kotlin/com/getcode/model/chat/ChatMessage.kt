package com.getcode.model.chat

import com.getcode.model.Cursor
import com.getcode.model.ID
import kotlinx.serialization.Serializable

/**
 * A message in a chat
 *
 * @param id Globally unique ID for this message
 * This is a time based UUID in v2
 * @param senderId The chat member that sent the message.
 * @param cursor Cursor value for this message for reference in a paged GetMessagesRequest
 * @param dateMillis Timestamp this message was generated at
 * @param contents Ordered message content. A message may have more than one piece of content.
 */
@Serializable
data class ChatMessage(
    val id: ID, // time based UUID in v2
    val senderId: ID,
    val isFromSelf: Boolean,
    val cursor: Cursor = id,
    val dateMillis: Long,
    val contents: List<MessageContent>,
) {
    val hasEncryptedContent: Boolean
        get() {
            return contents.firstOrNull { it is MessageContent.SodiumBox } != null
        }
}