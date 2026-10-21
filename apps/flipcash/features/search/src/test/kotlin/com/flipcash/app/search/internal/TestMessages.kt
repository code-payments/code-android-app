package com.flipcash.app.search.internal

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import kotlin.time.Instant

internal val ChatA = ChatId("0a")
internal val ChatB = ChatId("0b")
internal val Alice = listOf<Byte>(1)

internal fun stored(
    id: Long,
    vararg content: MessageContent,
    chatId: ChatId = ChatA,
    at: Long = id,
    fromSelf: Boolean = false,
) = StoredMessage(
    chatId = chatId,
    message = ChatMessage(
        messageId = id,
        senderId = if (fromSelf) null else Alice,
        content = content.toList(),
        timestamp = Instant.fromEpochSeconds(at),
        unreadSeq = id,
        isFromSelf = fromSelf,
    ),
)

internal fun text(value: String) = MessageContent.Text(value)
