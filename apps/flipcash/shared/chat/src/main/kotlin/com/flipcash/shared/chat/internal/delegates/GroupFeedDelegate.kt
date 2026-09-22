package com.flipcash.shared.chat.internal.delegates

import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.EditChatParameters
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.chat.RosterChange
import com.flipcash.services.models.chat.StartChatParameters
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.GroupOperations
import com.flipcash.shared.chat.internal.RosterStateHolder
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the group side of the conversation list: fetching the group feed, joining and leaving, and
 * the two roster changes that mean something to the list rather than to the roster.
 *
 * Beside [FeedSyncDelegate] rather than inside it. The DM sync treats a contact-feed failure as a
 * failure of the whole sync; a group-feed failure must not take the DM list down with it, and the
 * group feed pages where the DM feed does not. Folding the two together would put a
 * `when (chatType)` in every method and make one list's failure mode reachable from the other.
 *
 * **Cross-delegate communication:** as in [FeedSyncDelegate], work that belongs to another
 * delegate is emitted on [events] and routed by
 * [RealChatCoordinator][com.flipcash.shared.chat.internal.RealChatCoordinator] rather than called
 * directly.
 *
 * Requires [initialize] with a [CoroutineScope] before [syncGroupFeed] can be launched.
 */
@Singleton
class GroupFeedDelegate @Inject constructor(
    private val chatController: ChatController,
    private val metadataDataSource: ChatMetadataDataSource,
    private val memberDataSource: ChatMemberDataSource,
    private val messageDataSource: ChatMessageDataSource,
    private val rosterStateHolder: RosterStateHolder,
    private val userManager: UserManager,
) : GroupOperations {

    sealed interface Event {
        /** A group whose transcript the device does not have yet. */
        data class LoadMessages(val chatId: ChatId) : Event

        /**
         * The group's applied cursor is behind the feed's head, so the device is missing a window
         * of its transcript. Same contract as
         * [FeedSyncDelegate.Event.DeltaSyncNeeded][FeedSyncDelegate.Event.DeltaSyncNeeded].
         */
        data class DeltaSyncNeeded(val chatId: ChatId) : Event
    }

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private var scope: CoroutineScope? = null
    private var syncJob: Job? = null

    internal fun initialize(scope: CoroutineScope) {
        this.scope = scope
    }

    internal fun syncGroupFeed() {
        val scope = scope ?: return
        syncJob?.cancel()
        syncJob = scope.launch { performGroupFeedSync() }
    }

    internal fun cancelJobs() {
        syncJob?.cancel()
        syncJob = null
    }

    /**
     * Fetches the first page of the group feed and caches it.
     *
     * Deliberately does not reconcile removals. This is one page, and a group below it has not
     * been left — it has not been reached. That comparison needs a whole pass, which is what
     * [com.flipcash.app.persistence.sources.mediator.ChatFeedRemoteMediator] knows about and this
     * does not.
     */
    internal suspend fun performGroupFeedSync() {
        val page = chatController.getGroupChatFeed().getOrElse {
            trace(tag = TAG, message = "Group feed sync failed", type = TraceType.Error)
            return
        }
        persist(page.chats)

        // Caching the feed is not the same as having the transcript: [persist] writes each group's
        // last-message preview and nothing else, so without this a synced group opens to a single
        // bubble. The applied cursor is what says whether the transcript was ever pulled — a feed
        // sync never writes it. Same rule the DM feed applies in [FeedSyncDelegate].
        for (chat in page.chats) {
            val cursor = metadataDataSource.getLatestEventSequence(chat.chatId)
            when {
                cursor <= 0L -> _events.send(Event.LoadMessages(chat.chatId))
                chat.latestEventSequence > cursor -> _events.send(Event.DeltaSyncNeeded(chat.chatId))
            }
        }
    }

    /**
     * Creates the group [parameters] describe, caching it the same way [join] caches a chat the
     * user just entered: the creator is a member of what they created, so the row belongs in the
     * list on the response rather than after the next feed sync.
     *
     * No [Event.LoadMessages] — a chat that was created this instant has no transcript to fetch,
     * and asking for one would be a round trip that can only come back empty. The membership flag
     * is still written explicitly: `StartChat` returns the chat, not the caller's relationship to
     * it, so nothing in [persist] would set it.
     */
    override suspend fun create(
        parameters: StartChatParameters,
        idempotencyKey: IdempotencyKey,
    ): Result<ChatMetadata> {
        return chatController.startChat(parameters, idempotencyKey)
            .onSuccess { metadata ->
                persist(listOf(metadata))
                metadataDataSource.setMembership(metadata.chatId, isMember = true)
            }
    }

    /**
     * Edits [chatId] and stores the post-edit metadata the server returns.
     *
     * [persist] rather than a refetch: `EditChat` answers `OK` with the chat as it now stands, so
     * a second round trip could only return the same thing later. No membership write — unlike
     * [create] and [join], an edit does not change who the caller is to the chat.
     */
    override suspend fun editChat(
        chatId: ChatId,
        parameters: EditChatParameters,
    ): Result<ChatMetadata> {
        return chatController.editChat(chatId, parameters)
            .onSuccess { metadata -> persist(listOf(metadata)) }
            .onFailure {
                trace(tag = TAG, message = "Edit failed for $chatId", type = TraceType.Error)
            }
    }

    /**
     * Joins [chatId], caching the chat the server returns so the list has it before any sync runs.
     */
    override suspend fun join(chatId: ChatId): Result<Unit> {
        return chatController.joinChat(chatId)
            .onSuccess { metadata ->
                persist(listOf(metadata))
                metadataDataSource.setMembership(metadata.chatId, isMember = true)
                _events.send(Event.LoadMessages(metadata.chatId))
            }
            .map { }
    }

    /**
     * Leaves [chatId].
     *
     * The row is cleared first so the chat drops out of the list on the tap rather than a round
     * trip later, and put back if the call fails — the same optimistic shape as
     * [FeedSyncDelegate.setChatHidden], with the restore that a remote call needs.
     *
     * The viewer state goes on success, not optimistically: leaving clears the mute server-side
     * and the server sends nothing to say so, so a retained mute would outlive the chat it
     * belonged to and be waiting on the other side of a rejoin. A failed leave leaves it alone,
     * which is what the restore above wants — the user is still in the chat, still muted.
     */
    override suspend fun leave(chatId: ChatId): Result<Unit> {
        metadataDataSource.setMembership(chatId, isMember = false)
        return chatController.leaveChat(chatId)
            .onSuccess { metadataDataSource.clearViewerState(chatId) }
            .onFailure {
                trace(tag = TAG, message = "Leave failed for $chatId", type = TraceType.Error)
                metadataDataSource.setMembership(chatId, isMember = true)
            }
    }

    /**
     * Handles the list-level meaning of [changes], then hands them to [RosterStateHolder] for the
     * roster itself.
     *
     * Two of the changes say something about whether the chat belongs in your list at all: a join
     * carrying a full snapshot is your own join, and a departure naming you is your own removal.
     * Everything else is a roster edit.
     *
     * The list arm runs first so a self-join costs no extra round trip: persisting the snapshot
     * writes its roster version, which is exactly the version the change carries, so the holder
     * sees an already-applied change instead of a gap to refetch.
     */
    suspend fun applyRosterChanges(chatId: ChatId, changes: List<RosterChange>) {
        val selfId = userManager.accountId
        for (change in changes) {
            when (change) {
                is RosterChange.MemberJoined -> {
                    // Set only when you are the one who joined. Assigned to a local first: a smart
                    // cast will not hold across a module boundary.
                    val metadata = change.metadata ?: continue
                    persist(listOf(metadata))
                    metadataDataSource.setMembership(metadata.chatId, isMember = true)
                    _events.send(Event.LoadMessages(metadata.chatId))
                }

                is RosterChange.MemberLeft -> {
                    if (change.userId != selfId) continue
                    metadataDataSource.setMembership(chatId, isMember = false)
                    // Same reason as [leave]: the mute is gone server-side the moment you are out
                    // of the chat, whether you left from this device or another one.
                    metadataDataSource.clearViewerState(chatId)
                }
            }
        }
        rosterStateHolder.apply(chatId, changes)
    }

    private suspend fun persist(chats: List<ChatMetadata>) {
        metadataDataSource.upsert(chats)
        for (chat in chats) {
            memberDataSource.upsert(chat.chatId, chat.members)
            chat.lastMessage?.let { messageDataSource.upsert(chat.chatId, listOf(it)) }
        }
    }

    private companion object {
        const val TAG = "GroupFeedDelegate"
    }
}
