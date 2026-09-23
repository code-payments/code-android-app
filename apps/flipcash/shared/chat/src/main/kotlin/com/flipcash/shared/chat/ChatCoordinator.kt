@file:OptIn(ExperimentalPagingApi::class)

package com.flipcash.shared.chat

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingData
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.EditChatParameters
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.ReactionSummary
import com.flipcash.services.models.chat.StartChatParameters
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
    /**
     * Reactive list of conversations of any of [chatTypes], sorted by last activity.
     *
     * Several types at once because a group and a DM share one list on screen; asking for one
     * type is still the common case and reads the same as it did.
     */
    fun feed(vararg chatTypes: ChatType): Flow<List<ChatSummary>>

    /**
     * What [feed] would emit right now, or null while it would emit nothing yet. Lets a screen
     * built after the feed is ready draw it on its first frame instead of waiting for a collector.
     */
    fun currentFeed(vararg chatTypes: ChatType): List<ChatSummary>?

    /** Emits the number of conversations of any of [chatTypes] that have unread messages. */
    fun observeUnreadConversations(vararg chatTypes: ChatType): Flow<Int>

    /**
     * The same conversations as [feed], paged.
     *
     * Room is the page source and [com.flipcash.app.persistence.sources.mediator.ChatFeedRemoteMediator]
     * fetches more when the local rows run out, so the list is not bounded by what one sync put in
     * memory. Unlike [feed] it does not read [ChatState.feed], so the two can be collected at once
     * without one starving the other.
     */
    fun feedPaged(vararg chatTypes: ChatType): Flow<PagingData<ChatSummary>>

    /** Triggers a server-side feed sync. Safe to call redundantly. */
    fun refreshFeed()

    /**
     * Locally sets [chatId]'s hidden flag so it drops out of / returns to the feed immediately
     * (optimistic — e.g. right after blocking or unblocking the other member), without waiting for
     * the next server sync.
     */
    suspend fun setChatHidden(chatId: ChatId, hidden: Boolean)
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

    /**
     * Releases [chatId] as the currently-viewed chat, if it still is. A null [chatId] releases
     * nothing.
     *
     * The conditional is the point. A chat screen claims the active chat on open and releases it
     * on teardown, but the two do not interleave in that order when one chat is opened directly
     * from another: both entries are alive at once and the outgoing one is disposed after the
     * incoming one has already claimed the chat. Clearing unconditionally there would clear the
     * chat the user is looking at, and every push for it would notify.
     */
    fun clearActiveChat(chatId: ChatId?)

    /** Returns `true` if [chatId] is the currently-viewed chat. */
    fun isActiveChat(chatId: ChatId): Boolean

    /** Cancels any pending system notifications for [chatId]. */
    fun dismissNotifications(chatId: ChatId)

    /** Observes all messages in [chatId] as a flat list. */
    fun observeMessages(chatId: ChatId): Flow<List<ChatMessage>>

    /**
     * Whether the user has ever sent a tip — a Cash message with verb TIPPED — or `null` while the
     * answer is not yet trustworthy.
     *
     * The read is of a local cache, so an absent TIPPED message means "never tipped" only once that
     * cache is known to be complete; before then it is indistinguishable from history that has not
     * arrived. Onboarding is the caller, and it draws a tutorial off the answer, so it needs the
     * third state rather than a `false` it has to guess about. Resolves to `true`/`false` once
     * [ChatHydrationState] leaves [ChatHydrationState.Unknown] — including on
     * [ChatHydrationState.Unavailable], so an unreachable server ends the wait instead of extending
     * it forever.
     */
    fun hasEverTipped(): Flow<Boolean?>

    /** Observes messages in [chatId] via Paging 3, with remote-mediated page loads. */
    fun observeMessagesPaged(chatId: ChatId): Flow<PagingData<ChatMessage>>

    /**
     * The stored message [messageId] in [chatId], or `null` if this device has never stored it.
     *
     * A local read only. A reply citing a message from before this device's history resolves to
     * `null`, which the transcript renders as a reply with no citation rather than as an error.
     */
    suspend fun getMessage(chatId: ChatId, messageId: Long): ChatMessage?

    /**
     * How far back [messageId] sits from the newest message in [chatId], or `null` when this
     * device has not stored it. Bounds the walk that scrolls a quote's citation into view.
     */
    suspend fun distanceFromNewest(chatId: ChatId, messageId: Long): Int?

    /** Observes the member list for [chatId]. */
    fun observeMembers(chatId: ChatId): Flow<List<ChatMember>>

    /**
     * One chat's metadata and this device's membership of it, re-emitted on change, or `null`
     * while the chat is not stored locally.
     *
     * Rebuilt per emission the way the feed builds a summary: the row supplies title, picture,
     * roster summary and rules, the member table supplies the roster subset, and the message table
     * supplies the last message.
     */
    fun observeMetadata(chatId: ChatId): Flow<ChatMembership?>

    /**
     * Fetches [chatId] from the server for a chat this device holds no row for, so a conversation
     * reached by invite link or push tap can render before it has ever been synced.
     *
     * Returns `null` when the row is already stored — [observeMetadata] is answering in that case,
     * and a second copy would only race it — and when the fetch fails.
     *
     * Deliberately not persisted. `GetChat` returns the chat, not the caller's relationship to it,
     * and the row's membership column has to be written with something, so storing this would
     * record a guess: `true` opens a group the viewer has not joined, `false` gates a member whose
     * feed has not synced yet. The result carries membership as `null` instead, and the join path
     * writes the row once the server has confirmed it.
     */
    suspend fun hydrateChat(chatId: ChatId): ChatMembership?

    /**
     * Every profile this device holds, keyed by user-id hex.
     *
     * The group transcript indexes into this by a bubble's `senderId` to draw a name and avatar.
     * A DM never reads it: its only other participant is already the screen's subject.
     */
    fun observeSenderProfiles(): Flow<Map<String, UserProfile>>

    /**
     * Asks for [userId]'s profile if nothing has asked already, for a sender the roster subset
     * does not cover. Returns immediately; the answer arrives through [observeSenderProfiles].
     */
    fun requestSenderProfile(userId: ID)

    /** Observes the other member's read pointer in [chatId] (for read receipts). */
    fun observeOtherReadPointer(chatId: ChatId): Flow<MessagePointer?>

    /** Fetches the full message history for [chatId] from the server and persists locally. */
    suspend fun loadMessages(chatId: ChatId)

    /**
     * Persists a message the server delivered inside a push payload, without an RPC.
     *
     * Idempotent on `eventSequence`: a copy that is not newer than the stored row is
     * dropped, so re-delivery of the same push and a following [loadMessages] converge
     * on the same transcript.
     */
    suspend fun applyPushedMessage(chatId: ChatId, message: ChatMessage)

    /**
     * Sends a text message to [chatId]. Returns the server-confirmed [ChatMessage].
     *
     * [replyToMessageId] cites a message in the same chat, which wraps the body in
     * [MessageContent.Reply]. It defaults to `null` so a caller with no message to cite — the
     * notification quick-reply replies to a conversation, not to a message — is unchanged.
     */
    suspend fun sendMessage(
        chatId: ChatId,
        content: String,
        replyToMessageId: Long? = null,
    ): Result<ChatMessage>

    /** Retries a failed pending message: resets to SENDING and re-sends to the server. */
    suspend fun retryMessage(chatId: ChatId, pendingClientIdHex: String, content: List<MessageContent>): Result<ChatMessage>

    /**
     * Replaces [messageId]'s body with [text], optimistically.
     *
     * The change is visible immediately as a [PendingMutation] over the stored row; the row itself
     * is only written once the server agrees. Returns the server's version of the message.
     */
    suspend fun editMessage(chatId: ChatId, messageId: Long, text: String): Result<ChatMessage>

    /**
     * Deletes [messageId] for everyone, optimistically.
     *
     * "For everyone" is the only delete the wire models — there is no local-only variant to choose
     * between. Same overlay-then-reconcile path as [editMessage].
     */
    suspend fun deleteMessage(chatId: ChatId, messageId: Long): Result<ChatMessage>

    /**
     * Emits the edits and deletes in [chatId] that the server has not answered yet, keyed by
     * message id, for composing over the stored transcript with
     * [applying][com.flipcash.shared.chat.applying].
     */
    fun observePendingMutations(chatId: ChatId): Flow<Map<Long, PendingMutation>>

    /** Advances the local and remote read pointer for [chatId] to [messageId]. */
    suspend fun advanceReadPointer(chatId: ChatId, messageId: Long): Result<Unit>

    /** Marks [chatId] as fully read (advances pointer to the latest message). */
    suspend fun markAsRead(chatId: ChatId): Result<Unit>

    /** Notifies the server of the user's typing state in [chatId]. */
    suspend fun notifyTyping(chatId: ChatId, typingState: TypingState): Result<Unit>

    /**
     * Mutes [chatId] for this viewer, [mute] saying until when or indefinitely, and stores the
     * viewer state the server answers with.
     *
     * Not optimistic, unlike the leave: the stored state is versioned, and the version that decides
     * whether a later stream update wins is the server's to mint. Writing a guess at it would
     * either lose to the echo of this very call or shut a genuinely newer update out.
     */
    suspend fun mute(chatId: ChatId, mute: MuteState): Result<Unit>

    /**
     * Clears the mute on [chatId] for this viewer.
     *
     * Its own call rather than a zero-length [mute]: the contract has two mute shapes, a deadline
     * and forever, and neither of them says "not muted".
     */
    suspend fun unmute(chatId: ChatId): Result<Unit>
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
/**
 * Membership of a group chat.
 *
 * Implemented by [com.flipcash.shared.chat.internal.delegates.GroupFeedDelegate].
 */
