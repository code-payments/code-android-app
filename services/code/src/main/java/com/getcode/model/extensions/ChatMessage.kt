package com.getcode.model.extensions

import com.getcode.ed25519.Ed25519
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageContent

fun ChatMessage.decryptingUsing(keyPair: Ed25519.KeyPair): ChatMessage {
    return ChatMessage(
        id = id,
        senderId = senderId,
        isFromSelf = isFromSelf,
        dateMillis = dateMillis,
        contents = contents.map {
            when (it) {
                is MessageContent.SodiumBox -> {
                    val decrypted = it.data.decryptMessageUsingNaClBox(keyPair = keyPair)
                    if (decrypted != null) {
                        MessageContent.Decrypted(data = decrypted, isFromSelf = isFromSelf)
                    } else {
                        it
                    }
                }

                is MessageContent.Exchange,
                is MessageContent.Localized,
                is MessageContent.Decrypted,
                is MessageContent.RawText,
                is MessageContent.Announcement,
                is MessageContent.Reaction,
                is MessageContent.Reply,
                is MessageContent.DeletedMessage,
                is MessageContent.Unknown,
                is MessageContent.ActionableAnnouncement,
                is MessageContent.MessageInReview,
                is MessageContent.MessageTip -> it // passthrough
            }
        }
    )
}