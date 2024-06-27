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
import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.ChatType
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.OutgoingMessageContent
import com.getcode.model.chat.Reference
import com.getcode.model.description
import com.getcode.network.client.ChatMessageStreamReference
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.base58
import com.getcode.network.service.ChatServiceV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

interface ConversationController {
    fun observeConversation(id: ID): Flow<Conversation?>
    suspend fun createConversation(identifier: ID, type: ChatType): Conversation
    suspend fun getConversation(identifier: ID): Conversation?
    suspend fun getOrCreateConversation(identifier: ID, type: ChatType): Conversation?
    fun openChatStream(scope: CoroutineScope, conversation: Conversation)
    fun closeChatStream()
    suspend fun hasInteracted(messageId: ID): Boolean
    suspend fun revealIdentity(messageId: ID)
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

    override fun observeConversation(id: ID): Flow<Conversation?> {
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

    override suspend fun getConversation(identifier: ID): Conversation? {
        return db.conversationDao().findConversation(identifier)
    }

    override suspend fun getOrCreateConversation(identifier: ID, type: ChatType): Conversation? {
        var conversationByChatId = getConversation(identifier)
        if (conversationByChatId != null) {
            return conversationByChatId
        }

        // lookup chat ID by tip intent ID
        val conversationId = db.conversationIntentMappingDao().conversationIdByReference(identifier)
        if (conversationId != null) {
            conversationByChatId = getConversation(identifier)
        }

        return conversationByChatId ?: createConversation(identifier, type)
    }

    @Throws(IllegalStateException::class)
    override fun openChatStream(scope: CoroutineScope, conversation: Conversation) {
        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: throw IllegalStateException()

        val chat = historyController.chats.value?.firstOrNull {
            it.id == conversation.id
        } ?: throw IllegalArgumentException("Unable to resolve chat for this conversation")

        val memberId = chat.members.filter { it.isSelf }
            .map { it.id }.firstOrNull() ?: throw IllegalStateException("Not a member of this chat")

        stream = chatService.openChatStream(
            scope = scope,
            conversation = conversation,
            memberId = memberId,
            owner = owner,
            chatLookup = { chat }
        ) { result ->
            if (result.isSuccess) {
                println("chat messages: ${result.getOrNull()}")
                val messages = result.getOrNull().orEmpty().map {
                    messageWithContentMapper.map(chat.id to it)
                }
                scope.launch(Dispatchers.IO) {
                    db.conversationMessageDao().upsertMessagesWithContent(messages)
                }
            } else {
                println("Error: ${result.exceptionOrNull()?.message}")
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

    override suspend fun revealIdentity(messageId: ID) {
    }

    override suspend fun sendMessage(conversationId: ID, message: String): Result<ID> {
        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: return Result.failure(Throwable("Owner not found"))

        val chat = historyController.chats.value?.firstOrNull {
            it.id == conversationId
        } ?: return Result.failure(Throwable("Unable to find chat"))

        val memberId = chat.members.filter { it.isSelf }
            .map { it.id }.firstOrNull() ?: return Result.failure(Throwable("Not a member of this chat"))

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