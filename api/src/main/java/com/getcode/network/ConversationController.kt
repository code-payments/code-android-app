package com.getcode.network

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.getcode.api.BuildConfig
import com.getcode.db.AppDatabase
import com.getcode.db.Database
import com.getcode.manager.SessionManager
import com.getcode.mapper.ConversationMapper
import com.getcode.mapper.ConversationMessageWithContentMapper
import com.getcode.model.Conversation
import com.getcode.model.ConversationIntentIdReference
import com.getcode.model.ConversationMessageWithContent
import com.getcode.model.ConversationWithLastPointers
import com.getcode.model.ID
import com.getcode.model.MessageStatus
import com.getcode.model.SocialUser
import com.getcode.model.chat.ChatType
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.OutgoingMessageContent
import com.getcode.model.chat.Platform
import com.getcode.model.chat.isConversation
import com.getcode.model.chat.selfId
import com.getcode.model.description
import com.getcode.network.client.ChatMessageStreamReference
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.base58
import com.getcode.network.service.ChatServiceV2
import com.getcode.utils.ErrorUtils
import com.getcode.utils.bytes
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

interface ConversationController {
    fun observeConversation(id: ID): Flow<ConversationWithLastPointers?>
    suspend fun createConversation(identifier: ID, with: SocialUser): Conversation
    suspend fun getConversation(identifier: ID): ConversationWithLastPointers?
    suspend fun getOrCreateConversation(identifier: ID, with: SocialUser): ConversationWithLastPointers
    fun openChatStream(scope: CoroutineScope, conversation: Conversation)
    fun closeChatStream()
    suspend fun hasInteracted(messageId: ID): Boolean
    suspend fun revealIdentity(conversationId: ID, platform: Platform, username: String): Result<Unit>
    suspend fun resetUnreadCount(conversationId: ID)
    suspend fun advanceReadPointer(conversationId: ID, messageId: ID, status: MessageStatus)
    suspend fun sendMessage(conversationId: ID, message: String): Result<ID>
    fun conversationPagingData(conversationId: ID): Flow<PagingData<ConversationMessageWithContent>>
    fun observeTyping(conversationId: ID): Flow<Boolean>
    suspend fun onUserStartedTypingIn(conversationId: ID)
    suspend fun onUserStoppedTypingIn(conversationId: ID)
}