interface GroupOperations {
    /**
     * Creates a group from [parameters], caching it so it is in the list before the next sync, and
     * returns the chat the server created.
     *
     * [idempotencyKey] is the caller's to mint, once, where the intent to create originates — the
     * server derives the chat's identity from the caller and this key, so the same key retried
     * returns the chat the first attempt created instead of a second chat. Minting it here would
     * make every retry a new chat.
     */
    suspend fun create(
        parameters: StartChatParameters,
        idempotencyKey: IdempotencyKey,
    ): Result<ChatMetadata>

    /**
     * Applies [parameters] to [chatId] and stores the chat the server answers with.
     *
     * A partial update: [EditChatParameters] leaves every unset field alone, so editing the title
     * cannot clear the picture. Requires `ViewerState.Permissions.canEdit`, which is the server's
     * to compute — a caller without it gets `DENIED` rather than a local refusal.
     *
     * The post-edit metadata is persisted from the response rather than refetched, and the
     * `titleChanged` / `pictureChanged` echo that follows carries the same absolute values and is
     * applied unconditionally, so the two converge instead of compounding. Suppressing the echo
     * would be the wrong fix: it cannot be told apart from a *different* editor's change arriving
     * in the same window, which is a change this device does need.
     */
    suspend fun editChat(
        chatId: ChatId,
        parameters: EditChatParameters,
    ): Result<ChatMetadata>

