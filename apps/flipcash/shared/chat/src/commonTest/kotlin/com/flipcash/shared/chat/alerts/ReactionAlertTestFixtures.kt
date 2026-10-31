package com.flipcash.shared.chat.alerts

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.Emoji
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.ReactionUpdate
import kotlinx.datetime.Instant

internal val SELF: List<Byte> = listOf(1)
internal val ALICE: List<Byte> = listOf(2)
internal val BOB: List<Byte> = listOf(3)
internal val CHAT = ChatId(byteArrayOf(9))
internal val OTHER_CHAT = ChatId(byteArrayOf(8))

internal fun reactionUpdate(
    actor: List<Byte> = ALICE,
    action: ReactionUpdate.Action = ReactionUpdate.Action.ADDED,
    emoji: String = "🔥",
    messageId: Long = 10,
    actorBoostCents: Long = 0,
) = ReactionUpdate(
    messageId = messageId,
    emoji = Emoji(emoji),
    actor = actor,
    action = action,
    count = 1,
    version = 1,
    reactedAt = Instant.fromEpochSeconds(1000),
    actorBoostCents = actorBoostCents,
)

internal fun message(senderId: List<Byte>?, fromSelf: Boolean = false, text: String = "hello") = ChatMessage(
    messageId = 10,
    senderId = senderId,
    content = listOf(MessageContent.Text(text)),
    timestamp = Instant.fromEpochSeconds(1000),
    unreadSeq = 0,
    isFromSelf = fromSelf,
)

internal fun alertEvent(
    messageId: Long = 10,
    chatId: ChatId = CHAT,
    actor: List<Byte> = ALICE,
    actorName: String? = "Alice",
    emoji: String = "🔥",
    action: ReactionUpdate.Action = ReactionUpdate.Action.ADDED,
    boostDeltaCents: Long? = null,
    quote: String = "hello",
) = ReactionAlertEvent(
    chatId = chatId,
    messageId = messageId,
    emoji = emoji,
    actor = actor,
    action = action,
    boostDeltaCents = boostDeltaCents,
    actorName = actorName,
    chatName = "Moony",
    quote = quote,
)
