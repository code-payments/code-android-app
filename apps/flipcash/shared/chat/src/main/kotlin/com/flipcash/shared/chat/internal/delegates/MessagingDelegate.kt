@file:OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)

package com.flipcash.shared.chat.internal.delegates

import androidx.core.app.NotificationManagerCompat
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.mediator.ChatMessageRemoteMediator
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.TypingState
import com.flipcash.services.models.DeleteMessageError
import com.flipcash.services.models.EditMessageError
import com.flipcash.shared.chat.ChatHydrationState
import com.flipcash.shared.chat.MessagingOperations
import com.flipcash.shared.chat.PendingMutation
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.replacingText
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Handles all per-chat messaging operations: sending and receiving messages,
 * read-pointer advancement, paging, member identity resolution, and notification
 * management.
 *
 * This delegate is self-contained — it does not emit cross-delegate events.
 * All methods target a single conversation identified by [ChatId].
 *
 * Message sending follows an optimistic-insert pattern:
 * 1. [insertPending][ChatMessageDataSource.insertPending] writes a local placeholder.
 * 2. The server call returns the confirmed [ChatMessage].
 * 3. [confirmPending][ChatMessageDataSource.confirmPending] replaces the placeholder,
 *    or [failPending][ChatMessageDataSource.failPending] marks it as failed.
 *
 * @see com.flipcash.shared.chat.internal.RealChatCoordinator
 */
