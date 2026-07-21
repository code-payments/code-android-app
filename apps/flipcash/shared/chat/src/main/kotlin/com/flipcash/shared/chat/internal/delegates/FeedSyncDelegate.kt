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
import com.flipcash.shared.chat.ChatSummary
import com.flipcash.shared.chat.FeedOperations
import com.flipcash.shared.chat.FeedSyncState
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.services.user.UserManager
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
        data class DeltaSyncNeeded(val chatId: ChatId) : Event
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
                .mapNotNull { metadata ->
                    val otherMember = metadata.members.firstOrNull { !isSelf(it) }
                        ?: return@mapNotNull null

                    // Contact DMs require a resolvable identity (phone / display name). Tip DMs are
                    // identified by user id and have no phone by design, so they are never dropped.
                    if (chatType == ChatType.CONTACT_DM) {
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

    override fun observeUnreadConversations(chatType: ChatType): Flow<Int> {
        return feed(chatType).map { summaries -> summaries.count { it.unreadCount > 0 } }
    }

    override fun refreshFeed() {
        syncFeed()
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

    internal fun cancelJobs() {
        syncJob?.cancel()
        feedObserverJob?.cancel()
        feedObserverJob = null
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
    internal suspend fun fetchCombinedFeed(): Result<List<ChatMetadata>> {
        val contact = chatController.getDmChatFeed(ChatType.CONTACT_DM)
            .getOrElse { return Result.failure(it) }
        val tip = chatController.getDmChatFeed(ChatType.TIP_DM).getOrNull()
        return Result.success(contact.chats + (tip?.chats ?: emptyList()))
    }

    private suspend fun performFeedSync() {
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

                for (chat in chats) {
                    if (chat.latestEventSequence > 0) {
                        val localSeq = metadataDataSource.getLatestEventSequence(chat.chatId)
                        if (localSeq > 0 && localSeq < chat.latestEventSequence) {
                            _events.send(Event.DeltaSyncNeeded(chat.chatId))
                            continue
                        }
                    }
                    if (!messageDataSource.hasMessages(chat.chatId)) {
                        _events.send(Event.LoadMessages(chat.chatId))
                    }
                }
            }
            .onFailure { error ->
                stateHolder.update { it.copy(feedSyncState = FeedSyncState.Error) }
                trace(tag = TAG, message = "Feed sync failed: ${error.message}", type = TraceType.Error)
            }
    }

    // endregion
}
