package com.flipcash.app.persistence.sources

import androidx.paging.PagingSource
import androidx.room.withTransaction
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.dao.ChatMessageDao
import com.flipcash.app.persistence.entities.ChatMessageEntity
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.services.chat.MessageEncryption
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.ReactionSummary
import com.flipcash.app.persistence.entities.MessageStatus
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.persistence.PagingDataSource
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.RandomId
import com.getcode.utils.hexEncodedString
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class PendingMessage(
    val message: ChatMessage,
    val clientMessageId: ClientMessageId,
)

/** Messages already opened by [ChatMessageDataSource.prepare], ready for one write. */
class PreparedMessages internal constructor(internal val byChat: Map<ChatId, List<ChatMessage>>)

@Singleton
class ChatMessageDataSource @Inject constructor(
    private val mapper: ChatEntityMapper,
    private val userManager: UserManager,
    private val opener: IncomingMessageOpener,
) : PagingDataSource<Long, ChatMessage, List<ChatMessage>, Int, ChatMessageEntity> {

    private val db: FlipcashDatabase?
        get() = FlipcashDatabase.getInstance()

    private var activeChatId: ChatId? = null

    fun setActiveChatId(chatId: ChatId) {
        activeChatId = chatId
    }

    /**
     * Reactive "has the user ever sent a tip" — an outgoing Cash message (self) with verb TIPPED.
     *
     * Both inputs are resolved reactively rather than read once at subscription time. The per-user
     * DB is created at login and the account id is set during it, so a subscriber that starts before
     * either is ready (the wallet tab composing while a soft login is still in flight) used to latch
     * onto a constant `false` for the whole session — pinning the onboarding checklist to
     * "incomplete" and leaving the new-user tutorial on screen for an established account.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun hasEverTipped(): Flow<Boolean> =
        combine(
            FlipcashDatabase.observeInstance(),
            userManager.state.map { it.accountId }.distinctUntilChanged(),
        ) { database, accountId -> database to accountId }
            .flatMapLatest { (database, accountId) ->
                val selfHex = accountId?.hexEncodedString() ?: return@flatMapLatest flowOf(false)
                database?.chatMessageDao()?.hasEverTipped(selfHex) ?: flowOf(false)
            }

    // region PagingDataSource

    override fun observe(): PagingSource<Int, ChatMessageEntity> {
        val id = activeChatId ?: return emptyPagingSource()
        return db?.chatMessageDao()?.observeMessagesPaged(mapper.chatIdHex(id))
            ?: emptyPagingSource()
    }

    override suspend fun getById(id: Long): ChatMessage? {
        val hex = activeChatId?.let { mapper.chatIdHex(it) } ?: return null
        return db?.chatMessageDao()?.getLatest(hex)?.let { toChatMessage(it) }
    }

    override suspend fun get(): List<ChatMessage> {
        val hex = activeChatId?.let { mapper.chatIdHex(it) } ?: return emptyList()
        return db?.chatMessageDao()?.observeMessages(hex)
            ?.firstOrNull()
            ?.map { toChatMessage(it) }
            ?: emptyList()
    }

    override suspend fun getMostRecent(): ChatMessage? {
        val hex = activeChatId?.let { mapper.chatIdHex(it) } ?: return null
        return getLatest(hex)
    }

    override suspend fun query(whereClause: String): List<ChatMessage> = emptyList()

    override suspend fun upsert(value: List<ChatMessage>) {
        val chatId = activeChatId ?: return
        upsert(chatId, value)
    }

    override suspend fun clear() {
        db?.chatMessageDao()?.deleteAll()
    }

    // endregion

    fun observeForChat(chatId: ChatId): PagingSource<Int, ChatMessageEntity> {
        return db?.chatMessageDao()?.observeMessagesPaged(mapper.chatIdHex(chatId))
            ?: emptyPagingSource()
    }

    fun observeMessages(chatId: ChatId): Flow<List<ChatMessage>> =
        db?.chatMessageDao()?.observeMessages(mapper.chatIdHex(chatId))?.map { entities ->
            entities.map { toChatMessage(it) }
        } ?: emptyFlow()

    /** The newest [limit] confirmed messages [selfId] sent in [chatId], newest first. */
    suspend fun getRecentSentBy(chatId: ChatId, selfId: ID, limit: Int): List<ChatMessage> =
        db?.chatMessageDao()
            ?.getRecentSentBy(mapper.chatIdHex(chatId), selfId.hexEncodedString(), limit)
            .orEmpty()
            .map { toChatMessage(it) }

    /** The optimistic row of [clientMessageId], whatever its status; null once it was confirmed or dropped. */
    suspend fun getPending(chatId: ChatId, clientMessageId: ClientMessageId): ChatMessage? =
        db?.chatMessageDao()
            ?.getByClientId(mapper.chatIdHex(chatId), mapper.clientMessageIdHex(clientMessageId))
            ?.let { toChatMessage(it) }

    suspend fun getLatest(chatIdHex: String): ChatMessage? =
        db?.chatMessageDao()?.getLatest(chatIdHex)?.let { toChatMessage(it) }

    /** The newest message that isn't a tombstone — what the conversation list previews. */
    suspend fun getLatestVisible(chatIdHex: String): ChatMessage? =
        db?.chatMessageDao()?.getLatestVisible(chatIdHex)?.let { toChatMessage(it) }

    /** [getLatestVisible] for every chat, keyed by chat id hex; chats with none are absent. */
    suspend fun getLatestVisibleByChat(): Map<String, ChatMessage> =
        db?.chatMessageDao()?.getLatestVisibleForAllChats()
            ?.associate { it.chatIdHex to toChatMessage(it) }
            .orEmpty()

    /**
     * Emits whenever the set of newest-visible messages changes (one per chat). Carries the rows
     * only so callers can de-duplicate on them; the list reads previews with
     * [getLatestVisibleByChat].
     */
    fun observeLatestVisibleChanges(): Flow<List<Any>> =
        db?.chatMessageDao()?.observeLatestVisibleForAllChats()?.distinctUntilChanged() ?: emptyFlow()

    /**
     * The server's unread stamp on one stored message, or null when that message isn't stored —
     * the running count of unread-eligible messages, so two stamps subtract to the count between.
     */
    suspend fun getUnreadSeq(chatIdHex: String, messageId: Long): Long? =
        db?.chatMessageDao()?.getUnreadSeq(chatIdHex, messageId)

    suspend fun hasMessages(chatId: ChatId): Boolean =
        db?.chatMessageDao()?.getLatest(mapper.chatIdHex(chatId)) != null

    suspend fun getLatestMessageId(chatId: ChatId): Long? =
        db?.chatMessageDao()?.getLatest(mapper.chatIdHex(chatId))?.messageId

    /** The locally-stored copy of a single message, or `null` if this device has never seen it. */
    suspend fun getMessage(chatId: ChatId, messageId: Long): ChatMessage? =
        db?.chatMessageDao()?.getMessage(mapper.chatIdHex(chatId), messageId)?.let { toChatMessage(it) }

    /** How far back [messageId] sits from the newest message in [chatId], or `null` if unknown. */
    suspend fun distanceFromNewest(chatId: ChatId, messageId: Long): Int? {
        val dao = db?.chatMessageDao() ?: return null
        val hex = mapper.chatIdHex(chatId)
        val stored = dao.getMessage(hex, messageId) ?: return null
        return dao.countNewerThan(hex, stored.timestampEpochMs)
    }

    /** Stored, non-deleted messages in [chatId] past [afterId] that someone other than [selfId] sent. */
    suspend fun countInboundAfter(chatId: ChatId, selfId: ID, afterId: Long): Int =
        db?.chatMessageDao()?.countInboundAfter(
            chatIdHex = mapper.chatIdHex(chatId),
            selfIdHex = mapper.userIdHex(selfId),
            afterId = afterId,
        ) ?: 0

    /** The first stored message in [chatId] past [afterId] that [selfId] did not send, or `null`. */
    suspend fun firstNotSentByAfter(chatId: ChatId, selfId: ID, afterId: Long): Long? =
        db?.chatMessageDao()?.firstNotSentByAfter(
            chatIdHex = mapper.chatIdHex(chatId),
            selfIdHex = mapper.userIdHex(selfId),
            afterId = afterId,
        )

    /** Whether any message in [chatId] at or below [id] is stored, deleted or not. */
    suspend fun hasAtOrBelow(chatId: ChatId, id: Long): Boolean =
        db?.chatMessageDao()?.hasAtOrBelow(mapper.chatIdHex(chatId), id) ?: false

    /** Every stored message in [chatId] past [afterId], deleted or not. */
    suspend fun countAfter(chatId: ChatId, afterId: Long): Int =
        db?.chatMessageDao()?.countAfter(mapper.chatIdHex(chatId), afterId) ?: 0

    suspend fun getInboundMessagesInRange(
        chatId: ChatId,
        selfId: ID,
        afterId: Long,
        throughId: Long,
    ): List<ChatMessage> =
        db?.chatMessageDao()?.getInboundMessagesInRange(
            chatIdHex = mapper.chatIdHex(chatId),
            selfIdHex = mapper.userIdHex(selfId),
            afterId = afterId,
            throughId = throughId,
        )?.map { toChatMessage(it) }.orEmpty()

    suspend fun upsert(chatId: ChatId, messages: List<ChatMessage>) {
        val hex = mapper.chatIdHex(chatId)
        val opened = openEncrypted(chatId, hex, messages)
        write(hex, opened)
        // A write that got a key is the next chance to open what an earlier one couldn't.
        if (opened.none { it.encryption == MessageEncryption.KeyPending }) {
            reopenKeyPending(chatId)
        }
    }

    /**
     * Opens the messages in [chatId] stored [MessageEncryption.KeyPending]: those whose key fetch
     * failed, or that arrived before the other member of the DM was stored. Cheap when there are
     * none. Called on each write to the chat, when the chat is opened, and on a push for it.
     */
    suspend fun reopenKeyPending(chatId: ChatId) {
        val dao = db?.chatMessageDao() ?: return
        val hex = mapper.chatIdHex(chatId)
        if (!dao.hasKeyPending(hex)) return
        val selfId = userManager.accountId
        val peerId = peerOf(hex, selfId)
        val reopened = dao.getKeyPending(hex).map { entity ->
            opener.reopen(chatId, selfId, peerId, mapper.toMessage(entity))
        }
        if (reopened.all { it.encryption == MessageEncryption.KeyPending }) return
        dao.upsert(reopened.map { mapper.toEntity(hex, it) })
    }

    /** [reopenKeyPending] for every chat that has a message waiting. */
    suspend fun reopenAllKeyPending() {
        val dao = db?.chatMessageDao() ?: return
        for (hex in dao.chatsWithKeyPending()) reopenKeyPending(mapper.chatIdFromHex(hex))
    }

    /**
     * The id of the oldest end-to-end encrypted message in [chatId] the transcript shows, opened
     * or not; the Encrypted marker sits above it.
     */
    fun observeOldestEncryptedMessageId(chatId: ChatId): Flow<Long?> =
        db?.chatMessageDao()?.observeOldestEncrypted(mapper.chatIdHex(chatId)) ?: flowOf(null)

    private suspend fun openEncrypted(chatId: ChatId, hex: String, messages: List<ChatMessage>): List<ChatMessage> {
        if (messages.none { it.encryption == null && it.content.singleOrNull() is MessageContent.Encrypted }) {
            return messages
        }
        val dao = db?.chatMessageDao() ?: return messages
        val selfId = userManager.accountId
        return opener.open(
            chatId = chatId,
            selfId = selfId,
            peerId = peerOf(hex, selfId),
            messages = messages,
            stored = { messageId -> dao.getMessage(hex, messageId)?.let(mapper::toMessage) },
        )
    }

    /** The DM member who isn't the viewer, when the chat's members are stored. */
    private suspend fun peerOf(chatIdHex: String, selfId: ID?): ID? {
        val selfHex = selfId?.hexEncodedString() ?: return null
        return db?.chatMemberDao()?.getMembersForChat(chatIdHex)
            ?.map { it.member.userIdHex }
            ?.singleOrNull { it != selfHex }
            ?.let(mapper::userIdFromHex)
    }

    private suspend fun write(hex: String, messages: List<ChatMessage>) {
        val entities = messages.map { mapper.toEntity(hex, it) }
        val selfHex = userManager.accountId?.hexEncodedString()
        val dao = db?.chatMessageDao() ?: return
        if (selfHex != null && entities.any { it.senderIdHex == selfHex }) {
            // Carries pending ids onto their server copies, see ChatMessageDao.upsertRescuingPending.
            dao.upsertRescuingPending(hex, selfHex, entities)
        } else {
            dao.upsert(entities)
        }
    }

    /**
     * [upsert] for several chats in one transaction. Room invalidates `chat_messages` as a whole,
     * so a feed sync writing each chat's preview separately re-pages the open transcript once per
     * chat in the feed.
     */
    suspend fun upsertAll(messagesByChat: Map<ChatId, List<ChatMessage>>) {
        if (db == null || messagesByChat.isEmpty()) return
        val prepared = prepare(messagesByChat)
        writePrepared(prepared)
        afterWrite(prepared)
    }

    /**
     * The network half of [upsertAll]: opens what can be opened. Split out so a caller can run it
     * before it takes a transaction, since opening can fetch a key.
     */
    suspend fun prepare(messagesByChat: Map<ChatId, List<ChatMessage>>): PreparedMessages =
        PreparedMessages(
            messagesByChat.mapValues { (chatId, messages) ->
                openEncrypted(chatId, mapper.chatIdHex(chatId), messages)
            },
        )

    /** The write half of [upsertAll]. Joins a transaction the caller already holds. */
    suspend fun writePrepared(prepared: PreparedMessages) {
        val database = db ?: return
        if (prepared.byChat.isEmpty()) return
        database.withTransaction {
            for ((chatId, messages) in prepared.byChat) write(mapper.chatIdHex(chatId), messages)
        }
    }

    /** Runs once the transaction that carried [prepared] has committed. */
    suspend fun afterWrite(prepared: PreparedMessages) {
        for ((chatId, messages) in prepared.byChat) {
            if (messages.none { it.encryption == MessageEncryption.KeyPending }) reopenKeyPending(chatId)
        }
    }

    suspend fun insertPending(
        chatId: ChatId,
        content: List<MessageContent>,
        senderId: ID,
    ): PendingMessage = insertPending(chatId, content, senderId, ClientMessageId(RandomId.toByteArray()), ordinal = 0)

    /** [insertPending] under a client id the caller already made, at [ordinal] among rows sent together. */
    suspend fun insertPending(
        chatId: ChatId,
        content: List<MessageContent>,
        senderId: ID,
        clientMessageId: ClientMessageId,
        ordinal: Int,
    ): PendingMessage {
        val entity = mapper.toPendingEntity(
            chatIdHex = mapper.chatIdHex(chatId),
            content = content,
            senderId = senderId,
            clientMessageId = clientMessageId,
            ordinal = ordinal,
        )
        db?.chatMessageDao()?.upsert(entity)
        return PendingMessage(
            message = mapper.toMessage(entity).copy(isFromSelf = true),
            clientMessageId = clientMessageId,
        )
    }

    /**
     * [replaceContent] is for a photo, whose optimistic row holds the local file: the confirmed row
     * takes [serverMessage]'s content instead of keeping what was written locally.
     */
    suspend fun confirmPending(
        chatId: ChatId,
        clientMessageId: ClientMessageId,
        serverMessage: ChatMessage,
        replaceContent: Boolean = false,
    ) {
        val hex = mapper.chatIdHex(chatId)
        db?.chatMessageDao()?.confirmPendingMessage(
            hex,
            mapper.clientMessageIdHex(clientMessageId),
            mapper.toEntity(hex, serverMessage),
            replaceContent,
        )
    }

    suspend fun failPending(chatId: ChatId, clientMessageId: ClientMessageId) {
        db?.chatMessageDao()?.updatePendingStatus(
            mapper.chatIdHex(chatId),
            mapper.clientMessageIdHex(clientMessageId),
            MessageStatus.FAILED,
        )
    }

    /**
     * Fails the sends a previous process left in flight. See [ChatMessageDao.failInterruptedSends].
     */
    suspend fun failInterruptedSends() {
        db?.chatMessageDao()?.failInterruptedSends()
    }

    suspend fun retryPending(chatId: ChatId, pendingClientIdHex: String): ClientMessageId {
        val clientMessageId = mapper.clientMessageIdFromHex(pendingClientIdHex)
        db?.chatMessageDao()?.updatePendingStatus(
            mapper.chatIdHex(chatId),
            pendingClientIdHex,
            MessageStatus.SENDING,
        )
        return clientMessageId
    }

    /**
     * Merges [summary] onto the message's stored `reactions_json`, per emoji keeping whichever
     * side has the higher `version` (see [ChatMessageDao.mergeReactionsJson]). A no-op if the
     * message isn't stored yet — a reaction event for a message this device hasn't seen is
     * dropped rather than written as a bare row.
     */
    suspend fun mergeReactions(chatId: ChatId, messageId: Long, summary: ReactionSummary) {
        val json = mapper.encodeReactions(summary) ?: return
        db?.chatMessageDao()?.mergeReactionsJson(mapper.chatIdHex(chatId), messageId, json)
    }

    /** [mergeReactions] for several messages of [chatId], written in one transaction. */
    suspend fun mergeReactions(chatId: ChatId, summaries: List<ReactionSummary>) {
        val jsonByMessageId = summaries.mapNotNull { summary ->
            mapper.encodeReactions(summary)?.let { summary.messageId to it }
        }.toMap()
        if (jsonByMessageId.isEmpty()) return
        db?.chatMessageDao()?.mergeReactionsJson(mapper.chatIdHex(chatId), jsonByMessageId)
    }

    fun toChatMessage(entity: ChatMessageEntity): ChatMessage {
        val message = mapper.toMessage(entity)
        val selfId = userManager.accountId
        return message.copy(isFromSelf = selfId != null && message.senderId == selfId)
    }

    private fun emptyPagingSource() = object : PagingSource<Int, ChatMessageEntity>() {
        override fun getRefreshKey(state: androidx.paging.PagingState<Int, ChatMessageEntity>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ChatMessageEntity> =
            LoadResult.Error(Exception("Database not initialized"))
    }
}

/** Each chat's last message, keyed by chat — the previews a feed page carries for [ChatMessageDataSource.upsertAll]. */
fun List<ChatMetadata>.lastMessagesByChat(): Map<ChatId, List<ChatMessage>> =
    mapNotNull { chat -> chat.lastMessage?.let { chat.chatId to listOf(it) } }.toMap()
