@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)

package com.flipcash.shared.chat

import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.mediator.ChatMessageRemoteMediator
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.ContactDataSource
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.controllers.EventStreamingController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.models.chat.EmojiReaction
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MetadataUpdate
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.ReactionSummary
import com.flipcash.services.models.chat.ReactionUpdate
import com.flipcash.services.models.chat.TypingNotification
import com.flipcash.services.models.chat.TypingState
import com.flipcash.services.models.GetDeltaError
import com.flipcash.services.repository.DeltaUpdate
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.providers.SessionListener
import com.getcode.utils.TraceType
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.decodeBase58
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Singleton
class ChatCoordinator @Inject constructor(
    private val chatController: ChatController,
    private val messagingController: ChatMessagingController,
    private val eventStreamingController: EventStreamingController,
    private val metadataDataSource: ChatMetadataDataSource,
    private val messageDataSource: ChatMessageDataSource,
    private val memberDataSource: ChatMemberDataSource,
    private val contactDataSource: ContactDataSource,
    private val networkObserver: NetworkConnectivityListener,
    private val notificationManager: NotificationManagerCompat,
    private val userManager: UserManager,
    private val tokenCoordinator: TokenCoordinator,
    private val featureFlags: FeatureFlagController,
    private val dispatchers: DispatcherProvider,
) : SessionListener, DefaultLifecycleObserver {

    companion object {
        private const val TAG = "ChatCoordinator"
        private val HEARTBEAT_INTERVAL = 30.seconds
    }

    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(dispatchers.IO + supervisorJob)
    private val cluster = MutableStateFlow<AccountCluster?>(null)
    private val _state = MutableStateFlow(ChatState())
    private var syncJob: Job? = null
    private var flagObserverJob: Job? = null
    private var eventStreamCollectJob: Job? = null
    private var feedObserverJob: Job? = null
    private var heartbeatJob: Job? = null
    private var networkObserverJob: Job? = null
    private var backgroundedActiveChat: ChatId? = null

    val state: StateFlow<ChatState>
        get() = _state.asStateFlow()

    val feed: Flow<List<ChatSummary>>
        get() = _state.map { state ->
            val selfId = userManager.accountId
            state.feed.mapNotNull { metadata ->
                // Filter out anonymous chats (DMs where the other member has no name or phone)
                val otherMember = metadata.members.firstOrNull { it.userId != selfId }
                if (otherMember != null) {
                    val profile = otherMember.userProfile
                    val hasIdentity = !profile.displayName.isNullOrBlank() ||
                        !profile.verifiedPhoneNumber.isNullOrBlank()
                    if (!hasIdentity) return@mapNotNull null
                }

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

    // region SessionListener

    override suspend fun onUserLoggedIn(cluster: AccountCluster) {
        trace(tag = TAG, message = "User logged in, hydrating chat", type = TraceType.User)
        this.cluster.value = cluster
        observeFeedFromDb()
        syncFeed()
        openEventStream()
        startHeartbeat()
        observeFeatureFlag()
    }

    // endregion

    // region Lifecycle

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        networkObserverJob = cluster.filterNotNull()
            .flatMapLatest { networkObserver.state }
            .distinctUntilChanged()
            .filter { it.connected }
            .debounce(1.seconds)
            .onEach {
                if (!isChatEnabled()) return@onEach
                trace(tag = TAG, message = "Network connected, re-syncing chat feed", type = TraceType.Process)
                syncFeed()
                openEventStream()
            }
            .launchIn(scope)
    }

    override fun onStart(owner: LifecycleOwner) {
        backgroundedActiveChat?.let {
            setActiveChatId(it)
            backgroundedActiveChat = null
        }
        scope.launch {
            if (cluster.value != null && isChatEnabled()) {
                trace(tag = TAG, message = "Lifecycle resumed, syncing chat feed", type = TraceType.Process)
                syncFeed()
                openEventStream()
                startHeartbeat()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedActiveChat = _state.value.activeChat
        setActiveChatId(null)
        stopHeartbeat()
        closeEventStream()
    }

    // endregion

    // region Public API

    suspend fun getChatId(contact: DeviceContact): Result<ChatId> {
        val raw = contactDataSource.getDmChatId(contact.e164)
        if (raw.isNullOrEmpty()) {
            return Result.failure(NoDmChatInitializedException(contact.e164))
        }
        return runCatching { ChatId(raw.decodeBase58()) }
    }

    fun observeUnreadConversations(): Flow<Int> {
        return feed.map { summaries -> summaries.count { it.unreadCount > 0 } }
    }

    fun observeMessages(chatId: ChatId): Flow<List<ChatMessage>> {
        return messageDataSource.observeMessages(chatId)
    }

    fun observeMessagesPaged(chatId: ChatId): Flow<PagingData<ChatMessage>> {
        return Pager(
            config = PagingConfig(pageSize = 50),
            remoteMediator = ChatMessageRemoteMediator(chatId, messagingController, messageDataSource),
        ) {
            messageDataSource.observeForChat(chatId)
        }.flow.map { page ->
            page.map { entity -> messageDataSource.toChatMessage(entity) }
        }
    }

    fun observeTypingIndicators(chatId: ChatId): Flow<Set<ActiveTypist>> {
        return _state.map { it.typingIndicators[chatId] ?: emptySet() }
    }

    fun observeMembers(chatId: ChatId): Flow<List<ChatMember>> {
        return memberDataSource.observeMembers(chatId)
    }

    fun observeOtherReadPointer(chatId: ChatId): Flow<MessagePointer?> {
        val selfId = userManager.accountId
        return memberDataSource.observeMembers(chatId)
            .map { members ->
                members.firstOrNull { it.userId != selfId }
                    ?.pointers
                    ?.firstOrNull { it.type == PointerType.READ }
            }
            .distinctUntilChanged()
    }

    suspend fun loadMessages(chatId: ChatId) {
        messagingController.getMessages(chatId)
            .onSuccess { messages ->
                messageDataSource.upsert(chatId, messages)

                val latest = messages.maxByOrNull { it.messageId } ?: return@onSuccess
                metadataDataSource.updateLastMessageId(chatId, latest.messageId)
                metadataDataSource.updateLastActivity(chatId, latest.timestamp.toEpochMilliseconds())
            }
    }

    suspend fun sendMessage(chatId: ChatId, content: String): Result<ChatMessage> {
        val senderId = userManager.accountId
            ?: return Result.failure(IllegalStateException("Cannot send message without an account"))

        val content = listOf(MessageContent.Text(content))
        val (_, clientMessageId) = messageDataSource.insertPending(
            chatId = chatId,
            content = content,
            senderId = senderId,
        )

        return messagingController.sendMessage(chatId, content, clientMessageId)
            .onSuccess { serverMessage ->
                messageDataSource.confirmPending(chatId, clientMessageId, serverMessage)
                advanceReadPointer(chatId, serverMessage.messageId)

                // Update feed metadata — reactive flow picks up the change
                metadataDataSource.updateLastMessageId(chatId, serverMessage.messageId)
                metadataDataSource.updateLastActivity(chatId, serverMessage.timestamp.toEpochMilliseconds())
            }
            .onFailure {
                messageDataSource.failPending(chatId, clientMessageId)
            }
    }

    suspend fun advanceReadPointer(chatId: ChatId, messageId: Long): Result<Unit> {
        val selfId = userManager.accountId ?: return Result.failure(
            IllegalStateException("No account")
        )

        // Update local pointer — reactive flow updates the feed's unread count
        val pointer = MessagePointer(
            type = PointerType.READ,
            userId = selfId,
            value = messageId,
            timestamp = Clock.System.now(),
        )
        memberDataSource.updatePointers(chatId, pointer)

        return messagingController.advancePointer(chatId, PointerType.READ, messageId)
    }

    fun setActiveChatId(chatId: ChatId?) {
        _state.update { it.copy(activeChat = chatId) }
    }

    fun isActiveChat(chatId: ChatId): Boolean {
        return _state.value.activeChat == chatId
    }

    suspend fun getOtherMemberE164(chatId: ChatId): String? {
        val selfId = userManager.accountId
        val localMembers = memberDataSource.getMembersForChat(chatId)
        val otherMember = localMembers.firstOrNull { it.userId != selfId }
        if (otherMember != null) return otherMember.userProfile.verifiedPhoneNumber

        // Chat not persisted locally yet — fetch from server
        val metadata = chatController.getChat(chatId).getOrNull() ?: return null
        memberDataSource.upsert(chatId, metadata.members)
        return metadata.members
            .firstOrNull { it.userId != selfId }
            ?.userProfile?.verifiedPhoneNumber
    }

    fun dismissNotifications(chatId: ChatId) {
        notificationManager.cancel(chatId.hashCode())
    }

    suspend fun markAsRead(chatId: ChatId): Result<Unit> {
        val messageId = state.value.feed
            .firstOrNull { it.chatId == chatId }
            ?.lastMessage?.messageId
            ?: messageDataSource.getLatestMessageId(chatId)
            ?: return Result.success(Unit)
        return advanceReadPointer(chatId, messageId)
            .also { dismissNotifications(chatId) }
    }

    suspend fun notifyTyping(chatId: ChatId, typingState: TypingState): Result<Unit> {
        return messagingController.notifyIsTyping(chatId, typingState)
    }

    fun refreshFeed() {
        syncFeed()
    }

    suspend fun reset() {
        stopHeartbeat()
        closeEventStream()
        syncJob?.cancel()
        flagObserverJob?.cancel()
        feedObserverJob?.cancel()
        networkObserverJob?.cancel()
        feedObserverJob = null
        _state.value = ChatState()
        cluster.value = null
        metadataDataSource.clear()
        messageDataSource.clear()
        memberDataSource.clear()
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
                    syncFeed()
                    openEventStream()
                    startHeartbeat()
                }
            }
            .launchIn(scope)
    }

    private fun observeFeedFromDb() {
        feedObserverJob?.cancel()
        feedObserverJob = combine(
            metadataDataSource.observeAll(),
            memberDataSource.observeAll(),
        ) { metadataEntities, membersByChat ->
            buildFeedFromDb(metadataEntities, membersByChat)
        }.onEach { feed ->
            _state.update { it.copy(feed = feed) }
        }.launchIn(scope)
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

    private fun syncFeed() {
        syncJob?.cancel()
        syncJob = scope.launch { performFeedSync() }
    }

    private suspend fun performFeedSync() {
        _state.update { it.copy(feedSyncState = FeedSyncState.Syncing) }
        chatController.getDmChatFeed()
            .onSuccess { page ->
                metadataDataSource.upsert(page.chats)

                for (chat in page.chats) {
                    memberDataSource.upsert(chat.chatId, chat.members)
                    chat.lastMessage?.let { msg ->
                        messageDataSource.upsert(chat.chatId, listOf(msg))
                    }
                }

                _state.update { it.copy(feedSyncState = FeedSyncState.Synced) }
                trace(tag = TAG, message = "Feed synced: ${page.chats.size} chats", type = TraceType.Process)

                // Delta-sync for chats with a known event sequence; full load for new chats
                for (chat in page.chats) {
                    if (chat.latestEventSequence > 0) {
                        val localSeq = metadataDataSource.getLatestEventSequence(chat.chatId)
                        if (localSeq > 0 && localSeq < chat.latestEventSequence) {
                            performDeltaSync(chat.chatId)
                            continue
                        }
                    }
                    if (!messageDataSource.hasMessages(chat.chatId)) {
                        loadMessages(chat.chatId)
                    }
                }
            }
            .onFailure { error ->
                _state.update { it.copy(feedSyncState = FeedSyncState.Error) }
                trace(tag = TAG, message = "Feed sync failed: ${error.message}", type = TraceType.Error)
            }
    }

    private fun openEventStream() {
        if (eventStreamingController.isConnected) {
            trace(tag = TAG, message = "Event stream already connected, skipping open", type = TraceType.Process)
            ensureCollector()
            return
        }

        eventStreamingController.open(scope)
        ensureCollector()
    }

    private fun ensureCollector() {
        if (eventStreamCollectJob?.isActive != true) {
            eventStreamCollectJob = scope.launch {
                eventStreamingController.chatUpdates.collect { applyUpdate(it) }
            }
        }
    }

    private fun closeEventStream() {
        eventStreamCollectJob?.cancel()
        eventStreamCollectJob = null
        eventStreamingController.close()
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL)
                if (!eventStreamingController.isStreamActive) {
                    trace(tag = TAG, message = "Heartbeat: event stream dead, syncing feed and reconnecting", type = TraceType.Process)
                    syncFeed()
                    // Close the dead ref so open() creates a fresh one
                    eventStreamingController.close()
                    openEventStream()
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun applyUpdate(update: ChatUpdate) {
        val chatId = update.chatId

        // --- Resolve messages: prefer events, fall back to deprecated newMessages ---

        val resolvedMessages = if (update.events.isNotEmpty()) {
            update.events
                .flatMap { event -> event.mutations.map { it.message } }
                .sortedBy { it.eventSequence }
                .distinctBy { it.messageId }
        } else {
            @Suppress("DEPRECATION")
            update.newMessages
        }

        trace(
            tag = TAG,
            message = "applyUpdate: chatId=$chatId, messages=${resolvedMessages.size}, events=${update.events.size}, pointers=${update.pointerUpdates.size}, reactions=${update.reactionUpdates.size}, typing=${update.typingNotifications.size}",
            type = TraceType.Process,
        )

        // --- Persist to DB first (suspend, off main thread) ---

        val lastMsg = if (resolvedMessages.isNotEmpty()) {
            trace(tag = TAG, message = "Upserting ${resolvedMessages.size} messages for $chatId", type = TraceType.Process)
            messageDataSource.upsert(chatId, resolvedMessages)
            resolvedMessages.maxByOrNull { it.messageId }?.also { msg ->
                metadataDataSource.updateLastMessageId(chatId, msg.messageId)
                metadataDataSource.updateLastActivity(chatId, msg.timestamp.toEpochMilliseconds())
            }
        } else null

        // Advance event sequence cursor when processing events
        if (update.events.isNotEmpty()) {
            val maxSequence = update.events.maxOf { it.sequence }
            val currentSequence = metadataDataSource.getLatestEventSequence(chatId)
            if (maxSequence > currentSequence) {
                metadataDataSource.updateLatestEventSequence(chatId, maxSequence)
            }
        }

        for (pointer in update.pointerUpdates) {
            memberDataSource.updatePointers(chatId, pointer)
        }

        for (metaUpdate in update.metadataUpdates) {
            when (metaUpdate) {
                is MetadataUpdate.FullRefresh -> {
                    metadataDataSource.upsert(metaUpdate.metadata)
                    memberDataSource.deleteForChat(metaUpdate.metadata.chatId)
                    memberDataSource.upsert(metaUpdate.metadata.chatId, metaUpdate.metadata.members)
                    metaUpdate.metadata.lastMessage?.let { msg ->
                        messageDataSource.upsert(metaUpdate.metadata.chatId, listOf(msg))
                    }
                }
                is MetadataUpdate.LastActivityChanged -> {
                    metadataDataSource.updateLastActivity(
                        chatId,
                        metaUpdate.newLastActivity.toEpochMilliseconds(),
                    )
                }
            }
        }

        // --- Process reaction updates into in-memory overlay ---

        if (update.reactionUpdates.isNotEmpty()) {
            _state.update { state ->
                val chatOverlays = state.reactionOverlays[chatId]?.toMutableMap() ?: mutableMapOf()
                for (reactionUpdate in update.reactionUpdates) {
                    applyReactionUpdate(chatOverlays, reactionUpdate)
                }
                state.copy(
                    reactionOverlays = state.reactionOverlays + (chatId to chatOverlays.toMap())
                )
            }
        }

        // --- Eagerly update token balance for incoming cash ---

        val selfId = userManager.accountId
        for (msg in resolvedMessages) {
            if (msg.senderId == selfId) continue
            for (content in msg.content) {
                if (content is MessageContent.Cash) {
                    tokenCoordinator.add(content.mint, content.amount)
                }
            }
        }

        // --- Check if unknown chat requires a full feed sync ---

        if (lastMsg != null) {
            if (!metadataDataSource.exists(chatId)) {
                syncFeed()
            }
        }

        // --- Update ephemeral state (typing indicators are not DB-backed) ---

        if (update.typingNotifications.isNotEmpty()) {
            _state.update { state ->
                val currentTypists = state.typingIndicators[chatId]?.toMutableSet() ?: mutableSetOf()
                for (notification in update.typingNotifications) {
                    applyTypingNotification(currentTypists, notification)
                }
                state.copy(
                    typingIndicators = state.typingIndicators + (chatId to currentTypists.toSet())
                )
            }
        }
    }

    private fun applyReactionUpdate(
        overlays: MutableMap<Long, ReactionSummary>,
        update: ReactionUpdate,
    ) {
        val existing = overlays[update.messageId]
        val existingReactions = existing?.reactions?.toMutableList() ?: mutableListOf()

        // Find existing reaction for this emoji
        val idx = existingReactions.indexOfFirst { it.emoji == update.emoji }
        if (idx >= 0) {
            val current = existingReactions[idx]
            // LWW guard using sequence
            if (update.sequence <= current.sequence) return
            existingReactions[idx] = EmojiReaction(
                emoji = update.emoji,
                count = update.count,
                reactedBySelf = current.reactedBySelf, // preserved; server will correct on next full fetch
                sampleReactors = current.sampleReactors,
                sequence = update.sequence,
            )
        } else {
            existingReactions.add(
                EmojiReaction(
                    emoji = update.emoji,
                    count = update.count,
                    reactedBySelf = false,
                    sampleReactors = emptyList(),
                    sequence = update.sequence,
                )
            )
        }

        // Remove reactions with count == 0
        existingReactions.removeAll { it.count <= 0 }

        overlays[update.messageId] = ReactionSummary(
            messageId = update.messageId,
            reactions = existingReactions.toList(),
        )
    }

    private suspend fun performDeltaSync(chatId: ChatId) {
        val afterSequence = metadataDataSource.getLatestEventSequence(chatId)
        trace(tag = TAG, message = "Delta sync for $chatId from sequence $afterSequence", type = TraceType.Process)

        try {
            val result = messagingController.getDelta(chatId, afterSequence).first()
            result
                .onSuccess { delta ->
                    if (delta.messages.isNotEmpty()) {
                        messageDataSource.upsert(chatId, delta.messages)
                        val latest = delta.messages.maxByOrNull { it.messageId }
                        latest?.let { msg ->
                            metadataDataSource.updateLastMessageId(chatId, msg.messageId)
                            metadataDataSource.updateLastActivity(chatId, msg.timestamp.toEpochMilliseconds())
                        }
                    }
                    if (delta.latestSequence > afterSequence) {
                        metadataDataSource.updateLatestEventSequence(chatId, delta.latestSequence)
                    }
                    trace(tag = TAG, message = "Delta sync complete: ${delta.messages.size} messages, sequence ${delta.latestSequence}", type = TraceType.Process)
                }
                .onFailure { error ->
                    if (error is GetDeltaError.ResetRequired) {
                        trace(tag = TAG, message = "Delta sync reset required for $chatId, falling back to full load", type = TraceType.Process)
                        loadMessages(chatId)
                    } else {
                        trace(tag = TAG, message = "Delta sync failed for $chatId: ${error.message}", type = TraceType.Error)
                    }
                }
        } catch (e: Exception) {
            trace(tag = TAG, message = "Delta sync exception for $chatId: ${e.message}", type = TraceType.Error)
        }
    }

    fun observeReactions(chatId: ChatId, messageId: Long): Flow<ReactionSummary?> {
        return _state.map { it.reactionOverlays[chatId]?.get(messageId) }
            .distinctUntilChanged()
    }

    private fun applyTypingNotification(
        typists: MutableSet<ActiveTypist>,
        notification: TypingNotification,
    ) {
        when (notification.state) {
            TypingState.STARTED_TYPING, TypingState.STILL_TYPING -> {
                typists.removeAll { it.userId == notification.userId }
                typists.add(ActiveTypist(userId = notification.userId, since = Clock.System.now()))
            }
            TypingState.STOPPED_TYPING, TypingState.TYPING_TIMED_OUT -> {
                typists.removeAll { it.userId == notification.userId }
            }
            TypingState.UNKNOWN -> Unit
        }
    }

    // endregion
}

class NoDmChatInitializedException(e164: String) : Exception("No DM chat for $e164")
