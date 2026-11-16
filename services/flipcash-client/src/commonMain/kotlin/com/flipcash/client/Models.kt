package com.flipcash.client

import com.flipcash.chat.MessageContent
import kotlin.time.Instant

/** An opaque id from the wire (chat, user, paging token). Compared by content. */
class Id(val bytes: ByteArray) {
    override fun equals(other: Any?) = other is Id && bytes.contentEquals(other.bytes)
    override fun hashCode() = bytes.contentHashCode()
    override fun toString() = "Id(${bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }})"
}

/** Paging for list calls, as Android's `QueryOptions`: [limit] items, newest first when [descending]. */
data class QueryOptions(
    val limit: Int = 100,
    val descending: Boolean = true,
    val token: Id? = null,
)

/**
 * One `events.v1.ChatUpdate` from the event stream.
 *
 * Ported: pointer updates, typing notifications, message events and reaction updates. Not yet
 * ported, and dropped:
 * metadata, roster, lobby and payment-request updates.
 */
data class ChatUpdate(
    val chatId: Id,
    val pointerUpdates: List<MessagePointer>,
    val typingNotifications: List<TypingNotification>,
    val events: List<ChatEvent>,
    val reactionUpdates: List<ReactionUpdate>,
)

data class ChatEvent(
    val sequence: Long,
    val count: Int,
    val ts: Instant,
    val mutations: List<ChatMutation>,
)

sealed interface ChatMutation {
    val message: ChatMessage

    data class MessageSent(override val message: ChatMessage) : ChatMutation
    data class MessageEdited(override val message: ChatMessage) : ChatMutation
    data class MessageDeleted(override val message: ChatMessage) : ChatMutation
}

/**
 * A `messaging.v1.Message`. [content] leaves out system notices with no text, as Android does: the
 * message stays so read pointers and paging can anchor on its id.
 */
data class ChatMessage(
    val messageId: Long,
    val senderId: Id?,
    val content: List<MessageContent>,
    val timestamp: Instant,
    val unreadSeq: Long,
    val lastEditedTs: Instant?,
    val eventSequence: Long,
    val reactions: ReactionSummary?,
    val redacted: Boolean,
)

data class ReactionSummary(val messageId: Long, val reactions: List<EmojiReaction>)

/** [boostCents] is `boost_usd` in whole cents, rounded. */
data class EmojiReaction(
    val emoji: String,
    val count: Long,
    val selfReactor: Reactor?,
    val sampleReactors: List<Reactor>,
    val version: Long,
    val boostCents: Long,
)

data class Reactor(
    val userId: Id,
    val reactedAt: Instant,
    val version: Long,
    val boostCents: Long,
)

data class ReactionUpdate(
    val messageId: Long,
    val emoji: String,
    val actor: Id,
    val action: Action,
    val count: Long,
    val version: Long,
    val reactedAt: Instant,
    val boostCents: Long,
    val actorBoostCents: Long,
) {
    enum class Action { UNKNOWN, ADDED, REMOVED, BOOSTED }
}

data class MessagePointer(
    val type: Type,
    val userId: Id,
    val value: Long,
    val timestamp: Instant,
) {
    enum class Type { UNKNOWN, SENT, DELIVERED, READ }
}

data class TypingNotification(
    val userId: Id,
    val state: State,
) {
    enum class State { UNKNOWN, STARTED_TYPING, STILL_TYPING, STOPPED_TYPING, TYPING_TIMED_OUT }
}

/** One page of `GetReactors`. */
data class ReactorsPage(
    val reactors: List<Reactor>,
    val hasMore: Boolean,
    val pagingToken: Id?,
)

/** A non-OK `GetReactorsResponse.Result`. */
sealed class GetReactorsException(message: String) : Exception(message) {
    class Denied : GetReactorsException("Denied")
    class MessageNotFound : GetReactorsException("Message not found")
    class Unrecognized(val result: Int) : GetReactorsException("Unrecognized result $result")
}
