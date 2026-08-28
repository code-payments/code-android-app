package com.flipcash.shared.chat.internal.delegates

import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.isDmAddressable
import com.flipcash.shared.chat.ChatHydrationState
import com.flipcash.shared.chat.ChatSummary
import com.flipcash.shared.chat.FeedOperations
import com.flipcash.shared.chat.FeedSyncState
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the chat feed: syncing conversation metadata from the server, observing
 * the local Room database, and projecting [ChatSummary] items with unread counts.
 *
 * **Cross-delegate communication:** After a feed sync, this delegate may discover
 * chats that need their message history loaded or their event sequence caught up.
 * Rather than calling other delegates directly, it emits [Event.LoadMessages] or
 * [Event.DeltaSyncNeeded] on [events], which [RealChatCoordinator] routes to the
 * appropriate delegate.
 *
 * Requires [initialize] with a [CoroutineScope] before any work can be launched.
 *
 * @see com.flipcash.shared.chat.internal.RealChatCoordinator
 */
@Singleton
class FeedSyncDelegate @Inject constructor(
    private val chatController: ChatController,
    private val metadataDataSource: ChatMetadataDataSource,
    private val messageDataSource: ChatMessageDataSource,
    private val memberDataSource: ChatMemberDataSource,
    private val stateHolder: ChatStateHolder,
    private val userManager: UserManager,
) : FeedOperations {

    companion object {
        private const val TAG = "FeedSyncDelegate"
    }

    sealed interface Event {
        data class LoadMessages(val chatId: ChatId) : Event
        /**
         * The chat's applied cursor is behind the feed's head. The consumer reads that cursor from
         * `chat_metadata` itself: a feed sync never writes it (see `ChatMetadataDao.upsert`), so
         * the row still holds what the client has actually applied.
         */
        data class DeltaSyncNeeded(val chatId: ChatId) : Event

        /**
         * The client's own READ pointer for [chatId] is ahead of the copy the feed just returned,
         * so the server never took the advance. The consumer re-reports [messageId].
         */
        data class ReadPointerUnreported(val chatId: ChatId, val messageId: Long) : Event

        /**
         * Emitted last by every successful sync, after any catch-up above it.
         *
         * [events] is a FIFO channel routed by a single sequential collector in
         * [RealChatCoordinator][com.flipcash.shared.chat.internal.RealChatCoordinator], so by the
         * time this is handled every catch-up item ahead of it has finished its suspend call and
         * committed its writes. That ordering is the whole reason it exists: it is what makes
         * [markHistoryHydrated] safe to call, and it cannot be replaced by watching
         * [FeedSyncState][com.flipcash.shared.chat.FeedSyncState], which flips to `Synced` before
         * the catch-up is even scheduled.
         */
        data object CatchUpComplete : Event
    }

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private var scope: CoroutineScope? = null
    private var syncJob: Job? = null
    private var feedObserverJob: Job? = null

    // region FeedOperations

    override fun feed(chatType: ChatType): Flow<List<ChatSummary>> =
        stateHolder.state.map { state ->
            val selfId = userManager.accountId
            val selfPhone = userManager.profile?.verifiedPhoneNumber
            val isSelf = { member: ChatMember ->
                member.userId == selfId || (selfPhone != null && member.userProfile.verifiedPhoneNumber == selfPhone)
            }
            state.feed
                .filter { it.type == chatType }
                // Hidden chats (e.g. a DM the user blocked) must not surface in the feed.
                .filter { !it.isHidden }
                .mapNotNull { metadata ->
                    val otherMember = metadata.members.firstOrNull { !isSelf(it) }
                        ?: return@mapNotNull null

                    // Contact DMs require a resolvable identity (phone / display name). Tip DMs are
                    // identified by user id and have no phone by design, so they are never dropped.
                    if (!isDmAddressable(chatType, otherMember.userProfile)) return@mapNotNull null

                    val readPointer = metadata.members
                        .firstOrNull { it.userId == selfId }
                        ?.pointers
                        ?.firstOrNull { it.type == PointerType.READ }
                        ?.value ?: 0L

                    val unreadCount = metadata.lastMessage?.let { lastMsg ->
                        if (lastMsg.messageId > readPointer && lastMsg.senderId != selfId) 1 else 0
                    } ?: 0

                    ChatSummary(metadata = metadata, unreadCount = unreadCount)
                }
        }

    override fun observeUnreadConversations(chatType: ChatType): Flow<Int> {
        return feed(chatType).map { summaries -> summaries.count { it.unreadCount > 0 } }
    }

    override fun refreshFeed() {
        syncFeed()
    }

    override suspend fun setChatHidden(chatId: ChatId, hidden: Boolean) {
        // Persist the hidden flag; observeFeedFromDb re-emits off the Room change, so the feed
        // (filtered by isHidden) updates live without a server round-trip.
        metadataDataSource.setHidden(chatId, hidden = hidden)
    }

    // endregion

    // region Internal

    internal fun initialize(scope: CoroutineScope) {
        this.scope = scope
    }

    internal fun observeFeedFromDb() {
        val scope = scope ?: return
        feedObserverJob?.cancel()
        feedObserverJob = combine(
            metadataDataSource.observeAll(),
            memberDataSource.observeAll(),
        ) { metadataEntities, membersByChat ->
            buildFeedFromDb(metadataEntities, membersByChat)
        }.onEach { feed ->
            stateHolder.update { it.copy(feed = feed) }
        }.launchIn(scope)
    }

    internal fun syncFeed() {
        val scope = scope ?: return
        syncJob?.cancel()
        syncJob = scope.launch { performFeedSync() }
    }

    /**
     * Marks the message cache reconciled with the server.
     *
     * Called by the coordinator when it routes [Event.CatchUpComplete], not by the sync itself —
     * the sync only *schedules* the backfill, and hydration is about that backfill having run.
     */
    internal fun markHistoryHydrated() {
        stateHolder.update { it.copy(historyHydration = ChatHydrationState.Hydrated) }
    }

    internal fun cancelJobs() {
        syncJob?.cancel()
        feedObserverJob?.cancel()
        feedObserverJob = null
    }

    /**
     * Emits [Event.ReadPointerUnreported] when the stored READ pointer for [chat] is ahead of the
     * one the feed just returned.
     *
     * A read is written locally the moment the message is on screen and reported to the server
     * afterwards, and nothing retries a report that fails. The local pointer survives — the member
     * row keeps whichever value is further ahead — so the two copies can disagree indefinitely,
     * leaving every other device and the pushes this one receives treating the chat as unread. A
     * feed payload is the server's own copy, so the sync is where they can be compared.
     */
    private suspend fun reportUnreportedRead(chat: ChatMetadata, selfId: ID) {
        val server = chat.members
            .firstOrNull { it.userId == selfId }
            ?.pointers
            ?.firstOrNull { it.type == PointerType.READ }
            ?.value
            ?: 0L

        // Read after the merge above, so this is max(local, server): ahead of `server` only when
        // a local advance never reached it.
        val local = memberDataSource.getSelfReadPointer(chat.chatId, selfId)
        if (local > server) {
            _events.send(Event.ReadPointerUnreported(chat.chatId, local))
        }
    }

    private suspend fun buildFeedFromDb(
        metadataEntities: List<ChatMetadataEntity>,
        membersByChat: Map<String, List<ChatMember>>,
    ): List<ChatMetadata> {
        return metadataEntities.map { entity ->
            val members = membersByChat[entity.chatIdHex] ?: emptyList()
            val lastMessage = entity.lastMessageId?.let {
                messageDataSource.getLatest(entity.chatIdHex)
            }
            metadataDataSource.toMetadata(entity, members, lastMessage)
        }
    }

    /**
     * Fetches the CONTACT_DM and TIP_DM feeds and returns their merged chats. The contact feed is
     * required (its failure fails the whole sync, preserving prior behaviour); a TIP_DM failure is
     * tolerated so tips never break the main DM list. Each chat carries its own [ChatType].
     */
    internal suspend fun fetchCombinedFeed(): Result<List<ChatMetadata>> = coroutineScope {
        // Fetch both feeds concurrently — total time is the slower of the two, not their sum.
        val contactDeferred = async { chatController.getDmChatFeed(ChatType.CONTACT_DM) }
        val tipDeferred = async { chatController.getDmChatFeed(ChatType.TIP_DM) }

        // Contact feed is required (its failure fails the whole sync); a TIP_DM failure is tolerated.
        val contact = contactDeferred.await().getOrElse { return@coroutineScope Result.failure(it) }
        val tip = tipDeferred.await().getOrNull()
        Result.success(contact.chats + (tip?.chats ?: emptyList()))
    }

    internal suspend fun performFeedSync() {
        stateHolder.update { it.copy(feedSyncState = FeedSyncState.Syncing) }
        fetchCombinedFeed()
            .onSuccess { chats ->
                metadataDataSource.upsert(chats)

                for (chat in chats) {
                    memberDataSource.upsert(chat.chatId, chat.members)
                    chat.lastMessage?.let { msg ->
                        messageDataSource.upsert(chat.chatId, listOf(msg))
                    }
                }

                stateHolder.update { it.copy(feedSyncState = FeedSyncState.Synced) }
                trace(tag = TAG, message = "Feed synced: ${chats.size} chats", type = TraceType.Process)

                val selfId = userManager.accountId

                for (chat in chats) {
                    if (selfId != null) reportUnreportedRead(chat, selfId)

                    // The applied cursor, not the presence of messages, is what says whether a
                    // transcript was ever pulled: the loop above persists each chat's last-message
                    // preview, so "has messages" is true for nearly every chat in a feed the client
                    // has otherwise never fetched. Only a message load or an applied delta seats a
                    // cursor.
                    val cursor = metadataDataSource.getLatestEventSequence(chat.chatId)
                    when {
                        // Never fetched: take the newest page. Resuming a delta from 0 would instead
                        // re-pull the entire history as a "gap".
                        cursor <= 0L -> _events.send(Event.LoadMessages(chat.chatId))
                        // Fetched, but the server has moved on: stream the missed window.
                        chat.latestEventSequence > cursor ->
                            _events.send(Event.DeltaSyncNeeded(chat.chatId))
                    }
                }

                _events.send(Event.CatchUpComplete)
            }
            .onFailure { error ->
                stateHolder.update { state ->
                    state.copy(
                        feedSyncState = FeedSyncState.Error,
                        // Don't downgrade a hydration that already succeeded: a later failure means
                        // this sync missed, not that the cache stopped being trustworthy. Moving off
                        // Unknown at all matters though — callers waiting on hydration have to stop
                        // waiting when the server is unreachable, or the wallet spins forever
                        // offline.
                        historyHydration = if (state.historyHydration == ChatHydrationState.Unknown) {
                            ChatHydrationState.Unavailable
                        } else {
                            state.historyHydration
                        },
                    )
                }
                trace(tag = TAG, message = "Feed sync failed: ${error.message}", type = TraceType.Error)
            }
    }

    // endregion
}
