package com.flipcash.client

import com.codeinc.flipcash.wire.events.v1.ChatUpdate as WireChatUpdate
import com.codeinc.flipcash.wire.messaging.v1.EmojiReaction as WireEmojiReaction
import com.codeinc.flipcash.wire.messaging.v1.Event as WireChatEvent
import com.codeinc.flipcash.wire.messaging.v1.Message as WireMessage
import com.codeinc.flipcash.wire.messaging.v1.Mutation as WireMutation
import com.codeinc.flipcash.wire.messaging.v1.IsTypingNotification as WireTyping
import com.codeinc.flipcash.wire.messaging.v1.Pointer as WirePointer
import com.codeinc.flipcash.wire.messaging.v1.ReactionSummary as WireReactionSummary
import com.codeinc.flipcash.wire.messaging.v1.ReactionUpdate as WireReactionUpdate
import com.codeinc.flipcash.wire.messaging.v1.Reactor as WireReactor
import com.flipcash.chat.ContentCodec
import com.flipcash.chat.MessageContent
import com.squareup.wire.FieldEncoding
import com.squareup.wire.ProtoReader
import kotlin.time.Instant
import okio.Buffer
import okio.ByteString

// Wire -> domain, following :services:flipcash's ProtobufToLocal mappers field for field. An absent
// submessage reads as protobuf-java's default instance would: empty ids, zero numbers, the epoch.

internal fun com.squareup.wire.Instant?.toInstant(): Instant =
    if (this == null) Instant.fromEpochSeconds(0) else Instant.fromEpochSeconds(getEpochSecond(), getNano())

internal fun ByteString?.toId(): Id = Id(this?.toByteArray() ?: ByteArray(0))

/**
 * `boost_usd` in whole cents. Rounded half up like `Math.round`, not truncated: 0.29 * 100 is
 * 28.999… in a double.
 */
internal fun Double.toUsdCents(): Long = kotlin.math.floor(this * 100 + 0.5).toLong()

internal fun WireChatUpdate.toDomain(): ChatUpdate = ChatUpdate(
    chatId = chat?.value_.toId(),
    pointerUpdates = pointer_updates?.pointers.orEmpty().map { it.toDomain() },
    typingNotifications = is_typing_notifications?.is_typing_notifications.orEmpty().map { it.toDomain() },
    events = events?.events.orEmpty().map { it.toDomain() },
    reactionUpdates = reaction_updates?.reaction_updates.orEmpty().map { it.toDomain() },
)

internal fun WireChatEvent.toDomain(): ChatEvent = ChatEvent(
    sequence = sequence,
    count = count,
    ts = ts.toInstant(),
    mutations = mutations.map { it.toDomain() },
)

internal fun WireMutation.toDomain(): ChatMutation = when {
    message_sent != null -> ChatMutation.MessageSent(message_sent!!.toDomain())
    message_edited != null -> ChatMutation.MessageEdited(message_edited!!.toDomain())
    message_deleted != null -> ChatMutation.MessageDeleted(message_deleted!!.toDomain())
    // Android maps an unknown mutation to a sent default message; keep that.
    else -> ChatMutation.MessageSent(WireMessage().toDomain())
}

internal fun WireMessage.toDomain(): ChatMessage = ChatMessage(
    messageId = message_id?.value_ ?: 0L,
    senderId = sender_id?.value_?.let { Id(it.toByteArray()) },
    content = content.map { ContentCodec.decode(it.encode()) }
        .filterNot { it is MessageContent.System && it.fallbackText.isSystemBlank() },
    timestamp = ts.toInstant(),
    unreadSeq = unread_seq,
    lastEditedTs = last_edited_ts?.toInstant(),
    eventSequence = event_sequence,
    reactions = reactions?.toDomain(),
    redacted = redacted,
)

internal fun WireReactionSummary.toDomain(): ReactionSummary = ReactionSummary(
    messageId = message_id?.value_ ?: 0L,
    reactions = reactions.map { it.toDomain() },
)

