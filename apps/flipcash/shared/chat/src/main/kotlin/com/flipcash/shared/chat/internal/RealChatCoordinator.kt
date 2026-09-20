@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package com.flipcash.shared.chat.internal

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatDraftStore
import com.flipcash.shared.chat.ChatState
import com.flipcash.shared.chat.DmChatResolver
import com.flipcash.shared.chat.EventStreamOperations
import com.flipcash.shared.chat.FeedOperations
import com.flipcash.shared.chat.GroupOperations
import com.flipcash.shared.chat.MessagingOperations
import com.flipcash.shared.chat.internal.delegates.EventStreamDelegate
import com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate
import com.flipcash.shared.chat.internal.delegates.GroupFeedDelegate
import com.flipcash.shared.chat.internal.delegates.DmChatResolverDelegate
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.providers.SessionListener
import com.getcode.utils.TraceType
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Thin orchestration shell that implements [ChatCoordinator] by composing five
 * focused delegates via Kotlin `by` interface delegation:
 *
 * | Delegate | Interface | Responsibility |
 * |----------|-----------|----------------|
 * | [FeedSyncDelegate] | [FeedOperations] | Feed sync, DB observation, unread counts |
 * | [EventStreamDelegate] | [EventStreamOperations] | Event stream, real-time updates, gap-aware sequencing, reactions, typing |
 * | [DmChatResolverDelegate] | [DmChatResolver] | Resolve a DM's [ChatId] from its participants (derive or look up) |
 * | [MessagingDelegate] | [MessagingOperations] | Per-chat send/receive, read pointers, paging, notifications |
 * | [GroupFeedDelegate] | [GroupOperations] | Group feed sync, join/leave, roster changes |
 *
 * **What lives here (and why):**
 * - **Event routing** — each delegate exposes a `Flow<Event>` (backed by a `Channel`);
 *   the `init` block collects both and dispatches cross-delegate calls (e.g.
 *   feed-delegate's `DeltaSyncNeeded` → `eventStreamDelegate.performDeltaSync`,
 *   event-stream-delegate's `SyncFeedRequested` → [syncFeeds]).
 *   All cross-delegate wiring is visible in one place.
 * - **Feed composition** — the conversation list is [FeedSyncDelegate]'s DM feeds plus
 *   [GroupFeedDelegate]'s group feed, so every refresh trigger goes through [syncFeeds]
 *   rather than either delegate directly. See [refreshFeed].
 * - **Lifecycle methods** — [onStart]/[onStop] are inherently cross-cutting
 *   (stream connect/disconnect, heartbeat start/stop, active-chat save/restore).
 * - **Flow observers** — network reconnect re-syncing the chat feed.
 *
 * Delegates require [initialize] with a shared [CoroutineScope] before use;
 * this happens in [onUserLoggedIn].
 */
@Singleton
class RealChatCoordinator @Inject constructor(
    private val feedDelegate: FeedSyncDelegate,
    private val eventStreamDelegate: EventStreamDelegate,
    private val dmChatResolverDelegate: DmChatResolverDelegate,
    private val messagingDelegate: MessagingDelegate,
    private val groupFeedDelegate: GroupFeedDelegate,
    private val stateHolder: ChatStateHolder,
    private val draftStore: ChatDraftStore,
    private val userManager: UserManager,
    private val networkObserver: NetworkConnectivityListener,
    private val dispatchers: DispatcherProvider,
) : ChatCoordinator,
    SessionListener,
    DefaultLifecycleObserver,
    FeedOperations by feedDelegate,
    EventStreamOperations by eventStreamDelegate,
    DmChatResolver by dmChatResolverDelegate,
    MessagingOperations by messagingDelegate,
    GroupOperations by groupFeedDelegate {

    companion object {
        private const val TAG = "ChatCoordinator"
    }

    // Recreated on re-login: [reset] cancels [supervisorJob] on logout, which would
    // otherwise leave the scope permanently dead for this @Singleton and no-op every
    // launch in a subsequent [onUserLoggedIn] (chats never sync until a process
    // restart rebuilds the singleton). See [onUserLoggedIn].
    private var supervisorJob = SupervisorJob()
    private var scope = CoroutineScope(dispatchers.IO + supervisorJob)
    private val cluster = MutableStateFlow<AccountCluster?>(null)
    private var networkObserverJob: Job? = null
    private var backgroundedActiveChat: ChatId? = null

    override val state: StateFlow<ChatState>
        get() = stateHolder.state

    // region SessionListener

    override suspend fun onUserLoggedIn(cluster: AccountCluster) {
        trace(tag = TAG, message = "User logged in, hydrating chat", type = TraceType.User)
        // A prior logout in this process cancels [supervisorJob] via [reset], leaving
        // the scope dead. Rebuild it and re-establish the lifetime wiring before the
        // session work below, otherwise every launch here is a silent no-op.
        if (!supervisorJob.isActive) {
            supervisorJob = SupervisorJob()
            scope = CoroutineScope(dispatchers.IO + supervisorJob)
            wireDelegateRouting()
        }
        this.cluster.value = cluster
        feedDelegate.initialize(scope)
        eventStreamDelegate.initialize(scope)
        groupFeedDelegate.initialize(scope)
        feedDelegate.observeFeedFromDb()
        syncFeeds()
        eventStreamDelegate.open()
        eventStreamDelegate.startHeartbeat { syncFeeds() }
    }

    // endregion

    // region Lifecycle

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        wireDelegateRouting()
    }

    /**
     * Launches the lifetime collectors — cross-delegate event routing and the
     * network-reconnect re-sync — onto [scope]. Called once at construction and
     * again from [onUserLoggedIn] whenever the scope had to be rebuilt after a
     * [reset], so these collectors are never left orphaned on a dead scope.
     */
    private fun wireDelegateRouting() {
        feedDelegate.events
            .onEach { event ->
                when (event) {
                    is FeedSyncDelegate.Event.LoadMessages ->
                        messagingDelegate.loadMessages(event.chatId)
                    is FeedSyncDelegate.Event.DeltaSyncNeeded ->
                        eventStreamDelegate.performDeltaSync(event.chatId)
                    is FeedSyncDelegate.Event.ReadPointerUnreported ->
                        messagingDelegate.reportReadPointer(event.chatId, event.messageId)
                    // Arrives after every catch-up item above it, because this is one sequential
                    // collector over a FIFO channel. Anything reading chat history as evidence —
                    // the wallet's "send a tip" milestone — waits for this rather than for the
                    // feed sync, which reports itself synced before the backfill is scheduled.
                    FeedSyncDelegate.Event.CatchUpComplete ->
                        feedDelegate.markHistoryHydrated()
                }
            }.launchIn(scope)

        eventStreamDelegate.events
            .onEach { event ->
                when (event) {
                    is EventStreamDelegate.Event.SyncFeedRequested ->
                        syncFeeds()
                    is EventStreamDelegate.Event.LoadMessages ->
                        messagingDelegate.loadMessages(event.chatId)
                    is EventStreamDelegate.Event.RosterChanged ->
                        groupFeedDelegate.applyRosterChanges(event.chatId, event.changes)
                }
            }.launchIn(scope)

        groupFeedDelegate.events
            .onEach { event ->
                when (event) {
                    is GroupFeedDelegate.Event.LoadMessages ->
                        messagingDelegate.loadMessages(event.chatId)
                    is GroupFeedDelegate.Event.DeltaSyncNeeded ->
                        eventStreamDelegate.performDeltaSync(event.chatId)
                }
            }.launchIn(scope)

        networkObserverJob = cluster.filterNotNull()
            .flatMapLatest { networkObserver.state }
            .distinctUntilChanged()
            .filter { it.connected }
            .debounce(1.seconds)
            .onEach {
                trace(tag = TAG, message = "Network connected, re-syncing chat feed", type = TraceType.Process)
                syncFeeds()
                eventStreamDelegate.open()
            }
            .launchIn(scope)
    }

    override fun onStart(owner: LifecycleOwner) {
        backgroundedActiveChat?.let {
            messagingDelegate.setActiveChatId(it)
            backgroundedActiveChat = null
        }
        scope.launch {
            if (cluster.value != null) {
                trace(tag = TAG, message = "Lifecycle resumed, syncing chat feed", type = TraceType.Process)
                syncFeeds()
                eventStreamDelegate.open()
                eventStreamDelegate.startHeartbeat { syncFeeds() }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedActiveChat = stateHolder.current.activeChat
        messagingDelegate.setActiveChatId(null)
        eventStreamDelegate.stopHeartbeat()
        eventStreamDelegate.close()
    }

    // endregion

    // region ChatCoordinator

    /**
     * Overrides the [FeedOperations] delegation, which would otherwise refresh the DM half alone.
     *
     * The callers — a chat push, a contact payment, a tip payment — are asking for the
     * conversation list, and none of them know which half a chat belongs to.
     */
    override fun refreshFeed() {
        syncFeeds()
    }

    /**
     * Overrides the [FeedOperations] delegation to drop the chat's draft when it is being hidden.
     *
     * Blocking someone is the caller here, and it removes the composer along with the reason to
     * keep what was half-typed into it. Unhiding does not put a draft back — there is nothing to
     * put back — so only the hiding direction clears.
     */
    override suspend fun setChatHidden(chatId: ChatId, hidden: Boolean) {
        feedDelegate.setChatHidden(chatId, hidden)
        if (hidden) draftStore.clear(chatId)
    }

    /**
     * Overrides the [GroupOperations] delegation to drop the group's draft once leaving succeeds.
     *
     * Only on success: a failed leave leaves you in the group with the composer still there, and
     * the draft is what it was.
     */
    override suspend fun leave(chatId: ChatId): Result<Unit> =
        groupFeedDelegate.leave(chatId).onSuccess { draftStore.clear(chatId) }

    /**
     * Fetches both halves of the conversation list.
     *
     * The list is two feeds behind one surface: [FeedSyncDelegate] fetches `CONTACT_DM` and
     * `TIP_DM`, and a group's row comes from [GroupFeedDelegate] alone. Every trigger that
     * re-syncs means "the list may be stale", which is never true of only one half, so pairing
     * them is this class's job rather than something each trigger site remembers.
     *
     * Both are launch-and-return, and the delegates hold separate jobs, so the two fetches
     * overlap rather than queue. A group feed that fails is traced and dropped by
     * [GroupFeedDelegate.performGroupFeedSync]; it cannot take the DM list down with it.
     */
    private fun syncFeeds() {
        feedDelegate.syncFeed()
        groupFeedDelegate.syncGroupFeed()
    }

    override suspend fun teardown() {
        eventStreamDelegate.stopHeartbeat()
        eventStreamDelegate.close()
        feedDelegate.cancelJobs()
        groupFeedDelegate.cancelJobs()
        networkObserverJob?.cancel()
        stateHolder.reset()
        eventStreamDelegate.clearAll()
        // Before the scope dies: a draft is the one thing here that was never on the server, so
        // logout is the only chance to drop it.
        draftStore.clearAll()
        cluster.value = null
        supervisorJob.cancel()
        trace(tag = TAG, message = "teardown complete", type = TraceType.Process)
    }

    override suspend fun clearCache() {
        messagingDelegate.clear()
        trace(tag = TAG, message = "cache cleared", type = TraceType.Process)
    }

    // endregion

}
