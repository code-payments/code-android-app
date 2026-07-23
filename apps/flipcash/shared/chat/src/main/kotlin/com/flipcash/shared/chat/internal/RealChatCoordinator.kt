@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package com.flipcash.shared.chat.internal

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatState
import com.flipcash.shared.chat.DmChatResolver
import com.flipcash.shared.chat.EventStreamOperations
import com.flipcash.shared.chat.FeedOperations
import com.flipcash.shared.chat.MessagingOperations
import com.flipcash.shared.chat.internal.delegates.EventStreamDelegate
import com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Thin orchestration shell that implements [ChatCoordinator] by composing three
 * focused delegates via Kotlin `by` interface delegation:
 *
 * | Delegate | Interface | Responsibility |
 * |----------|-----------|----------------|
 * | [FeedSyncDelegate] | [FeedOperations] | Feed sync, DB observation, unread counts |
 * | [EventStreamDelegate] | [EventStreamOperations] | Event stream, real-time updates, gap-aware sequencing, reactions, typing |
 * | [DmChatResolverDelegate] | [DmChatResolver] | Resolve a DM's [ChatId] from its participants (derive or look up) |
 * | [MessagingDelegate] | [MessagingOperations] | Per-chat send/receive, read pointers, paging, notifications |
 *
 * **What lives here (and why):**
 * - **Event routing** — each delegate exposes a `Flow<Event>` (backed by a `Channel`);
 *   the `init` block collects both and dispatches cross-delegate calls (e.g.
 *   feed-delegate's `DeltaSyncNeeded` → `eventStreamDelegate.performDeltaSync`,
 *   event-stream-delegate's `SyncFeedRequested` → `feedDelegate.syncFeed`).
 *   All cross-delegate wiring is visible in one place.
 * - **Lifecycle methods** — [onStart]/[onStop] are inherently cross-cutting
 *   (stream connect/disconnect, heartbeat start/stop, active-chat save/restore).
 * - **Flow observers** — network reconnect and feature-flag transitions that
 *   gate whether the chat subsystem should be active.
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
    private val stateHolder: ChatStateHolder,
    private val userManager: UserManager,
    private val featureFlags: FeatureFlagController,
    private val networkObserver: NetworkConnectivityListener,
    private val dispatchers: DispatcherProvider,
) : ChatCoordinator,
    SessionListener,
    DefaultLifecycleObserver,
    FeedOperations by feedDelegate,
    EventStreamOperations by eventStreamDelegate,
    DmChatResolver by dmChatResolverDelegate,
    MessagingOperations by messagingDelegate {

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
    private var flagObserverJob: Job? = null
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
        feedDelegate.observeFeedFromDb()
        feedDelegate.syncFeed()
        eventStreamDelegate.open()
        eventStreamDelegate.startHeartbeat { feedDelegate.syncFeed() }
        observeFeatureFlag()
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
                }
            }.launchIn(scope)

        eventStreamDelegate.events
            .onEach { event ->
                when (event) {
                    is EventStreamDelegate.Event.SyncFeedRequested ->
                        feedDelegate.syncFeed()
                    is EventStreamDelegate.Event.LoadMessages ->
                        messagingDelegate.loadMessages(event.chatId)
                }
            }.launchIn(scope)

        networkObserverJob = cluster.filterNotNull()
            .flatMapLatest { networkObserver.state }
            .distinctUntilChanged()
            .filter { it.connected }
            .debounce(1.seconds)
            .onEach {
                if (!isChatEnabled()) return@onEach
                trace(tag = TAG, message = "Network connected, re-syncing chat feed", type = TraceType.Process)
                feedDelegate.syncFeed()
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
            if (cluster.value != null && isChatEnabled()) {
                trace(tag = TAG, message = "Lifecycle resumed, syncing chat feed", type = TraceType.Process)
                feedDelegate.syncFeed()
                eventStreamDelegate.open()
                eventStreamDelegate.startHeartbeat { feedDelegate.syncFeed() }
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

    override suspend fun reset() {
        eventStreamDelegate.stopHeartbeat()
        eventStreamDelegate.close()
        feedDelegate.cancelJobs()
        flagObserverJob?.cancel()
        networkObserverJob?.cancel()
        stateHolder.reset()
        eventStreamDelegate.clearAll()
        cluster.value = null
        messagingDelegate.clear()
        supervisorJob.cancel()
        trace(tag = TAG, message = "reset complete", type = TraceType.Process)
    }

    // endregion

    // region Internal

    private suspend fun isChatEnabled(): Boolean {
        val featureFlag = featureFlags.get(FeatureFlag.PhoneNumberSend)
        val serverFlag = userManager.state.value.flags?.enablePhoneNumberSend == true
        return featureFlag || serverFlag
    }

    private fun observeFeatureFlag() {
        flagObserverJob?.cancel()
        flagObserverJob = combine(
            featureFlags.observe(FeatureFlag.PhoneNumberSend),
            userManager.state.map { it.flags?.enablePhoneNumberSend == true },
        ) { featureFlag, serverFlag -> featureFlag || serverFlag }
            .distinctUntilChanged()
            .filter { it }
            .onEach {
                if (cluster.value != null) {
                    trace(tag = TAG, message = "Chat feature enabled, syncing feed", type = TraceType.Process)
                    feedDelegate.syncFeed()
                    eventStreamDelegate.open()
                    eventStreamDelegate.startHeartbeat { feedDelegate.syncFeed() }
                }
            }
            .launchIn(scope)
    }

    // endregion
}
