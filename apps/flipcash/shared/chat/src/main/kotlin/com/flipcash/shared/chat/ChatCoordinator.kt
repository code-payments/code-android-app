@file:OptIn(ExperimentalCoroutinesApi::class)

package com.flipcash.shared.chat

import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.flipcash.app.contacts.device.DeviceContact
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.ContactDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.controllers.EventStreamingController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessagePointer
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
import com.getcode.utils.decodeBase58
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private val contactDataSource: ContactDataSource,
    private val networkObserver: NetworkConnectivityListener,
    private val notificationManager: NotificationManagerCompat,
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
    private var eventStreamRetryJob: Job? = null

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

    suspend fun getChatId(contact: DeviceContact): Result<ChatId> {
        val raw = contactDataSource.getDmChatId(contact.e164)
        if (raw.isNullOrEmpty()) {
            return Result.failure(NoDmChatInitializedException(contact.e164))
        }
        return runCatching { ChatId(raw.decodeBase58()) }
    }

    fun observeMessages(chatId: ChatId): Flow<List<ChatMessage>> {
        return messageDataSource.observeMessages(chatId)
    }

    fun observeMessagesPaged(chatId: ChatId): Flow<PagingData<ChatMessage>> {
        return Pager(
            config = PagingConfig(pageSize = 50),
        ) {
            messageDataSource.observeForChat(chatId)
        }.flow.map { page ->
            page.map { entity -> messageDataSource.toChatMessage(entity) }
        }
    }

    fun observeTypingIndicators(chatId: ChatId): Flow<Set<ActiveTypist>> {
        return _state.map { it.typingIndicators[chatId] ?: emptySet() }
    }

    fun observeOtherReadPointer(chatId: ChatId): Flow<Long> {
        val selfId = userManager.accountId
        return memberDataSource.observeMembers(chatId)
            .map { members ->
                members.firstOrNull { it.userId != selfId }
                    ?.pointers
                    ?.firstOrNull { it.type == PointerType.READ }
                    ?.value ?: 0L
            }
            .distinctUntilChanged()
    }

    suspend fun loadMessages(chatId: ChatId, limit: Int = 100) {
        messagingController.getMessages(chatId)
            .onSuccess { messages ->
                messageDataSource.upsert(chatId, messages)
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

                // Update feed metadata so the contact list shows the latest message
                metadataDataSource.updateLastMessageId(chatId, serverMessage.messageId)
                metadataDataSource.updateLastActivity(chatId, serverMessage.timestamp.toEpochMilliseconds())
                _state.update { state ->
                    val updatedFeed = state.feed.map { meta ->
                        if (meta.chatId == chatId) {
                            meta.copy(
                                lastMessage = serverMessage,
                                lastActivity = serverMessage.timestamp,
                            )
                        } else meta
                    }
                    state.copy(feed = updatedFeed)
                }
            }
            .onFailure {
                messageDataSource.failPending(chatId, clientMessageId)
            }
    }

    suspend fun advanceReadPointer(chatId: ChatId, messageId: Long): Result<Unit> {
        val selfId = userManager.accountId ?: return Result.failure(
            IllegalStateException("No account")
        )

        // Optimistically update local pointer so the feed unread count clears immediately
        val pointer = MessagePointer(
            type = PointerType.READ,
            userId = selfId,
            value = messageId,
        )
        memberDataSource.updatePointers(chatId, pointer)
        _state.update { state ->
            val updatedFeed = state.feed.map { meta ->
                if (meta.chatId == chatId) {
                    meta.copy(members = meta.members.map { member ->
                        if (member.userId == selfId) {
                            val updated = member.pointers
                                .filter { it.type != PointerType.READ }
                                .plus(pointer)
                            member.copy(pointers = updated)
                        } else member
                    })
                } else meta
            }
            state.copy(feed = updatedFeed)
        }

        return messagingController.advancePointer(chatId, PointerType.READ, messageId)
    }

    fun dismissNotifications(chatId: ChatId) {
        notificationManager.cancel(chatId.hashCode())
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
        eventStreamRetryJob?.cancel()
        val opened = eventStreamingController.open(scope) {
            // Stream died after exhausting retries — schedule a re-open
            eventStreamRetryJob = scope.launch {
                delay(5_000)
                trace(tag = TAG, message = "Retrying event stream after failure", type = TraceType.Process)
                openEventStream()
            }
        }
        if (!opened) {
            trace(tag = TAG, message = "Event stream failed to open", type = TraceType.Error)
        }
        // Always ensure a collector is running so events are processed
        // as soon as the stream (re)connects.
        if (eventStreamCollectJob?.isActive != true) {
            eventStreamCollectJob = scope.launch {
                eventStreamingController.chatUpdates.collect { applyUpdate(it) }
            }
        }
    }

    private fun closeEventStream() {
        eventStreamRetryJob?.cancel()
        eventStreamRetryJob = null
        eventStreamCollectJob?.cancel()
        eventStreamCollectJob = null
        eventStreamingController.close()
    }

    private suspend fun applyUpdate(update: ChatUpdate) {
        val chatId = update.chatId
        trace(
            tag = TAG,
            message = "applyUpdate: chatId=$chatId, newMessages=${update.newMessages.size}, pointers=${update.pointerUpdates.size}, typing=${update.typingNotifications.size}",
            type = TraceType.Process,
        )

        // New messages
        if (update.newMessages.isNotEmpty()) {
            trace(tag = TAG, message = "Upserting ${update.newMessages.size} new messages for $chatId", type = TraceType.Process)
            messageDataSource.upsert(chatId, update.newMessages)

            val lastMsg = update.newMessages.maxByOrNull { it.messageId }
            if (lastMsg != null) {
                metadataDataSource.updateLastMessageId(chatId, lastMsg.messageId)
                metadataDataSource.updateLastActivity(chatId, lastMsg.timestamp.toEpochMilliseconds())

                _state.update { state ->
                    val updatedFeed = state.feed.map { meta ->
                        if (meta.chatId == chatId) {
                            meta.copy(
                                lastMessage = lastMsg,
                                lastActivity = lastMsg.timestamp,
                            )
                        } else meta
                    }
                    state.copy(feed = updatedFeed)
                }
            }
        }

        // Pointer updates
        for (pointer in update.pointerUpdates) {
            memberDataSource.updatePointers(chatId, pointer)
        }

        if (update.pointerUpdates.isNotEmpty()) {
            _state.update { state ->
                val updatedFeed = state.feed.map { meta ->
                    if (meta.chatId == chatId) {
                        meta.copy(members = meta.members.map { member ->
                            val memberPointerUpdates = update.pointerUpdates
                                .filter { it.userId == member.userId }
                            if (memberPointerUpdates.isNotEmpty()) {
                                val updated = member.pointers.toMutableList()
                                for (p in memberPointerUpdates) {
                                    updated.removeAll { it.type == p.type }
                                    updated.add(p)
                                }
                                member.copy(pointers = updated)
                            } else member
                        })
                    } else meta
                }
                state.copy(feed = updatedFeed)
            }
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

class NoDmChatInitializedException(e164: String) : Exception("No DM chat for $e164")
