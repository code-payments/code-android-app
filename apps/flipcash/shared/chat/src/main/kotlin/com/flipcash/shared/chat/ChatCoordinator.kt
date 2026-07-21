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
 * Per-chat messaging operations: sending, receiving, read receipts, and identity.
 *
 * All methods target a single conversation identified by [ChatId].
 *
 * Implemented by [com.flipcash.shared.chat.internal.delegates.MessagingDelegate].
 */
interface MessagingOperations {
    /** Resolves the [ChatId] for an existing DM with [contact]. */
    suspend fun getChatId(contact: DeviceContact): Result<ChatId>

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
 * [EventStreamOperations], and [MessagingOperations].
 *
 * The concrete implementation is
 * [RealChatCoordinator][com.flipcash.shared.chat.internal.RealChatCoordinator],
 * which delegates each sub-interface to a focused singleton and wires
 * cross-delegate events in its `init` block.
 *
 * @see com.flipcash.shared.chat.internal.RealChatCoordinator
 */
interface ChatCoordinator : FeedOperations, EventStreamOperations, MessagingOperations {
    /** Full observable snapshot of chat state (feed, typing, reactions, active chat). */
    val state: StateFlow<ChatState>

    /** Tears down all connections, clears persisted data, and resets in-memory state. */
    suspend fun reset()
}

class NoDmChatInitializedException(e164: String) : Exception("No DM chat for $e164")
