@file:OptIn(ExperimentalPagingApi::class)

package com.flipcash.shared.chat.internal.delegates

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.ChatFeedWriter
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.lastMessagesByChat
import com.flipcash.app.persistence.sources.mediator.ChatFeedRemoteMediator
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.GetMessageError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.PointerType
import com.flipcash.shared.chat.ChatArchiveStore
import com.flipcash.shared.chat.ChatFeeds
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
import com.flipcash.shared.chat.MessageLinkPrefetch
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
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
    private val linkPrefetch: MessageLinkPrefetch = MessageLinkPrefetch.None,
    private val archiveStore: ChatArchiveStore = ChatArchiveStore.None,
    private val feedWriter: ChatFeedWriter =
        ChatFeedWriter(metadataDataSource, memberDataSource, messageDataSource),
) : FeedOperations {

    companion object {
        private const val TAG = "FeedSyncDelegate"

        // The feed's rows are cheap and the merge in the mediator costs a round trip per source
        // per page, so this is larger than it would be for a transcript.
        private const val FEED_PAGE_SIZE = 20

        // The read-stamp lookups a reconcile folds in. Bounded in number and in time: the list is
        // already on screen from the cache, so a slow lookup costs one dot staying a dot for a
        // while longer, never a held-up list.
        private const val READ_STAMP_CONCURRENCY = 4
        private val READ_STAMP_WAIT = 1500.milliseconds
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

    // Stamps a reconcile fetched, parked until the feed rebuild that follows its write picks them
    // up. Applying them to state straight away would repaint the rows' counts a beat before the
    // rows themselves, as two emissions.
    private val stagedStamps = ConcurrentHashMap<Pair<ChatId, Long>, Long>()

    private fun FeedSyncState.isSettled() = this == FeedSyncState.Synced || this == FeedSyncState.Error

    // What the reconcile could not stage in time is fetched now, one lookup per stamp.
    private fun fetchLeftoverReadStamps() {
        stateHolder.current.feed?.let(::fetchMissingReadStamps)
    }

    // region FeedOperations

    override fun feed(vararg chatTypes: ChatType): Flow<List<ChatSummary>> {
        val requested = chatTypes.toSet()
        // Distinct: state also moves for reasons the list does not show (sync status, typing,
        // overlays), and an equal list repaints nothing but still re-runs every collector.
        return stateHolder.state
            .mapNotNull { state -> summaries(state, requested, archived = false) }
            .distinctUntilChanged()
    }

    override fun currentFeed(vararg chatTypes: ChatType): List<ChatSummary>? =
        summaries(stateHolder.state.value, chatTypes.toSet(), archived = false)

    override fun archivedFeed(vararg chatTypes: ChatType): Flow<List<ChatSummary>> {
        val requested = chatTypes.toSet()
        return stateHolder.state
            .mapNotNull { state -> summaries(state, requested, archived = true) }
            .distinctUntilChanged()
    }

    override fun currentArchivedFeed(vararg chatTypes: ChatType): List<ChatSummary>? =
        summaries(stateHolder.state.value, chatTypes.toSet(), archived = true)

    override fun feedWithArchived(vararg chatTypes: ChatType): Flow<ChatFeeds> {
        val requested = chatTypes.toSet()
        return stateHolder.state
            .mapNotNull { state -> feeds(state, requested) }
            .distinctUntilChanged()
    }

    override fun currentFeedWithArchived(vararg chatTypes: ChatType): ChatFeeds? =
        feeds(stateHolder.state.value, chatTypes.toSet())

    private fun feeds(state: ChatState, requested: Set<ChatType>): ChatFeeds? {
        val main = summaries(state, requested, archived = false) ?: return null
        val archived = summaries(state, requested, archived = true) ?: return null
        return ChatFeeds(main = main, archived = archived)
    }

    private fun summaries(state: ChatState, requested: Set<ChatType>, archived: Boolean): List<ChatSummary>? {
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
            // The one place archive is applied. An archived chat leaves the list, every chip and the
            // tab badge together: they all read `feed`, so none can disagree about what is archived.
            .filter { (it.chatId in state.archived) == archived }
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
            // A preview comes from `chat_messages`, which the two above do not touch. Without this
            // a row whose newest message changed without its metadata row changing (an edit, a
            // delete, a preview written a beat after the row) kept its old preview until some
            // unrelated write. Distinct, so only a change to some chat's newest visible message
            // rebuilds, not every message write; the initial value keeps a slow first read from
            // holding the list up.
            messageDataSource.observeLatestVisibleChanges()
                .onStart { emit(emptyList()) }
                .distinctUntilChanged(),
            archiveStore.observeArchived(),
        ) { metadataEntities, membersByChat, _, archived ->
            buildFeedFromDb(metadataEntities, membersByChat) to archived
        }.onEach { (built, archived) ->
            val (feed, readStamps) = built
            val staged = drainStagedStamps()
            stateHolder.update {
                it.copy(
                    feed = feed,
                    readStamps = readStamps,
                    fetchedReadStamps = if (staged.isEmpty()) it.fetchedReadStamps else it.fetchedReadStamps + staged,
                    archived = archived,
                )
            }
            // Until the first sync settles, the reconcile looks the stamps up itself, bounded and
            // folded into its write; doing it here as well would be the unbounded pop-in it avoids.
            if (stateHolder.current.feedSyncState.isSettled()) fetchMissingReadStamps(feed)
        }.launchIn(scope)
    }

    private fun drainStagedStamps(): Map<Pair<ChatId, Long>, Long> {
        if (stagedStamps.isEmpty()) return emptyMap()
        val drained = HashMap(stagedStamps)
        drained.keys.forEach { stagedStamps.remove(it) }
        return drained
    }

    /**
     * Standalone sync of the DM half. The coordinator syncs the DM and group halves together
     * through [fetchFeed] / [writeFeed] so the list is written once; this is the single-delegate
     * path. A sync already in flight is shared, not cancelled and restarted.
     */
    internal fun syncFeed() {
        val scope = scope ?: return
        if (syncJob?.isActive == true) return
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
        stagedStamps.clear()
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
                lookUpReadStamp(key)?.let { stamp ->
                    stateHolder.update { it.copy(fetchedReadStamps = it.fetchedReadStamps + (key to stamp)) }
                }
                readStampsInFlight.remove(key)
            }
        }
    }

    private suspend fun lookUpReadStamp(key: Pair<ChatId, Long>): Long? {
        val (chatId, readPointer) = key
        return messagingController.getMessage(chatId, readPointer)
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
            .getOrNull()
            ?.unreadSeq
    }

    /**
     * The reconcile's share of [fetchMissingReadStamps]: looks up, concurrently but bounded, the
     * stamps the incoming [chats] will need, and parks them in [stagedStamps] so the rebuild the
     * write triggers shows exact counts on its first paint.
     *
     * Not derivable locally: a count is `lastMessage.unreadSeq - stamp(readPointer)`, and the
     * stamp lives on the message the pointer names, which the device holds only if it ever loaded
     * that far back. The event sequence counts every event (edits, reactions), not unread
     * messages, so it cannot stand in.
     */
    private suspend fun stageReadStamps(chats: List<ChatMetadata>) {
        val selfId = userManager.accountId ?: return
        val selfPhone = userManager.profile?.verifiedPhoneNumber
        val state = stateHolder.current
        val wanted = chats.mapNotNull { chat ->
            if (!isRenderable(chat, selfId, selfPhone)) return@mapNotNull null
            val readPointer = selfReadPointer(chat, selfId) ?: return@mapNotNull null
            val key = chat.chatId to readPointer
            when {
                state.readStampAt(chat.chatId, readPointer) != null -> null
                key in readStampsNotFound -> null
                // Read, or counted without a stamp: nothing to look up.
                unreadCount(chat, selfId) { null } != null -> null
                messageDataSource.getUnreadSeq(metadataDataSource.chatIdHex(chat.chatId), readPointer) != null -> null
                else -> key
            }
        }.distinct()
        if (wanted.isEmpty()) return

        val gate = Semaphore(READ_STAMP_CONCURRENCY)
        withTimeoutOrNull(READ_STAMP_WAIT) {
            coroutineScope {
                wanted.map { key ->
                    async {
                        gate.withPermit { lookUpReadStamp(key)?.let { stagedStamps[key] = it } }
                    }
                }.awaitAll()
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

    /** Standalone DM sync: fetch, write, then the post-write steps. See [syncFeed]. */
    internal suspend fun performFeedSync() {
        fetchFeed()
            .onSuccess { chats ->
                writeFeed(chats)
                onFeedCommitted(chats)
            }
            .onFailure { onFeedFailed(it) }
    }

    /** The network half of a DM sync. Nothing is written. */
    internal suspend fun fetchFeed(): Result<List<ChatMetadata>> {
        stateHolder.update { it.copy(feedSyncState = FeedSyncState.Syncing) }
        return fetchCombinedFeed()
    }

    /**
     * Writes [chats] — whatever mix of DM and group chats the caller has in hand — in one
     * transaction, so the list rebuilds once. Link prefetch and read stamps go first: neither
     * blocks the list, and the stamps ride in with the rebuild.
     */
    internal suspend fun writeFeed(chats: List<ChatMetadata>) {
        if (chats.isEmpty()) return
        // Not waited on: a preview is one message per chat, and the feed is not held for it.
        linkPrefetch.prefetch(chats.lastMessagesByChat().values.flatten())
        stageReadStamps(chats)
        feedWriter.write(chats)
    }

    /** What follows a successful write of [chats]: the synced state and the catch-up work. */
    internal suspend fun onFeedCommitted(chats: List<ChatMetadata>) {
        stateHolder.update { it.copy(feedSyncState = FeedSyncState.Synced) }
        fetchLeftoverReadStamps()
        trace(tag = TAG, message = "Feed synced: ${chats.size} chats", type = TraceType.Process)

        val selfId = userManager.accountId

        for (chat in catchUpOrder(chats)) {
            if (selfId != null) reportUnreportedRead(chat, selfId)

            // The applied cursor, not the presence of messages, is what says whether a
            // transcript was ever pulled: the write above persists each chat's last-message
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

    internal fun onFeedFailed(error: Throwable) {
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
        fetchLeftoverReadStamps()
        trace(tag = TAG, message = "Feed sync failed: ${error.message}", type = TraceType.Error)
    }

    /**
     * Catch-up runs one chat at a time, so the order is the order the user sees things fill in:
     * the chat on screen first, then the list from the top down.
     */
    internal fun catchUpOrder(chats: List<ChatMetadata>): List<ChatMetadata> {
        val active = stateHolder.current.activeChat
        return chats.sortedWith(
            compareByDescending<ChatMetadata> { it.chatId == active }
                .thenByDescending { it.lastActivity },
        )
    }

    // endregion
}

/** The unread stamp on [messageId] in [chatId]: the stored one, else one fetched this session. */
private fun ChatState.readStampAt(chatId: ChatId, messageId: Long): Long? =
    readStamps[chatId to messageId] ?: fetchedReadStamps[chatId to messageId]