internal fun WireEmojiReaction.toDomain(): EmojiReaction = EmojiReaction(
    emoji = emoji?.value_.orEmpty(),
    count = count,
    selfReactor = self_reactor?.toDomain(),
    sampleReactors = sample_reactors.map { it.toDomain() },
    version = version,
    boostCents = boost_usd.toUsdCents(),
)

internal fun WireReactor.toDomain(): Reactor = Reactor(
    userId = user_id?.value_.toId(),
    reactedAt = reacted_ts.toInstant(),
    version = version,
    boostCents = boost_usd.toUsdCents(),
)

internal fun WireReactionUpdate.toDomain(): ReactionUpdate = ReactionUpdate(
    messageId = message_id?.value_ ?: 0L,
    emoji = emoji?.value_.orEmpty(),
    actor = actor?.value_.toId(),
    action = when (action) {
        WireReactionUpdate.Action.ADDED -> ReactionUpdate.Action.ADDED
        WireReactionUpdate.Action.REMOVED -> ReactionUpdate.Action.REMOVED
        WireReactionUpdate.Action.BOOSTED -> ReactionUpdate.Action.BOOSTED
        else -> ReactionUpdate.Action.UNKNOWN
    },
    count = count,
    version = version,
    reactedAt = reacted_ts.toInstant(),
    boostCents = boost_usd.toUsdCents(),
    actorBoostCents = actor_boost_usd.toUsdCents(),
)

internal fun WirePointer.toDomain(): MessagePointer = MessagePointer(
    type = when (type) {
        WirePointer.Type.SENT -> MessagePointer.Type.SENT
        WirePointer.Type.DELIVERED -> MessagePointer.Type.DELIVERED
        WirePointer.Type.READ -> MessagePointer.Type.READ
        else -> MessagePointer.Type.UNKNOWN
    },
    userId = user_id?.value_.toId(),
    value = value_?.value_ ?: 0L,
    timestamp = ts.toInstant(),
)

/**
 * Whether this text is nothing but white space by the definition iOS shares
 * (`CharacterSet.whitespacesAndNewlines`), as :services:flipcash's `isSystemBlank`. An empty string
 * is blank.
 */
internal fun String.isSystemBlank(): Boolean = all {
    when (it.code) {
        in 0x0009..0x000D, 0x0020, 0x0085, 0x00A0, 0x1680, in 0x2000..0x200A,
        0x2028, 0x2029, 0x202F, 0x205F, 0x3000 -> true
        else -> false
    }
}

/**
 * The raw number of enum field [tag] when Wire could not map it to a constant. Wire decodes such a
 * number to the zero constant and keeps it in `unknownFields`; protobuf-java reports
 * `UNRECOGNIZED`. Where the zero constant means something (`OK`, `DENIED`), this tells them apart.
 */
internal fun rawEnum(unknownFields: ByteString, tag: Int): Int? {
    if (unknownFields.size == 0) return null
    val reader = ProtoReader(Buffer().write(unknownFields))
    var value: Int? = null
    val token = reader.beginMessage()
    while (true) {
        val next = reader.nextTag()
        if (next == -1) break
        if (next == tag && reader.peekFieldEncoding() == FieldEncoding.VARINT) {
            value = reader.readVarint64().toInt()
        } else {
            reader.skip()
        }
    }
    reader.endMessageAndGetUnknownFields(token)
    return value
}

internal fun WireTyping.toDomain(): TypingNotification = TypingNotification(
    userId = user_id?.value_.toId(),
    state = when (state) {
        WireTyping.State.STARTED_TYPING -> TypingNotification.State.STARTED_TYPING
        WireTyping.State.STILL_TYPING -> TypingNotification.State.STILL_TYPING
        WireTyping.State.STOPPED_TYPING -> TypingNotification.State.STOPPED_TYPING
        WireTyping.State.TYPING_TIMED_OUT -> TypingNotification.State.TYPING_TIMED_OUT
        else -> TypingNotification.State.UNKNOWN
    },
)
