@file:OptIn(ExperimentalCoroutinesApi::class)

package com.flipcash.shared.chat

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.controllers.EventStreamingController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MetadataUpdate
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.TypingNotification
import com.flipcash.services.models.chat.TypingState
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.providers.SessionListener
import com.getcode.utils.TraceType
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
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
    private val networkObserver: NetworkConnectivityListener,
    private val userManager: UserManager,
) : SessionListener, DefaultLifecycleObserver {

    companion object {
        private const val TAG = "ChatCoordinator"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cluster = MutableStateFlow<AccountCluster?>(null)
    private val _state = MutableStateFlow(ChatState())

    private var syncJob: Job? = null
    private var eventStreamCollectJob: Job? = null

    val state: StateFlow<ChatState>
        get() = _state.asStateFlow()

    val feed: Flow<List<ChatSummary>>
        get() = _state.map { state ->
            state.feed.map { metadata ->
                val readPointer = metadata.members
                    .firstOrNull { it.userId == userManager.accountId }
                    ?.pointers
                    ?.firstOrNull { it.type == PointerType.READ }
                    ?.value ?: 0L

                val unreadCount = metadata.lastMessage?.let { lastMsg ->
                    if (lastMsg.messageId > readPointer) 1 else 0
                } ?: 0

                ChatSummary(metadata = metadata, unreadCount = unreadCount)
            }
        }

    // region SessionListener

    override suspend fun onUserLoggedIn(cluster: AccountCluster) {
        trace(tag = TAG, message = "User logged in, hydrating chat", type = TraceType.User)
        this.cluster.value = cluster
        hydrateFromPersistence()
    }

    // endregion

    // region Lifecycle

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        cluster.filterNotNull()
            .flatMapLatest { networkObserver.state }
            .distinctUntilChanged()
            .filter { it.connected }
            .debounce(1.seconds)
            .onEach {
                trace(tag = TAG, message = "Network connected, re-syncing chat feed", type = TraceType.Process)
                syncFeed()
                openEventStream()
            }
            .launchIn(scope)
    }

    override fun onStart(owner: LifecycleOwner) {
        if (cluster.value != null) {
            trace(tag = TAG, message = "Lifecycle resumed, syncing chat feed", type = TraceType.Process)
            syncFeed()
            openEventStream()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        closeEventStream()
    }

    // endregion

    // region Public API

    fun observeMessages(chatId: ChatId): Flow<List<ChatMessage>> {
        return messageDataSource.observeMessages(chatId)
    }

    fun observeTypingIndicators(chatId: ChatId): Flow<Set<ActiveTypist>> {
        return _state.map { it.typingIndicators[chatId] ?: emptySet() }
    }

    suspend fun loadMessages(chatId: ChatId, limit: Int = 100) {
        messagingController.getMessages(chatId)
            .onSuccess { messages ->
                messageDataSource.upsert(chatId, messages)
            }
    }

    suspend fun sendMessage(chatId: ChatId, content: List<MessageContent>): Result<ChatMessage> {
        val senderId = userManager.accountId
            ?: return Result.failure(IllegalStateException("Cannot send message without an account"))

        val (_, clientMessageId) = messageDataSource.insertPending(
            chatId = chatId,
            content = content,
            senderId = senderId,
        )

        return messagingController.sendMessage(chatId, content, clientMessageId)
            .onSuccess { serverMessage ->
                messageDataSource.confirmPending(chatId, clientMessageId, serverMessage.messageId)
            }
            .onFailure {
                messageDataSource.failPending(chatId, clientMessageId)
            }
    }

    suspend fun advanceReadPointer(chatId: ChatId, messageId: Long): Result<Unit> {
        return messagingController.advancePointer(chatId, PointerType.READ, messageId)
    }

    suspend fun notifyTyping(chatId: ChatId, typingState: TypingState): Result<Unit> {
        return messagingController.notifyIsTyping(chatId, typingState)
    }

    suspend fun reset() {
        closeEventStream()
        syncJob?.cancel()
        _state.value = ChatState()
        cluster.value = null
        metadataDataSource.clear()
        messageDataSource.clear()
        memberDataSource.clear()
        trace(tag = TAG, message = "reset complete", type = TraceType.Process)
    }

    // endregion

    // region Internal

    private suspend fun hydrateFromPersistence() {
        val entities = metadataDataSource.observeAll().firstOrNull() ?: return
        if (entities.isEmpty()) return

        val feed = entities.map { entity ->
            val members = memberDataSource.getMembersForChat(entity.chatIdHex)
            val lastMessage = entity.lastMessageId?.let {
                messageDataSource.getLatest(entity.chatIdHex)
            }
            metadataDataSource.toMetadata(entity, members, lastMessage)
        }

        _state.update { it.copy(feed = feed) }
        trace(tag = TAG, message = "Hydrated ${feed.size} chats from persistence", type = TraceType.Process)
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
                }

                _state.update { it.copy(feed = page.chats, feedSyncState = FeedSyncState.Synced) }
                trace(tag = TAG, message = "Feed synced: ${page.chats.size} chats", type = TraceType.Process)
            }
            .onFailure { error ->
                _state.update { it.copy(feedSyncState = FeedSyncState.Error) }
                trace(tag = TAG, message = "Feed sync failed: ${error.message}", type = TraceType.Error)
            }
    }

    private fun openEventStream() {
        eventStreamingController.open(scope)
        eventStreamCollectJob?.cancel()
        eventStreamCollectJob = scope.launch {
            eventStreamingController.chatUpdates.collect { applyUpdate(it) }
        }
    }

    private fun closeEventStream() {
        eventStreamCollectJob?.cancel()
        eventStreamCollectJob = null
        eventStreamingController.close()
    }

    private suspend fun applyUpdate(update: ChatUpdate) {
        val chatId = update.chatId

        // New messages
        if (update.newMessages.isNotEmpty()) {
            messageDataSource.upsert(chatId, update.newMessages)

            val lastMsg = update.newMessages.maxByOrNull { it.messageId }
            if (lastMsg != null) {
                metadataDataSource.updateLastMessageId(chatId, lastMsg.messageId)
                metadataDataSource.updateLastActivity(chatId, lastMsg.timestamp.toEpochMilliseconds())
            }
        }

        // Pointer updates
        for (pointer in update.pointerUpdates) {
            memberDataSource.updatePointers(chatId, pointer)
        }

        // Typing notifications (ephemeral, in-memory only)
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

        // Metadata updates
        for (metaUpdate in update.metadataUpdates) {
            when (metaUpdate) {
                is MetadataUpdate.FullRefresh -> {
                    metadataDataSource.upsert(metaUpdate.metadata)
                    memberDataSource.deleteForChat(metaUpdate.metadata.chatId)
                    memberDataSource.upsert(metaUpdate.metadata.chatId, metaUpdate.metadata.members)

                    _state.update { state ->
                        val updatedFeed = state.feed.map {
                            if (it.chatId == metaUpdate.metadata.chatId) metaUpdate.metadata else it
                        }
                        state.copy(feed = updatedFeed)
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