class ConversationStreamController @Inject constructor(
    private val historyController: ChatHistoryController,
    private val exchange: Exchange,
    private val chatService: ChatServiceV2,
    private val conversationMapper: ConversationMapper,
    private val messageWithContentMapper: ConversationMessageWithContentMapper,
    private val tipController: TipController,
) : ConversationController {
    private val pagingConfig = PagingConfig(pageSize = 20)

    private val db: AppDatabase by lazy { Database.requireInstance() }

    private var stream: ChatMessageStreamReference? = null

    private val typingChats = MutableStateFlow<List<ID>>(emptyList())

    private fun conversationPagingSource(conversationId: ID) =
        db.conversationMessageDao().observeConversationMessages(conversationId.base58)

    override fun observeConversation(id: ID): Flow<ConversationWithLastPointers?> {
        return db.conversationDao().observeConversation(id)
    }

    override suspend fun createConversation(identifier: ID, with: SocialUser): Conversation {
        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: throw IllegalStateException()
        val self = tipController.connectedAccount.value ?: throw IllegalStateException()
        return chatService.startChat(owner, self, with, identifier, ChatType.TwoWay)
            .onSuccess { historyController.addChat(it) }
            .map { conversationMapper.map(it) }
            .onSuccess {
                // TODO: remove
                // stop gap until startChat rejects created chats for the same identifier
                db.conversationIntentMappingDao()
                    .insert(
                        ConversationIntentIdReference(
                            conversationIdBase58 = it.id.base58,
                            intentIdBase58 = identifier.base58
                        )
                    )
                db.conversationDao().upsertConversations(it)
            }
            .getOrThrow()
    }

    override suspend fun getConversation(identifier: ID): ConversationWithLastPointers? {
        val conversation = db.conversationDao().findConversation(identifier) ?: return null
        return conversation
    }

    override suspend fun getOrCreateConversation(identifier: ID, with: SocialUser): ConversationWithLastPointers {
        var conversationByChatId = getConversation(identifier)
        if (conversationByChatId != null) {
            return conversationByChatId
        }

        // lookup chat ID by tip intent ID
        val conversationId = db.conversationIntentMappingDao().conversationIdByReference(identifier)
        if (conversationId != null) {
            conversationByChatId = getConversation(identifier)
        }

        if (conversationByChatId != null) {
            return conversationByChatId
        }

        return ConversationWithLastPointers(createConversation(identifier, with), emptyList())
    }

    @Throws(IllegalStateException::class)
    override fun openChatStream(scope: CoroutineScope, conversation: Conversation) {
        runCatching { closeChatStream() }
        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: throw IllegalStateException()

        val chat = historyController.findChat { it.id == conversation.id }
            ?: throw IllegalArgumentException("Unable to resolve chat for this conversation")
        val memberId = chat.selfId ?: throw IllegalStateException("Not a member of this chat")

        stream = chatService.openChatStream(
            scope = scope,
            conversation = conversation,
            memberId = memberId,
            owner = owner,
            chatLookup = { chat }
        ) result@{ result ->
            if (result.isSuccess) {
                val updates = result.getOrNull() ?: return@result
                val (messages, pointers, isTyping) = updates

                typingChats.value = if (isTyping) {
                    typingChats.value + listOf(conversation.id).toSet()
                } else {
                    typingChats.value - listOf(conversation.id).toSet()
                }

                historyController.updateChatWithMessages(chat, messages)
                val messagesWithContent = messages.map {
                    messageWithContentMapper.map(chat.id to it)
                }

                val identityRevealed = messages
                    .flatMap { it.contents }
                    .filterIsInstance<MessageContent.IdentityRevealed>()
                    .firstOrNull()
                    .takeIf { chat.isConversation }

                if (identityRevealed != null && conversation.members.isNotEmpty()) {
                    val members = conversation.members.map {
                        if (identityRevealed.memberId == it.id.bytes) {
                            it.copy(identity = identityRevealed.identity)
                        } else {
                            it
                        }
                    }
                    scope.launch(Dispatchers.IO) {
                        db.conversationDao()
                            .upsertConversations(
                                conversation.copy(members = members)
                            )
                    }
                }

                println("chat messages: ${messages.count()}, pointers=${pointers.count()}, isTyping=$isTyping")

                scope.launch(Dispatchers.IO) {
                    db.conversationMessageDao().upsertMessagesWithContent(messagesWithContent)
                    pointers
                        // we only care about pointers of others
                        .filter { it.memberId != chat.selfId }
                        .onEach {
                        println("inserting $it")
                        db.conversationPointersDao().insert(
                            conversationId = conversation.id,
                            messageId = it.messageId,
                            status = it.messageStatus
                        )
                    }
                }
            } else {
                result.exceptionOrNull()?.let {
                    ErrorUtils.handleError(it)
                }
            }
        }
    }

    override fun closeChatStream() {
        stream?.destroy()
    }

    override suspend fun hasInteracted(messageId: ID): Boolean {
        // TODO: will require conversation model update
        return false
    }

    override suspend fun revealIdentity(conversationId: ID, platform: Platform, username: String): Result<Unit> {
        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: return Result.failure(Throwable("owner not found"))
        val chat = historyController.findChat { it.id == conversationId }
            ?: return Result.failure(Throwable("Chat not found"))

        val memberId = chat.selfId ?: return Result.failure(Throwable("Not member of chat"))

        return chatService.revealIdentity(owner, chat, memberId, platform, username)
            .map { }
            .onSuccess {
                db.conversationDao().revealIdentity(conversationId)
            }
    }

    override suspend fun resetUnreadCount(conversationId: ID) {
        val chat = historyController.findChat { it.id == conversationId } ?: return
        historyController.resetUnreadCount(chat.id)
    }

    override suspend fun advanceReadPointer(conversationId: ID, messageId: ID, status: MessageStatus) {
        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: return

        val chat = historyController.findChat { it.id == conversationId } ?: return

        val memberId = chat.selfId ?: return

        chatService.advancePointer(owner, conversationId, memberId, messageId, status)
            .onSuccess {
                trace("advanced pointer for chat on ${messageId.description} => $status")
            }.onFailure { it.printStackTrace() }
    }

    override suspend fun sendMessage(conversationId: ID, message: String): Result<ID> {
        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: return Result.failure(Throwable("Owner not found"))

        val chat = historyController.findChat { it.id == conversationId }
            ?: return Result.failure(Throwable("Chat not found"))

        val memberId = chat.selfId ?: return Result.failure(Throwable("Not a member of this chat"))

        return chatService.sendMessage(
            owner = owner,
            chat = chat,
            memberId = memberId,
            content = OutgoingMessageContent.Text(message)
        ).map {
            val messageWithContent = messageWithContentMapper.map(conversationId to it)
            CoroutineScope(Dispatchers.IO).launch {
                db.conversationMessageDao().upsertMessagesWithContent(messageWithContent)
            }

            messageWithContent.message.id
        }
    }

    override fun conversationPagingData(conversationId: ID) =
        Pager(
            config = pagingConfig,
            initialKey = null,
        ) { conversationPagingSource(conversationId) }.flow

    override fun observeTyping(conversationId: ID): Flow<Boolean> =
        typingChats.map { it.contains(conversationId) }

    override suspend fun onUserStartedTypingIn(conversationId: ID) {
        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: return

        val chat = historyController.findChat { it.id == conversationId }
            ?: return

        val memberId = chat.selfId ?: return

        chatService.onStartedTyping(
            owner, chat, memberId
        ).onSuccess {
            println("on typing started reported")
        }.onFailure {
            if (BuildConfig.DEBUG) {
                it.printStackTrace()
            }
        }
    }

    override suspend fun onUserStoppedTypingIn(conversationId: ID) {
        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: return

        val chat = historyController.findChat { it.id == conversationId }
            ?: return

        val memberId = chat.selfId ?: return

        chatService.onStoppedTyping(
            owner, chat, memberId
        ).onSuccess {
            println("on typing stopped reported")
        }.onFailure {
            if (BuildConfig.DEBUG) {
            it.printStackTrace()
            }
        }
    }

}