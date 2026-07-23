@file:OptIn(ExperimentalPagingApi::class)

package com.flipcash.shared.chat

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingData
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.ReactionSummary
import com.flipcash.services.models.chat.TypingState
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Feed-level operations: observing the conversation list and its unread state.
 *
 * Implemented by [com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate].
 */
interface FeedOperations {
    /** Reactive list of [chatType] conversations, sorted by last activity. */
    fun feed(chatType: ChatType): Flow<List<ChatSummary>>

    /** Emits the number of [chatType] conversations that have unread messages. */
    fun observeUnreadConversations(chatType: ChatType): Flow<Int>

    /** Triggers a server-side feed sync. Safe to call redundantly. */
    fun refreshFeed()
}

/**
 * Ephemeral real-time observations derived from the server event stream.
 *
 * Typing indicators and reaction overlays are held in memory only — they are
 * not persisted to Room.
 *
 * Implemented by [com.flipcash.shared.chat.internal.delegates.EventStreamDelegate].
 */
interface EventStreamOperations {
    /** Emits the set of users currently typing in [chatId]. */
    fun observeTypingIndicators(chatId: ChatId): Flow<Set<ActiveTypist>>

    /** Emits the current reaction summary for a specific message, or `null` if none. */
    fun observeReactions(chatId: ChatId, messageId: Long): Flow<ReactionSummary?>
}

/**
 * Resolves the [ChatId] of a DM from its participants, independent of any single
 * conversation. Two ways to arrive at an id:
 *
 * - **Derive** ([generateChatId]) — compute the canonical DM id from the
 *   participants alone. Deterministic and order-independent, so either user
 *   reaches the same id without a prior lookup, matching the server's
 *   `MustDeriveDmChatID`. Works even before a chat has been initialized.
 * - **Look up** ([getChatId]) — return the id of an *already-initialized* DM
 *   from local persistence, failing with [NoDmChatInitializedException] if none
 *   exists yet.
 *
 * Only tip DMs are derivable client-side: derivation needs the counterparty's
 * `UserId`, which the client has for a tip target but not for a phone [contact]
 * (`FlipcashContact` carries no user id). Contact DMs don't need it anyway — the
 * server pre-derives their id and delivers it via `GetFlipcashContacts`, so a
 * contact's id is always resolved through [getChatId], never derived.
 *
 * Implemented by [com.flipcash.shared.chat.internal.delegates.DmChatResolverDelegate].
 */
interface DmChatResolver {
    /** Derives the canonical TIP_DM [ChatId] between the current user and [userId]. */
    suspend fun generateChatId(userId: ID): Result<ChatId>

    /** Resolves the [ChatId] for an existing DM with [contact]. */
    suspend fun getChatId(contact: DeviceContact): Result<ChatId>

    /** Resolves the [ChatId] for an existing DM with [userId] (tip chat). */
    suspend fun getChatId(userId: ID): Result<ChatId>
}

/**
 * Per-chat messaging operations: sending, receiving, and read receipts.
 *
 * All methods target a single conversation identified by [ChatId]. Resolving
 * *which* [ChatId] to operate on is [DmChatResolver]'s job.
 *
 * Implemented by [com.flipcash.shared.chat.internal.delegates.MessagingDelegate].
 */
interface MessagingOperations {
    /**
     * Returns the other member of a DM (fetching from the server and persisting
     * if not cached locally), or `null` if it can't be resolved. Chat-type
     * agnostic — the returned [ChatMember] carries the counterparty's
     * [com.flipcash.services.models.UserProfile] (display name, avatar) for
     * rendering a sender without a phone contact.
     */
    suspend fun getOtherMember(chatId: ChatId): ChatMember?

    /** Returns the E.164 phone number of the other member in a DM, or `null` if unknown. */
    suspend fun getOtherMemberE164(chatId: ChatId): String?

    /** Marks [chatId] as the currently-viewed chat (used to suppress notifications). */
    fun setActiveChatId(chatId: ChatId?)

    /** Returns `true` if [chatId] is the currently-viewed chat. */
    fun isActiveChat(chatId: ChatId): Boolean

    /** Cancels any pending system notifications for [chatId]. */
    fun dismissNotifications(chatId: ChatId)

    /** Observes all messages in [chatId] as a flat list. */
    fun observeMessages(chatId: ChatId): Flow<List<ChatMessage>>

    /** Observes messages in [chatId] via Paging 3, with remote-mediated page loads. */
    fun observeMessagesPaged(chatId: ChatId): Flow<PagingData<ChatMessage>>

    /** Observes the member list for [chatId]. */
    fun observeMembers(chatId: ChatId): Flow<List<ChatMember>>

    /** Observes the other member's read pointer in [chatId] (for read receipts). */
    fun observeOtherReadPointer(chatId: ChatId): Flow<MessagePointer?>

    /** Fetches the full message history for [chatId] from the server and persists locally. */
    suspend fun loadMessages(chatId: ChatId)

    /** Sends a text message to [chatId]. Returns the server-confirmed [ChatMessage]. */
    suspend fun sendMessage(chatId: ChatId, content: String): Result<ChatMessage>

    /** Retries a failed pending message: resets to SENDING and re-sends to the server. */
    suspend fun retryMessage(chatId: ChatId, pendingClientIdHex: String, content: List<MessageContent>): Result<ChatMessage>

    /** Advances the local and remote read pointer for [chatId] to [messageId]. */
    suspend fun advanceReadPointer(chatId: ChatId, messageId: Long): Result<Unit>

    /** Marks [chatId] as fully read (advances pointer to the latest message). */
    suspend fun markAsRead(chatId: ChatId): Result<Unit>

    /** Notifies the server of the user's typing state in [chatId]. */
    suspend fun notifyTyping(chatId: ChatId, typingState: TypingState): Result<Unit>
}

/**
 * Unified facade for the chat subsystem, composing [FeedOperations],
 * [EventStreamOperations], [DmChatResolver], and [MessagingOperations].
 *
 * The concrete implementation is
 * [RealChatCoordinator][com.flipcash.shared.chat.internal.RealChatCoordinator],
 * which delegates each sub-interface to a focused singleton and wires
 * cross-delegate events in its `init` block.
 *
 * @see com.flipcash.shared.chat.internal.RealChatCoordinator
 */
interface ChatCoordinator : FeedOperations, EventStreamOperations, DmChatResolver, MessagingOperations {
    /** Full observable snapshot of chat state (feed, typing, reactions, active chat). */
    val state: StateFlow<ChatState>

    /** Tears down all connections, clears persisted data, and resets in-memory state. */
    suspend fun reset()
}

class NoDmChatInitializedException(e164: String) : Exception("No DM chat for $e164")
class FailedToGenerateChatIdException(identifier: String?) : Exception("Failed to generate chat ID for $identifier")