    /** Joins [chatId], caching the chat so it is in the list before the next sync. */
    suspend fun join(chatId: ChatId): Result<Unit>

    /**
     * Leaves [chatId]. The chat drops out of the list immediately and comes back if the call
     * fails.
     */
    suspend fun leave(chatId: ChatId): Result<Unit>
}

interface ChatCoordinator :
    FeedOperations,
    EventStreamOperations,
    DmChatResolver,
    MessagingOperations,
    GroupOperations {
    /** Full observable snapshot of chat state (feed, typing, reactions, active chat). */
    val state: StateFlow<ChatState>

    /**
     * Closes connections, cancels jobs, and drops in-memory state.
     *
     * Deliberately leaves the persisted cache alone. The Room database is per-account
     * (`FlipcashDatabase.init` names the file from the account entropy), so signing out does not
     * have to erase anything to keep the next account's data separate — logging in swaps to a
     * different file. Keeping the cache lets a re-login reconcile what it already has via the
     * feed sync's catch-up instead of rebuilding from nothing, which is what the "send a tip"
     * onboarding milestone was silently losing.
     *
     * @see clearCache for the one caller that does want the data gone.
     */
    suspend fun teardown()

    /**
     * Erases this account's persisted chat history — metadata, messages, and members.
     *
     * Account deletion only. Ordinary logout and account switching go through [teardown] and
     * keep the cache.
     */
    suspend fun clearCache()
}

class NoDmChatInitializedException(e164: String) : Exception("No DM chat for $e164")
class FailedToGenerateChatIdException(identifier: String?) : Exception("Failed to generate chat ID for $identifier")
