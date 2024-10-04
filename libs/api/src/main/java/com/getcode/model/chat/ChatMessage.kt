package com.getcode.model.chat

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.Cursor
import com.getcode.model.ID
import com.getcode.utils.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A message in a chat
 *
 * @param id Globally unique ID for this message
 * This is a time based UUID in v2
 * @param senderId The chat member that sent the message.
 * For [ChatType.Unknown] chats, this field is omitted since the chat has exactly 1 member.
 * @param cursor Cursor value for this message for reference in a paged GetMessagesRequest
 * @param dateMillis Timestamp this message was generated at
 * @param contents Ordered message content. A message may have more than one piece of content.
 */
@Serializable
data class ChatMessage(
    val id: ID, // time based UUID in v2
    val senderId: ID?,
    val isFromSelf: Boolean,
    val cursor: Cursor,
    val dateMillis: Long,
    val contents: List<MessageContent>,
) {
    val hasEncryptedContent: Boolean
        get() {
            return contents.firstOrNull { it is MessageContent.SodiumBox } != null
        }

    fun decryptingUsing(keyPair: KeyPair): ChatMessage {
        return ChatMessage(
            id = id,
            senderId = senderId,
            isFromSelf = isFromSelf,
            dateMillis = dateMillis,
            cursor = cursor,
            contents = contents.map {
                when (it) {
                    is MessageContent.Exchange,
                    is MessageContent.Localized,
                    is MessageContent.Decrypted,
                    is MessageContent.IdentityRevealed,
                    is MessageContent.RawText,
                    is MessageContent.ThankYou -> it // passthrough
                    is MessageContent.SodiumBox -> {
                        val decrypted = it.data.decryptMessageUsingNaClBox(keyPair = keyPair)
                        if (decrypted != null) {
                            MessageContent.Decrypted(data = decrypted, isFromSelf = isFromSelf)
                        } else {
                            it
                        }
                    }


                }
            }
        )
    }
}