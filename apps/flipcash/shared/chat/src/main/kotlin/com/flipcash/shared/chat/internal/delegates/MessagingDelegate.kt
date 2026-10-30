@file:OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)

package com.flipcash.shared.chat.internal.delegates

import com.flipcash.shared.chat.PaymentRequestNotSent
import com.flipcash.shared.chat.link.OutgoingLinkPreview
import com.flipcash.shared.chat.link.LinkPreviewImages
import com.flipcash.shared.chat.link.LinkPreviewChecks
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.LinkPreview
import com.flipcash.services.models.SendMessageError
import androidx.core.app.NotificationManagerCompat
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.flipcash.analytics.events.ChatEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.IncomingMessageOpener
import com.flipcash.services.chat.MessageEncryption
import com.flipcash.app.persistence.sources.mediator.ChatMessageRemoteMediator
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ViewMode
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.Pin
import com.flipcash.services.models.chat.PinSummary
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.chat.TypingState
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.services.models.chat.WidgetContent
import com.flipcash.services.models.DeleteMessageError
import com.flipcash.services.models.EditMessageError
import com.flipcash.services.models.GetChatError
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.UserProfile
import com.flipcash.shared.chat.ChatHydration
import com.flipcash.shared.chat.internal.SenderResolver
import com.flipcash.shared.chat.ChatMembership
import com.flipcash.shared.chat.MessagingOperations
import com.flipcash.shared.chat.PendingMutation
import com.flipcash.shared.chat.PinnedMessages
import com.flipcash.shared.chat.UnreadBoundary
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.OutgoingEncryption
import com.flipcash.shared.chat.media.ChatMediaText
import com.flipcash.shared.chat.replacingText
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import com.getcode.utils.TraceType
import java.nio.ByteBuffer
import com.getcode.utils.trace
import com.getcode.utils.hexEncodedString
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

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
 * In an end-to-end encrypted DM the placeholder is still the plaintext; [OutgoingEncryption]
 * seals only what goes on the wire, and its echo stores the reply with that plaintext.
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
    private val analytics: FlipcashAnalytics,
    private val senderResolver: SenderResolver,
    private val outgoing: OutgoingEncryption = OutgoingEncryption.None,
    /** Opens encrypted pushes; without it, every push keeps the server's body. */
    private val incoming: IncomingMessageOpener? = null,
    private val previewImages: LinkPreviewImages = LinkPreviewImages.None,
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

    override fun clearActiveChat(chatId: ChatId?) {
        if (chatId == null) return
        stateHolder.update { if (it.activeChat == chatId) it.copy(activeChat = null) else it }
    }

    override fun isActiveChat(chatId: ChatId): Boolean {
        return stateHolder.current.activeChat == chatId
    }

    override fun dismissNotifications(chatId: ChatId) {
        notificationManager.cancel(chatId.hashCode())
    }

    override suspend fun hydrateChat(chatId: ChatId): ChatHydration {
        if (metadataDataSource.exists(chatId)) {
            refreshGroupViewerState(chatId)
            return ChatHydration.Stored
        }
        val metadata = chatController.getChat(chatId).getOrElse { cause ->
            if (cause is GetChatError.NotFound) return ChatHydration.Absent
            trace(tag = TAG, message = "Hydrate failed for $chatId", type = TraceType.Error)
            return ChatHydration.Unavailable
        }
        return ChatHydration.Fetched(ChatMembership(metadata = metadata, isMember = null))
    }

    /**
     * A stored group's grants are only ever written by a fetch or a viewer-state event, and the
     * server sends the latter when the state changes. A chat stored before the moderation columns
     * existed would therefore keep no grant until something unrelated moved, hiding the host's
     * Silence and Remove. Reading the chat again on open fills them. They are written outside the
     * version gate, which would drop a read whose version the row already holds.
     */
    private suspend fun refreshGroupViewerState(chatId: ChatId) {
        if (metadataDataSource.getChatType(chatId) != ChatType.GROUP) return
        val moderation = chatController.getChat(chatId).getOrNull()?.viewerState?.permissions?.moderation ?: return
        metadataDataSource.updateModerationGrants(chatId, moderation.canSilence, moderation.canBan)
    }

    override fun observeMessages(chatId: ChatId): Flow<List<ChatMessage>> {
        return messageDataSource.observeMessages(chatId)
    }

    override fun observeMessagesPaged(chatId: ChatId): Flow<PagingData<ChatMessage>> {
        return Pager(
            // The first load defaults to three pages. One fills the screen, and every row in it
            // is mapped (quotes included) before the transcript can draw.
            config = PagingConfig(pageSize = 50, initialLoadSize = 50),
            remoteMediator = ChatMessageRemoteMediator(chatId, messagingController, messageDataSource),
        ) {
            messageDataSource.observeForChat(chatId)
        }.flow.map { page ->
            page.map { entity -> messageDataSource.toChatMessage(entity) }
        }
    }

    override suspend fun getMessage(chatId: ChatId, messageId: Long): ChatMessage? =
        messageDataSource.getMessage(chatId, messageId)

    override suspend fun distanceFromNewest(chatId: ChatId, messageId: Long): Int? =
        messageDataSource.distanceFromNewest(chatId, messageId)

    /**
     * Suspends until there is an account and a per-user database. The open snapshot reads once, so
     * "not ready yet" must not look like "nothing stored": a null here would become "no inbound
     * messages at open" and every stored row would later count as an arrival.
     */
    private suspend fun awaitReady(): ID {
        val selfId = userManager.state.map { it.accountId }.filterNotNull().first()
        messageDataSource.awaitDatabase()
        return selfId
    }

    override suspend fun loadHistoryThrough(chatId: ChatId, messageId: Long): Boolean {
        if (messageDataSource.getMessage(chatId, messageId) != null) return true
        var oldest = messageDataSource.oldestMessageId(chatId)
        var loaded = 0
        while (loaded < MAX_HISTORY_LOAD) {
            // Already past it: the target is older than what is stored and was never sent back.
            if (oldest != null && oldest <= messageId) return false
            val page = messagingController.getMessages(
                chatId,
                QueryOptions(
                    limit = HISTORY_PAGE_SIZE,
                    token = oldest?.toPagingToken(),
                    descending = true,
                ),
            ).getOrNull() ?: return false
            if (page.isEmpty()) return false
            messageDataSource.upsert(chatId, page)
            loaded += page.size
            if (page.any { it.messageId <= messageId }) {
                return messageDataSource.getMessage(chatId, messageId) != null
            }
            if (page.size < HISTORY_PAGE_SIZE) return false
            oldest = page.minOf { it.messageId }
        }
        return false
    }

    override suspend fun resolveUnreadBoundary(chatId: ChatId): UnreadBoundary {
        val selfId = awaitReady()
        val readThrough = memberDataSource.getSelfReadPointerOrNull(chatId, selfId)
            ?: return UnreadBoundary.None
        val count = messageDataSource.countInboundAfter(chatId, selfId, readThrough)
        if (count <= 0) return UnreadBoundary.None
        // The list places the divider by comparing neighbours, so it cannot see past the viewer's
        // own messages right after the pointer. Resolving the boundary past them puts the divider
        // above the first message someone else sent.
        val firstNotOwn = messageDataSource.firstNotSentByAfter(chatId, selfId, readThrough)
        val boundary = firstNotOwn?.minus(1) ?: readThrough
        // With nothing stored at or below the boundary, older unread messages may never have been
        // fetched, so the count could come out short. No divider beats a wrong one.
        if (!messageDataSource.hasAtOrBelow(chatId, boundary)) return UnreadBoundary.None
        return UnreadBoundary.At(readThrough = boundary, count = count)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeUnreadBelow(chatId: ChatId): Flow<Int> =
        // The account id is set during login, and the per-user DB is made then too (the data
        // sources wait on it): a screen that subscribes before either is ready counts 0 until they
        // are, and starts over if the account changes.
        userManager.state.map { it.accountId }
            .distinctUntilChanged()
            .flatMapLatest { selfId ->
                if (selfId == null) flowOf(0)
                else memberDataSource.observeSelfReadPointer(chatId, selfId)
                    .flatMapLatest { readThrough ->
                        if (readThrough == null) flowOf(0)
                        else messageDataSource.observeInboundCountAfter(chatId, selfId, readThrough)
                    }
            }
            .distinctUntilChanged()

    override suspend fun newestInboundId(chatId: ChatId): Long? {
        val selfId = awaitReady()
        return messageDataSource.newestInboundId(chatId, selfId)
    }

    override suspend fun selfReadPointer(chatId: ChatId): Long? {
        val selfId = awaitReady()
        return memberDataSource.getSelfReadPointerOrNull(chatId, selfId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeInboundAfter(chatId: ChatId, afterId: Long): Flow<Int> =
        userManager.state.map { it.accountId }
            .distinctUntilChanged()
            .flatMapLatest { selfId ->
                if (selfId == null) flowOf(0)
                else messageDataSource.observeInboundCountAfter(chatId, selfId, afterId)
            }
            .distinctUntilChanged()

    override suspend fun countMessagesAfter(chatId: ChatId, messageId: Long): Int =
        messageDataSource.countAfter(chatId, messageId)

    override fun observeMembers(chatId: ChatId): Flow<List<ChatMember>> {
        return memberDataSource.observeMembers(chatId)
    }

    override fun observeMetadata(chatId: ChatId): Flow<ChatMembership?> =
        combine(
            metadataDataSource.observeById(chatId),
            memberDataSource.observeMembers(chatId),
        ) { entity, members ->
            entity ?: return@combine null
            val lastMessage = entity.lastMessageId
                ?.let { messageDataSource.getLatestVisible(entity.chatIdHex) }
            ChatMembership(
                metadata = metadataDataSource.toMetadata(entity, members, lastMessage),
                isMember = entity.isMember,
            )
        }.distinctUntilChanged()

    override fun observeSenderProfiles(): Flow<Map<String, UserProfile>> = senderResolver.profiles

    override fun currentSenderProfiles(): Map<String, UserProfile>? = senderResolver.cachedProfiles

    override fun requestSenderProfile(userId: ID) = senderResolver.request(userId)

    override fun observeOldestEncryptedMessageId(chatId: ChatId): Flow<Long?> =
        messageDataSource.observeOldestEncryptedMessageId(chatId)

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

    private val newestPageLoaded = MutableSharedFlow<ChatId>(extraBufferCapacity = 16)

    override fun newestPageLoads(): Flow<ChatId> = newestPageLoaded

    override suspend fun loadMessages(chatId: ChatId) {
        messagingController.getMessages(chatId)
            .onSuccess { messages ->
                messageDataSource.upsert(chatId, messages)

                // Seat the event-log cursor at the newest page's frontier. It is what marks this
                // transcript as fetched, and it lets a following catch-up resume from head and
                // append genuinely newer messages instead of re-pulling the whole history from
                // sequence 0. Only ever advanced — a page older than the cursor must not rewind it.
                // One write, and one that changes nothing when this page has nothing newer than
                // what the feed already stored: the list rebuilds only for a real change.
                val head = messages.maxOfOrNull { it.eventSequence } ?: 0L
                metadataDataSource.applyCatchUp(chatId, head, messages.maxByOrNull { it.messageId })
                newestPageLoaded.tryEmit(chatId)
            }
    }

    override suspend fun applyPushedMessage(chatId: ChatId, message: ChatMessage) {
        // The DAO's upsert already drops a copy older than the stored row, so the only
        // ordering this has to protect is the metadata below it.
        messageDataSource.upsert(chatId, listOf(message))

        // Deliberately not advancing the event-log cursor. A push carries one message, not
        // a page, so seating the cursor at its sequence would let a later catch-up resume
        // from a frontier it never actually fetched and skip whatever it missed in between.
        // The row the list reads is updated here, so a message that arrived by push is already
        // on the list at the next cold launch.
        if (message.messageId > (metadataDataSource.getLastMessageId(chatId) ?: 0L)) {
            metadataDataSource.applyCatchUp(chatId, 0L, message)
        }
    }

    override suspend fun openPushedMessage(chatId: ChatId, message: ChatMessage?, messageId: Long?): String? =
        openPushedChatMessage(chatId, message, messageId)?.content?.singleOrNull()?.pushText()

    override suspend fun openPushedChatMessage(
        chatId: ChatId,
        message: ChatMessage?,
        messageId: Long?,
    ): ChatMessage? {
        val opener = incoming ?: return null
        val selfId = userManager.accountId ?: return null
        val candidate = message
            ?: messageId?.let { id ->
                messageDataSource.getMessage(chatId, id)
                    ?: messagingController.getMessage(chatId, id).getOrNull()
            }
            ?: return null

        val opened = when (candidate.encryption) {
            is MessageEncryption.Decrypted -> candidate
            null -> {
                if (candidate.content.singleOrNull() !is MessageContent.Encrypted) return null
                // Only the viewer's own message needs the other member; anyone else's is theirs.
                val peerId = if (candidate.senderId == selfId) getOtherMember(chatId)?.userId else null
                opener.open(chatId, selfId, peerId, listOf(candidate), stored = { null }).single()
            }
            else -> return null
        }
        return opened.takeIf { it.encryption is MessageEncryption.Decrypted }
    }

    override suspend fun readMessage(chatId: ChatId, messageId: Long): Result<ChatMessage> = runCatching {
        messageDataSource.getMessage(chatId, messageId)?.let { return@runCatching it }
        val fetched = messagingController.getMessage(chatId, messageId).getOrThrow()
        val opener = incoming
        val selfId = userManager.accountId
        if (opener == null || selfId == null || fetched.content.singleOrNull() !is MessageContent.Encrypted) {
            return@runCatching fetched
        }
        val peerId = if (fetched.senderId == selfId) getOtherMember(chatId)?.userId else null
        opener.open(chatId, selfId, peerId, listOf(fetched), stored = { null }).single()
    }

    override suspend fun peekMessage(chatId: ChatId, messageId: Long): Result<ChatMessage> =
        messagingController.getMessage(chatId, messageId, ViewMode.FULL_OR_REDACTED)

    override suspend fun getPinnedMessages(chatId: ChatId): Result<PinnedMessages> = runCatching {
        val pins = mutableListOf<Pin>()
        var token: PagingToken? = null
        var version: Long? = null
        do {
            val page = chatController.getPinnedMessages(
                chatId,
                QueryOptions(token = token),
                ViewMode.FULL_OR_REDACTED,
            ).getOrThrow()
            pins += page.pins
            // The first page's: a later page read after another pin landed must not claim a version
            // whose pins this list doesn't hold.
            if (version == null) version = page.pinSummary.version
            token = page.pagingToken
        } while (page.hasMore && token != null)
        PinnedMessages(
            pins = pins.map { pin -> pin.copy(message = readablePinned(chatId, pin.message)) },
            version = version ?: 0,
        )
    }

    /** The stored copy, opened on upsert, when there is one; otherwise [message] opened the way [readMessage] does. */
    private suspend fun readablePinned(chatId: ChatId, message: ChatMessage): ChatMessage {
        messageDataSource.getMessage(chatId, message.messageId)?.let { return it }
        val opener = incoming
        val selfId = userManager.accountId
        if (opener == null || selfId == null || message.content.singleOrNull() !is MessageContent.Encrypted) {
            return message
        }
        val peerId = if (message.senderId == selfId) getOtherMember(chatId)?.userId else null
        return opener.open(chatId, selfId, peerId, listOf(message), stored = { null }).single()
    }

    override suspend fun pinMessage(chatId: ChatId, messageId: Long): Result<PinSummary> =
        chatController.pinMessage(chatId, messageId)

    override suspend fun unpinMessage(chatId: ChatId, messageId: Long): Result<PinSummary> =
        chatController.unpinMessage(chatId, messageId)

    /** Text, photos and replies to either are what DMs encrypt; anything else keeps the server's body. */
    private fun MessageContent.pushText(): String? = when (this) {
        is MessageContent.Text -> text
        is MessageContent.Media -> ChatMediaText.pushText(caption?.text)
        is MessageContent.Reply -> content.singleOrNull()?.pushText()
        else -> null
    }

    override suspend fun sendMessage(
        chatId: ChatId,
        content: String,
        replyToMessageId: Long?,
        linkPreview: OutgoingLinkPreview?,
    ): Result<ChatMessage> {
        if (content.isBlank()) {
            return Result.failure(IllegalArgumentException("Cannot send a blank message"))
        }

        val senderId = userManager.accountId
            ?: return Result.failure(IllegalStateException("Cannot send message without an account"))

        val preview = linkPreview?.takeIf { LinkPreviewChecks.wholeLink(content, it.url) }
        fun payload(image: MediaItem?): List<MessageContent> {
            val body = listOf(
                MessageContent.Text(
                    text = content,
                    linkPreview = preview?.let { LinkPreview(it.url, it.title, it.description, image) },
                ),
            )
            // The optimistic row and the request carry the same payload, so the quote renders
            // before the server answers rather than appearing when it does.
            return replyToMessageId
                ?.let { listOf(MessageContent.Reply(repliedMessageId = it, content = body)) }
                ?: body
        }

        val (_, clientMessageId) = messageDataSource.insertPending(
            chatId = chatId,
            content = payload(image = null),
            senderId = senderId,
        )
        if (preview == null) linkPreview?.imageUploadId?.let(previewImages::discard)

        val image = preview?.imageUploadId?.let { previewImages.settle(it) }
            ?: return send(chatId, payload(image = null), clientMessageId)

        // Rewritten with the image, so a retry of a failed send keeps it.
        val withImage = payload(image.item)
        messageDataSource.insertPending(chatId, withImage, senderId, clientMessageId, ordinal = 0)
        return send(chatId, withImage, clientMessageId)
    }

    override suspend fun sendPaymentRequest(chatId: ChatId, requestId: ID): Result<ChatMessage> {
        val senderId = userManager.accountId
            ?: return Result.failure(IllegalStateException("Cannot send message without an account"))
        val payload = listOf(MessageContent.Widget(WidgetContent.PaymentRequest(requestId)))
        val (_, clientMessageId) = messageDataSource.insertPending(chatId = chatId, content = payload, senderId = senderId)
        val pendingClientIdHex = clientMessageId.bytes.toList().hexEncodedString()
        // The request already exists, so only the message is retried, on the same pending row.
        var result = send(chatId, payload, clientMessageId)
        for (attempt in 1..PAYMENT_REQUEST_SEND_RETRIES) {
            val cause = result.exceptionOrNull() ?: break
            if (cause is SendMessageError.Denied) break
            delay(PAYMENT_REQUEST_RETRY_BACKOFF * attempt)
            messageDataSource.retryPending(chatId, pendingClientIdHex)
            result = send(chatId, payload, clientMessageId)
        }
        return result.recoverCatching { cause -> throw PaymentRequestNotSent(pendingClientIdHex, cause) }
    }

    override suspend fun retryMessage(chatId: ChatId, pendingClientIdHex: String, content: List<MessageContent>): Result<ChatMessage> {
        val clientMessageId = messageDataSource.retryPending(chatId, pendingClientIdHex)

        // Sealed afresh: the pending row holds plaintext, and the chat may have started or stopped
        // encrypting since the first attempt.
        return send(chatId, content, clientMessageId)
    }

    /**
     * Seals [payload] if the chat encrypts, sends it, and settles the pending row. A failure to
     * seal fails the row like a failed request, so it can be retried; so does the server refusing
     * the ciphertext with `EncryptionNotAllowed`. A `Denied` settles the row as denied, with no retry.
     */
    private suspend fun send(
        chatId: ChatId,
        payload: List<MessageContent>,
        clientMessageId: ClientMessageId,
    ): Result<ChatMessage> {
        val prepared = outgoing.prepare(chatId, payload, blobSealedFor = payload.previewImageSealedFor(chatId))
            .getOrElse { cause ->
                trace(tag = TAG, message = "Couldn't prepare send in $chatId", type = TraceType.Error, error = cause)
                messageDataSource.failPending(chatId, clientMessageId)
                return Result.failure(cause)
            }

        return messagingController.sendMessage(chatId, prepared.wire, clientMessageId)
            .map(prepared::echo)
            .onSuccess { serverMessage ->
                messageDataSource.confirmPending(chatId, clientMessageId, serverMessage)
                advanceReadPointer(chatId, serverMessage.messageId)

                metadataDataSource.updateLastMessageId(chatId, serverMessage.messageId)
                metadataDataSource.updateLastActivity(chatId, serverMessage.timestamp.toEpochMilliseconds())
            }
            .onFailure { cause ->
                // The server won't take this from this user in this chat; a retry gets the same answer.
                if (cause is SendMessageError.Denied) {
                    messageDataSource.denyPending(chatId, clientMessageId)
                    rereadAfterDenied(chatId)
                } else {
                    messageDataSource.failPending(chatId, clientMessageId)
                }
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

        // A sealed message's preview image was sealed for this chat along with it.
        val prepared = outgoing.prepare(
            chatId,
            content,
            wasEncrypted = stored.encryption != null,
            blobSealedFor = chatId.takeIf { stored.encryption != null },
        )
            .getOrElse { cause ->
                clearMutation(chatId, messageId)
                return Result.failure(cause)
            }

        return messagingController.editMessage(chatId, messageId, prepared.wire, expectedSequence)
            .map(prepared::echo)
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
                ?.firstOrNull { it.chatId == chatId }
                ?.lastMessage?.messageId
            ?: return Result.success(Unit)
        return advanceReadPointer(chatId, messageId)
            .also { dismissNotifications(chatId) }
    }

    override suspend fun notifyTyping(chatId: ChatId, typingState: TypingState): Result<Unit> {
        return messagingController.notifyIsTyping(chatId, typingState)
    }

    override suspend fun mute(chatId: ChatId, mute: MuteState): Result<Unit> =
        chatController.muteChat(chatId, mute).storeViewerState(chatId)

    override suspend fun unmute(chatId: ChatId): Result<Unit> =
        chatController.unmuteChat(chatId).storeViewerState(chatId)

    /**
     * Stores the viewer state a mute call answered with, through the same version gate the stream
     * writes go through: the call's own echo can arrive on the stream first, and either order has
     * to land on the same row.
     */
    private suspend fun Result<ViewerState>.storeViewerState(chatId: ChatId): Result<Unit> =
        onSuccess { metadataDataSource.updateViewerState(chatId, it) }.map { }

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
                val amount = tip.amount.analytics.copy(mint = tip.mint.analytics)
                analytics.track(ChatEvents.tipReceived(chatType.analytics, amount))
            } else {
                analytics.track(ChatEvents.messageReceived(chatType.analytics))
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

    /**
     * Fails the sends a previous process left in flight, so the transcript offers to retry them
     * instead of spinning on a send that nothing is still attempting.
     *
     * A send is marked failed from the `onFailure` of the coroutine that issued it ([sendMessage],
     * [retryMessage]). Kill the app mid-send and that coroutine dies with the process, leaving a
     * `SENDING` row on disk that nothing will ever move — until the first refresh deletes it and the
     * user's text with it.
     *
     * Must complete before the first refresh of this session. The refresh path
     * ([com.flipcash.app.persistence.sources.ChatMessageDataSource.upsert] on a self-authored
     * message) deletes `SENDING` rows and spares `FAILED` ones, so sweeping first makes them
     * durable and sweeping second does nothing at all. That is why this is `suspend` and awaited by
     * [com.flipcash.shared.chat.internal.RealChatCoordinator.onUserLoggedIn] rather than launched
     * from an `initialize(scope)` hook like the other delegates' — the refresh is itself a `launch`,
     * and two coroutines would leave the order to chance.
     *
     * It first repairs chats an older version left with two rows under one client id, which crash
     * the transcript on open. A check that finds none costs one pass over the table.
     */
    internal suspend fun recoverInterruptedSends() {
        // A chat holding two rows under one client id crashes the transcript on open; see
        // ChatMessageDao.clearDuplicateClientIds.
        messageDataSource.clearDuplicateClientIds()
        messageDataSource.failInterruptedSends()
    }

    /** Opens the encrypted messages still waiting on a key, in every chat. */
    internal suspend fun openKeyPending() {
        messageDataSource.reopenAllKeyPending()
    }

    internal suspend fun clear() {
        pendingMutations.value = emptyMap()
        metadataDataSource.clear()
        messageDataSource.clear()
        memberDataSource.clear()
        senderResolver.clear()
    }

    /**
     * The server refused a send without saying why. If a speaker rule changed, the stored rules are
     * stale and the composer is still up; re-reading the chat lets `speakerBlock` see the new rules.
     * A removal from the group is not visible here: GetChat does not return the caller's membership.
     */
    internal suspend fun rereadAfterDenied(chatId: ChatId) {
        val metadata = chatController.getChat(chatId).getOrElse {
            trace(tag = TAG, message = "Couldn't re-read $chatId after a denied send", type = TraceType.Silent)
            return
        }
        // A removal may already have landed from the stream; don't write the caller back in.
        metadataDataSource.upsertKeepingMembership(metadata)
        memberDataSource.upsert(chatId, metadata.members)
    }

    // endregion

    private companion object {
        const val TAG = "MessagingDelegate"
        /** Retries after the first failed send of a payment request's widget. */
        const val PAYMENT_REQUEST_SEND_RETRIES = 2
        val PAYMENT_REQUEST_RETRY_BACKOFF = 1.seconds

        /** The transcript's page size. */
        const val HISTORY_PAGE_SIZE = 50

        /** The transcript's jump bound (MessageList MAX_JUMP_ITEMS): a walk this long gives up. */
        const val MAX_HISTORY_LOAD = 5_000
    }
}

/**
 * The chat a preview image in this payload was sealed for, read from the item [sendMessage] built:
 * a sealed image carries its metadata with no download url, a plain one carries none. Read off the
 * content so a retry, which only has the stored row, answers the same as the first attempt.
 */
private fun List<MessageContent>.previewImageSealedFor(chatId: ChatId): ChatId? {
    val image = firstNotNullOfOrNull { content ->
        when (content) {
            is MessageContent.Text -> content.linkPreview?.image
            is MessageContent.Reply -> content.content.filterIsInstance<MessageContent.Text>().firstNotNullOfOrNull { it.linkPreview?.image }
            else -> null
        }
    } ?: return null
    val blob = image.renditions.firstOrNull()?.blob
    return chatId.takeIf { blob != null && blob.downloadUrl.isEmpty() }
}

/** A message id as a paging token, as [ChatMessageRemoteMediator] writes it. */
private fun Long.toPagingToken(): List<Byte> =
    ByteBuffer.allocate(Long.SIZE_BYTES).putLong(this).array().toList()