@Singleton
class MessagingDelegate @Inject constructor(
    private val chatController: ChatController,
    private val messagingController: ChatMessagingController,
    private val metadataDataSource: ChatMetadataDataSource,
    private val messageDataSource: ChatMessageDataSource,
    private val memberDataSource: ChatMemberDataSource,
    private val notificationManager: NotificationManagerCompat,
    private val userManager: UserManager,
    private val stateHolder: ChatStateHolder,
    private val analytics: FlipcashAnalyticsService,
) : MessagingOperations {

    /**
     * Edits and deletes awaiting a server answer, per chat, keyed by message id.
     *
     * In memory on purpose. The database is the server's version of the transcript; a mutation the
     * server has not confirmed does not belong in it, and keeping it out means a rollback is a map
     * removal rather than a compensating write.
     */
    private val pendingMutations = MutableStateFlow<Map<ChatId, Map<Long, PendingMutation>>>(emptyMap())

    // region MessagingOperations

    override suspend fun getOtherMember(chatId: ChatId): ChatMember? {
        val selfId = userManager.accountId
        val localMembers = memberDataSource.getMembersForChat(chatId)
        localMembers.firstOrNull { it.userId != selfId }?.let { return it }

        val metadata = chatController.getChat(chatId).getOrNull() ?: return null
        memberDataSource.upsert(chatId, metadata.members)
        return metadata.members.firstOrNull { it.userId != selfId }
    }

    override suspend fun getOtherMemberE164(chatId: ChatId): String? =
        getOtherMember(chatId)?.userProfile?.verifiedPhoneNumber

    override fun setActiveChatId(chatId: ChatId?) {
        stateHolder.update { it.copy(activeChat = chatId) }
    }

    override fun isActiveChat(chatId: ChatId): Boolean {
        return stateHolder.current.activeChat == chatId
    }

    override fun dismissNotifications(chatId: ChatId) {
        notificationManager.cancel(chatId.hashCode())
    }

    override fun observeMessages(chatId: ChatId): Flow<List<ChatMessage>> {
        return messageDataSource.observeMessages(chatId)
    }

    override fun hasEverTipped(): Flow<Boolean?> =
        stateHolder.state
            .map { it.historyHydration }
            .distinctUntilChanged()
            .flatMapLatest { hydration ->
                // Re-subscribing on the hydration change re-runs the query, so the first value a
                // caller sees after a backfill is a fresh read. `combine`-ing the two flows instead
                // would race: the emission carrying "hydrated" would carry the *pre*-backfill answer
                // with it, and the caller would act on `false` a beat before Room's invalidation
                // published `true` — the same flash of wrong state, just narrower.
                messageDataSource.hasEverTipped().map { tipped ->
                    when {
                        // A TIPPED message in the cache is proof regardless of hydration, and
                        // answering straight away is what keeps a warm cache — the normal case now
                        // that logout no longer wipes it — from waiting on a round-trip.
                        tipped -> true
                        // Absence proves nothing yet. Null rather than false: the caller has to be
                        // able to tell "has not tipped" from "we have not looked".
                        hydration == ChatHydrationState.Unknown -> null
                        else -> false
                    }
                }
            }
            // The re-subscribe repeats the current answer whenever hydration moves, which for a
            // cache that already held the tip is the same `true` twice over.
            .distinctUntilChanged()

    override fun observeMessagesPaged(chatId: ChatId): Flow<PagingData<ChatMessage>> {
        return Pager(
            config = PagingConfig(pageSize = 50),
            remoteMediator = ChatMessageRemoteMediator(chatId, messagingController, messageDataSource),
        ) {
            messageDataSource.observeForChat(chatId)
        }.flow.map { page ->
            page.map { entity -> messageDataSource.toChatMessage(entity) }
        }
    }

    override fun observeMembers(chatId: ChatId): Flow<List<ChatMember>> {
        return memberDataSource.observeMembers(chatId)
    }

    override fun observeOtherReadPointer(chatId: ChatId): Flow<MessagePointer?> {
        val selfId = userManager.accountId
        return memberDataSource.observeMembers(chatId)
            .map { members ->
                members.firstOrNull { it.userId != selfId }
                    ?.pointers
                    ?.firstOrNull { it.type == PointerType.READ }
            }
            .distinctUntilChanged()
    }

    override suspend fun loadMessages(chatId: ChatId) {
        messagingController.getMessages(chatId)
            .onSuccess { messages ->
                messageDataSource.upsert(chatId, messages)

                // Seat the event-log cursor at the newest page's frontier. It is what marks this
                // transcript as fetched, and it lets a following catch-up resume from head and
                // append genuinely newer messages instead of re-pulling the whole history from
                // sequence 0. Only ever advanced — a page older than the cursor must not rewind it.
                val head = messages.maxOfOrNull { it.eventSequence } ?: 0L
                if (head > metadataDataSource.getLatestEventSequence(chatId)) {
                    metadataDataSource.updateLatestEventSequence(chatId, head)
                }

                val latest = messages.maxByOrNull { it.messageId } ?: return@onSuccess
                metadataDataSource.updateLastMessageId(chatId, latest.messageId)
                metadataDataSource.updateLastActivity(chatId, latest.timestamp.toEpochMilliseconds())
            }
    }

    override suspend fun sendMessage(chatId: ChatId, content: String): Result<ChatMessage> {
        if (content.isBlank()) {
            return Result.failure(IllegalArgumentException("Cannot send a blank message"))
        }

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

                metadataDataSource.updateLastMessageId(chatId, serverMessage.messageId)
                metadataDataSource.updateLastActivity(chatId, serverMessage.timestamp.toEpochMilliseconds())
            }
            .onFailure {
                messageDataSource.failPending(chatId, clientMessageId)
            }
    }

    override suspend fun retryMessage(chatId: ChatId, pendingClientIdHex: String, content: List<MessageContent>): Result<ChatMessage> {
        val clientMessageId = messageDataSource.retryPending(chatId, pendingClientIdHex)

        return messagingController.sendMessage(chatId, content, clientMessageId)
            .onSuccess { serverMessage ->
                messageDataSource.confirmPending(chatId, clientMessageId, serverMessage)
                advanceReadPointer(chatId, serverMessage.messageId)

                metadataDataSource.updateLastMessageId(chatId, serverMessage.messageId)
                metadataDataSource.updateLastActivity(chatId, serverMessage.timestamp.toEpochMilliseconds())
            }
            .onFailure {
                messageDataSource.failPending(chatId, clientMessageId)
            }
    }

    override fun observePendingMutations(chatId: ChatId): Flow<Map<Long, PendingMutation>> =
        pendingMutations.map { it[chatId].orEmpty() }.distinctUntilChanged()

    override suspend fun editMessage(chatId: ChatId, messageId: Long, text: String): Result<ChatMessage> {
        val edited = text.trim()
        if (edited.isBlank()) {
            return Result.failure(IllegalArgumentException("Cannot edit a message to be blank"))
        }

        val stored = messageDataSource.getMessage(chatId, messageId)
            ?: return Result.failure(IllegalStateException("No local copy of message $messageId"))

        // Read the stored body rather than building a bare text message, so anything wrapping the
        // text — a reply citation, once replies ship — survives the edit.
        val content = stored.content.replacingText(edited)
        val expectedSequence = stored.eventSequence

        putMutation(
            chatId = chatId,
            mutation = PendingMutation(
                messageId = messageId,
                expectedSequence = expectedSequence,
                kind = PendingMutation.Kind.Edited(edited, Clock.System.now()),
            ),
        )

        return messagingController.editMessage(chatId, messageId, content, expectedSequence)
            .reconcile(chatId, messageId) { it is EditMessageError.Conflict }
    }

    override suspend fun deleteMessage(chatId: ChatId, messageId: Long): Result<ChatMessage> {
        val stored = messageDataSource.getMessage(chatId, messageId)
            ?: return Result.failure(IllegalStateException("No local copy of message $messageId"))

        val expectedSequence = stored.eventSequence

        putMutation(
            chatId = chatId,
            mutation = PendingMutation(
                messageId = messageId,
                expectedSequence = expectedSequence,
                kind = PendingMutation.Kind.Deleted(Clock.System.now(), userManager.accountId),
            ),
        )

        return messagingController.deleteMessage(chatId, messageId, expectedSequence)
            .reconcile(chatId, messageId) { it is DeleteMessageError.Conflict }
    }

    override suspend fun advanceReadPointer(chatId: ChatId, messageId: Long): Result<Unit> {
        val selfId = userManager.accountId ?: return Result.failure(
            IllegalStateException("No account")
        )

        // Report before the write, while the previous pointer is still readable.
        // Crossing the pointer is the only moment a message is unambiguously
        // "received" by the user rather than merely delivered to the device.
        reportCrossedMessages(chatId, selfId, messageId)

        val pointer = MessagePointer(
            type = PointerType.READ,
            userId = selfId,
            value = messageId,
            timestamp = Clock.System.now(),
        )
        memberDataSource.updatePointers(chatId, pointer)

        return messagingController.advancePointer(chatId, PointerType.READ, messageId)
    }

    /**
     * Re-sends a READ pointer the server never took, without touching the local copy or the
     * analytics that go with a genuine read — both already happened when the message was seen.
     */
    internal suspend fun reportReadPointer(chatId: ChatId, messageId: Long) {
        messagingController.advancePointer(chatId, PointerType.READ, messageId)
            .onFailure {
                trace(
                    tag = TAG,
                    message = "Re-reporting read pointer $messageId failed",
                    type = TraceType.Error,
                    error = it,
                )
            }
    }

    override suspend fun markAsRead(chatId: ChatId): Result<Unit> {
        // The stored newest id, tombstones included — the feed's own `lastMessage` skips them so
        // the list can preview the last message with content, and reading it here would park the
        // pointer below a deleted message and leave the chat permanently unread.
        val messageId = messageDataSource.getLatestMessageId(chatId)
            ?: stateHolder.current.feed
                .firstOrNull { it.chatId == chatId }
                ?.lastMessage?.messageId
            ?: return Result.success(Unit)
        return advanceReadPointer(chatId, messageId)
            .also { dismissNotifications(chatId) }
    }

    override suspend fun notifyTyping(chatId: ChatId, typingState: TypingState): Result<Unit> {
        return messagingController.notifyIsTyping(chatId, typingState)
    }

    // endregion

    // region Internal

    /**
     * Emits one received event per inbound message the read pointer is about to
     * cross. A non-advancing pointer (a re-read, or a backwards jump from an
     * out-of-order caller) crosses nothing and emits nothing.
     */
    private suspend fun reportCrossedMessages(chatId: ChatId, selfId: ID, messageId: Long) {
        val previous = memberDataSource.getSelfReadPointer(chatId, selfId)
        if (messageId <= previous) return

        val crossed = messageDataSource.getInboundMessagesInRange(
            chatId = chatId,
            selfId = selfId,
            afterId = previous,
            throughId = messageId,
        )
        if (crossed.isEmpty()) return

        val chatType = metadataDataSource.getChatType(chatId)

        for (msg in crossed) {
            val tip = msg.content
                .filterIsInstance<MessageContent.Cash>()
                .firstOrNull { it.action == MessageContent.Cash.Action.TIPPED }

            if (tip != null) {
                analytics.tipReceived(chatType, tip.amount, tip.mint)
            } else {
                analytics.messageReceived(chatType)
            }
        }
    }

    /**
     * Settles a mutation against the server's answer, then drops the overlay either way.
     *
     * Ordering matters on success: persist first, drop second. Room publishes asynchronously, and
     * the `eventSequence` guard in `applying` retires the overlay the moment the newer row lands,
     * so the reader never sees a gap. Dropping first would flash the pre-edit text.
     *
     * A conflict means someone else moved the message first. There is no automatic retry — the
     * user's edit was written against a version that no longer exists, and silently re-applying it
     * over whatever replaced it is how you clobber someone. Re-read instead so the transcript shows
     * what is actually there, and let the caller tell the user.
     *
     * Any other failure reverts. A delete that did not happen means the message is still there, and
     * showing it again is the truth.
     */
    private suspend fun Result<ChatMessage>.reconcile(
        chatId: ChatId,
        messageId: Long,
        isConflict: (Throwable) -> Boolean,
    ): Result<ChatMessage> = this
        .onSuccess { serverMessage ->
            messageDataSource.upsert(chatId, listOf(serverMessage))
            clearMutation(chatId, messageId)
        }
        .onFailure { cause ->
            if (isConflict(cause)) {
                messagingController.getMessage(chatId, messageId)
                    .onSuccess { messageDataSource.upsert(chatId, listOf(it)) }
                    .onFailure {
                        trace(
                            tag = TAG,
                            message = "Re-reading conflicted message $messageId failed",
                            type = TraceType.Error,
                            error = it,
                        )
                    }
            }
            clearMutation(chatId, messageId)
        }

    private fun putMutation(chatId: ChatId, mutation: PendingMutation) {
        pendingMutations.update { all ->
            all + (chatId to (all[chatId].orEmpty() + (mutation.messageId to mutation)))
        }
    }

    private fun clearMutation(chatId: ChatId, messageId: Long) {
        pendingMutations.update { all ->
            val remaining = all[chatId].orEmpty() - messageId
            if (remaining.isEmpty()) all - chatId else all + (chatId to remaining)
        }
    }

    internal suspend fun clear() {
        pendingMutations.value = emptyMap()
        metadataDataSource.clear()
        messageDataSource.clear()
        memberDataSource.clear()
    }

    // endregion

    private companion object {
        const val TAG = "MessagingDelegate"
    }
}
