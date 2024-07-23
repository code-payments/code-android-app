package com.getcode.network

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
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
import com.getcode.model.chat.ChatType
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.OutgoingMessageContent
import com.getcode.model.chat.Platform
import com.getcode.model.chat.isConversation
import com.getcode.model.chat.selfId
import com.getcode.network.client.ChatMessageStreamReference
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.base58
import com.getcode.network.service.ChatServiceV2
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

interface ConversationController {
    fun observeConversation(id: ID): Flow<ConversationWithLastPointers?>
    suspend fun createConversation(identifier: ID, type: ChatType): Conversation
    suspend fun getConversation(identifier: ID): ConversationWithLastPointers?
    suspend fun getOrCreateConversation(identifier: ID, type: ChatType): ConversationWithLastPointers
    fun openChatStream(scope: CoroutineScope, conversation: Conversation)
    fun closeChatStream()
    suspend fun hasInteracted(messageId: ID): Boolean
    suspend fun revealIdentity(conversationId: ID, platform: Platform, username: String): Result<Unit>
    suspend fun advanceReadPointer(conversationId: ID, messageId: ID, status: MessageStatus)
    suspend fun sendMessage(conversationId: ID, message: String): Result<ID>
    fun conversationPagingData(conversationId: ID): Flow<PagingData<ConversationMessageWithContent>>
}

class ConversationStreamController @Inject constructor(
    private val historyController: HistoryController,
    private val exchange: Exchange,
    private val chatService: ChatServiceV2,
    private val conversationMapper: ConversationMapper,
    private val messageWithContentMapper: ConversationMessageWithContentMapper,
) : ConversationController {
    private val pagingConfig = PagingConfig(pageSize = 20)

    private val db: AppDatabase by lazy { Database.requireInstance() }

    private var stream: ChatMessageStreamReference? = null

    private fun conversationPagingSource(conversationId: ID) =
        db.conversationMessageDao().observeConversationMessages(conversationId.base58)

    override fun observeConversation(id: ID): Flow<ConversationWithLastPointers?> {
        return db.conversationDao().observeConversation(id)
    }

    override suspend fun createConversation(identifier: ID, type: ChatType): Conversation {
        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: throw IllegalStateException()

        return chatService.startChat(owner, identifier, type)
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
                // update chats
                historyController.fetchChats()
            }
            .getOrThrow()
    }

    override suspend fun getConversation(identifier: ID): ConversationWithLastPointers? {
        val conversation = db.conversationDao().findConversation(identifier) ?: return null
        return conversation
    }

    override suspend fun getOrCreateConversation(identifier: ID, type: ChatType): ConversationWithLastPointers {
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

        return ConversationWithLastPointers(createConversation(identifier, type), emptyList())
    }

    @Throws(IllegalStateException::class)
    override fun openChatStream(scope: CoroutineScope, conversation: Conversation) {
        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: throw IllegalStateException()

        val chat = historyController.chats.value?.firstOrNull {
            it.id == conversation.id
        } ?: throw IllegalArgumentException("Unable to resolve chat for this conversation")

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
                val (messages, pointers) = updates

                historyController.updateChatWithMessages(chat, messages)
                val messagesWithContent = messages.map {
                    messageWithContentMapper.map(chat.id to it)
                }

                val identityRevealed = messages
                    .flatMap { it.contents }
                    .filterIsInstance<MessageContent.IdentityRevealed>()
                    .firstOrNull()
                    .takeIf { chat.isConversation }

                if (identityRevealed != null && conversation.user == null) {
                    scope.launch(Dispatchers.IO) {
                        db.conversationDao()
                            .upsertConversations(
                                conversation.copy(user = identityRevealed.identity.username)
                            )
                    }
                }

                println("chat messages: ${messages.count()}, pointers=${pointers.count()}")

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
        val chat = historyController.chats.value?.firstOrNull {
            it.id == conversationId
        } ?: return Result.failure(Throwable("Chat not found"))

        val memberId = chat.selfId ?: return Result.failure(Throwable("Not member of chat"))

        return chatService.revealIdentity(owner, chat, memberId, platform, username)
            .map { }
            .onSuccess {
                db.conversationDao().revealIdentity(conversationId)
            }
    }

    override suspend fun advanceReadPointer(conversationId: ID, messageId: ID, status: MessageStatus) {
        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: return

        val chat = historyController.chats.value?.firstOrNull {
            it.id == conversationId
        } ?: return

        val memberId = chat.selfId ?: return

        chatService.advancePointer(owner, conversationId, memberId, messageId, status)
            .onSuccess {

            }.onFailure { it.printStackTrace() }
    }

    override suspend fun sendMessage(conversationId: ID, message: String): Result<ID> {
        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: return Result.failure(Throwable("Owner not found"))

        val chat = historyController.chats.value?.firstOrNull {
            it.id == conversationId
        } ?: return Result.failure(Throwable("Unable to find chat"))

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

}