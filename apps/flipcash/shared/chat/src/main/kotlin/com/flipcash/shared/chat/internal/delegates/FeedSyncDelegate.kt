@file:OptIn(ExperimentalPagingApi::class)

package com.flipcash.shared.chat.internal.delegates

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.mediator.ChatFeedRemoteMediator
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.GetMessageError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.PointerType
import com.flipcash.shared.chat.ChatHydrationState
import com.flipcash.shared.chat.ChatSummary
import com.flipcash.shared.chat.FeedOperations
import com.flipcash.shared.chat.FeedSyncState
import com.flipcash.shared.chat.ChatState
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.isRenderable
import com.flipcash.shared.chat.internal.selfReadPointer
import com.flipcash.shared.chat.internal.unreadCount
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
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
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
    private val messagingController: ChatMessagingController,
) : FeedOperations {

    companion object {
        private const val TAG = "FeedSyncDelegate"

        // The feed's rows are cheap and the merge in the mediator costs a round trip per source
        // per page, so this is larger than it would be for a transcript.
        private const val FEED_PAGE_SIZE = 20
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
    private val readStampsInFlight = ConcurrentHashMap.newKeySet<Pair<ChatId, Long>>()
    private val readStampsNotFound = ConcurrentHashMap.newKeySet<Pair<ChatId, Long>>()
    private var syncJob: Job? = null
    private var feedObserverJob: Job? = null

    // region FeedOperations

    override fun feed(vararg chatTypes: ChatType): Flow<List<ChatSummary>> {
        val requested = chatTypes.toSet()
        return stateHolder.state.mapNotNull { state -> summaries(state, requested) }
    }

    override fun currentFeed(vararg chatTypes: ChatType): List<ChatSummary>? =
        summaries(stateHolder.state.value, chatTypes.toSet())

    private fun summaries(state: ChatState, requested: Set<ChatType>): List<ChatSummary>? {
        // Nothing until the list is known: the chat list shows its empty state for an emitted empty
        // list, so it must not see one that only means "not read yet". Chats on disk are known as
        // soon as they are read. An empty database is not, because on a fresh sign-in it is empty
        // only until the first sync writes the account's chats, so it waits for that sync to answer.
        val feed = state.feed?.takeIf { feed ->
            feed.isNotEmpty() ||
                state.feedSyncState == FeedSyncState.Synced ||
                state.feedSyncState == FeedSyncState.Error
        } ?: return null
        val selfId = userManager.accountId
        val selfPhone = userManager.profile?.verifiedPhoneNumber
        return feed
            .filter { it.type in requested }
            .filter { isRenderable(it, selfId, selfPhone) }
            .map { metadata ->
                val count = unreadCount(metadata, selfId) { id -> state.readStampAt(metadata.chatId, id) }
                ChatSummary(metadata = metadata, unreadCount = count)
            }
    }

    override fun observeUnreadConversations(vararg chatTypes: ChatType): Flow<Int> {
        return feed(*chatTypes).map { summaries -> summaries.count { it.unreadCount != 0 } }
    }

    override fun feedPaged(vararg chatTypes: ChatType): Flow<PagingData<ChatSummary>> {
        val types = chatTypes.toList()
        return Pager(
            config = PagingConfig(pageSize = FEED_PAGE_SIZE),
            remoteMediator = ChatFeedRemoteMediator(
                chatTypes = types,
                controller = chatController,
                metadataDataSource = metadataDataSource,
                memberDataSource = memberDataSource,
                messageDataSource = messageDataSource,
            ),
        ) {
            metadataDataSource.observeFeedPaged(types)
        }.flow.map { page ->
            val selfId = userManager.accountId
            val selfPhone = userManager.profile?.verifiedPhoneNumber
            page
                .map { entity -> toSummary(entity, selfId) }
                // After the map, not before: the rules read a ChatMetadata, and PagingData carries
                // entities until this point.
                .filter { isRenderable(it.metadata, selfId, selfPhone) }
        }
    }

    /**
     * Rebuilds one row into a [ChatSummary], the same way [buildFeedFromDb] rebuilds the whole
     * list — the members and the newest visible message are separate reads because the row holds
     * neither.
     */
    private suspend fun toSummary(entity: ChatMetadataEntity, selfId: ID?): ChatSummary {
        val members = memberDataSource.getMembersForChat(entity.chatIdHex)
        val lastMessage = entity.lastMessageId?.let { messageDataSource.getLatestVisible(entity.chatIdHex) }
        val metadata = metadataDataSource.toMetadata(entity, members, lastMessage)
        val readPointer = selfReadPointer(metadata, selfId)
        val readStamp = readPointer?.let {
            messageDataSource.getUnreadSeq(entity.chatIdHex, it)
                ?: stateHolder.state.value.fetchedReadStamps[metadata.chatId to it]
        }
        val count = unreadCount(metadata, selfId) { id -> readStamp.takeIf { id == readPointer } }
        return ChatSummary(metadata = metadata, unreadCount = count)
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
        }.onEach { (feed, readStamps) ->
            stateHolder.update { it.copy(feed = feed, readStamps = readStamps) }
            fetchMissingReadStamps(feed)
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
        readStampsInFlight.clear()
        readStampsNotFound.clear()
    }

    /**
     * Fetches the unread stamp on the message each unread chat's READ pointer names, where the
     * device doesn't store that message, so the row can show an exact count rather than a dot.
     *
     * One request per key at a time. A NOT_FOUND key isn't asked for again this session and keeps
     * the dot; any other failure leaves the key unresolved, so the next feed build retries it.
     */
    private fun fetchMissingReadStamps(feed: List<ChatMetadata>) {
        val scope = scope ?: return
        val state = stateHolder.state.value
        val selfId = userManager.accountId
        val selfPhone = userManager.profile?.verifiedPhoneNumber
        for (metadata in feed) {
            if (!isRenderable(metadata, selfId, selfPhone)) continue
            val readPointer = selfReadPointer(metadata, selfId) ?: continue
            val key = metadata.chatId to readPointer
            if (state.readStampAt(metadata.chatId, readPointer) != null) continue
            if (key in readStampsNotFound) continue
            // With no stamp to look up, only a chat that is unread by its ids counts as unknown;
            // a read chat needs no stamp.
            if (unreadCount(metadata, selfId) { null } != null) continue
            if (!readStampsInFlight.add(key)) continue

            scope.launch {
                messagingController.getMessage(metadata.chatId, readPointer)
                    .onSuccess { message ->
                        stateHolder.update {
                            it.copy(fetchedReadStamps = it.fetchedReadStamps + (key to message.unreadSeq))
                        }
                    }
                    .onFailure { cause ->
                        if (cause is GetMessageError.NotFound) readStampsNotFound.add(key)
                        val expected = cause is GetMessageError.NotFound || cause is GetMessageError.Denied
                        trace(
                            tag = TAG,
                            message = "Fetching the read pointer's message $readPointer failed",
                            error = cause,
                            // Error routes through ErrorUtils, which drops transport failures.
                            type = if (expected) TraceType.Log else TraceType.Error,
                        )
                    }
                readStampsInFlight.remove(key)
            }
        }
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
    ): Pair<List<ChatMetadata>, Map<Pair<ChatId, Long>, Long>> {
        // `is_member` is a column rather than a field on ChatMetadata: a chat you have left is
        // still a chat you can be shown (Plan C's gate reads the same row), so the flag is dropped
        // here, at the edge of the list, rather than carried through the domain model.
        val latestVisible = messageDataSource.getLatestVisibleByChat()
        val selfId = userManager.accountId
        val readStamps = mutableMapOf<Pair<ChatId, Long>, Long>()
        val feed = metadataEntities.filter { it.isMember }.map { entity ->
            val members = membersByChat[entity.chatIdHex] ?: emptyList()
            // Deliberately the newest *visible* message, not the newest row: deleting the newest
            // message drops the feed back to the one before it, so the preview reads that message
            // instead of "Message deleted" and its unread splat clears with it (the fallback sits
            // at or below the read pointer whenever the deleted message was the only unread one).
            val lastMessage = entity.lastMessageId?.let { latestVisible[entity.chatIdHex] }
            metadataDataSource.toMetadata(entity, members, lastMessage).also { metadata ->
                // Only the stamp a row can ask for: the one on the message the READ pointer names.
                val readPointer = selfReadPointer(metadata, selfId) ?: return@also
                messageDataSource.getUnreadSeq(entity.chatIdHex, readPointer)
                    ?.let { readStamps[metadata.chatId to readPointer] = it }
            }
        }
        return feed to readStamps
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

/** The unread stamp on [messageId] in [chatId]: the stored one, else one fetched this session. */
private fun ChatState.readStampAt(chatId: ChatId, messageId: Long): Long? =
    readStamps[chatId to messageId] ?: fetchedReadStamps[chatId to messageId]
