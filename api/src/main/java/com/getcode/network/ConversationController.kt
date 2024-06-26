package com.getcode.network

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.getcode.db.AppDatabase
import com.getcode.db.Database
import com.getcode.manager.SessionManager
import com.getcode.mapper.ConversationMapper
import com.getcode.model.Conversation
import com.getcode.model.ConversationIntentIdReference
import com.getcode.model.ConversationMessage
import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.ChatType
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.Reference
import com.getcode.network.client.ChatMessageStreamReference
import com.getcode.network.client.Client
import com.getcode.network.client.openChatStream
import com.getcode.network.client.startChat
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.base58
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
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
    fun sendMessage(conversationId: ID, message: String)
    fun conversationPagingData(conversationId: ID): Flow<PagingData<ConversationMessage>>
}

class ConversationStreamController @Inject constructor(
    private val historyController: HistoryController,
    private val exchange: Exchange,
    private val client: Client,
    private val conversationMapper: ConversationMapper,
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

        return client.startChat(owner, identifier, type)
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

        println(chat.type)
        println("members in chat ${chat.members.joinToString()}")
        val memberId = chat.members.filter { it.isSelf }
            .map { it.id }.firstOrNull() ?: throw IllegalStateException("Not a member of this chat")

        stream = client.openChatStream(
            scope = scope,
            conversation = conversation,
            memberId = memberId,
            owner = owner,
            chatLookup = { chat }
        ) { result ->
            if (result.isSuccess) {
                println("chat messages: ${result.getOrNull()}")
            } else {
                println("Error: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    override fun closeChatStream() {
        stream?.destroy()
    }

    override suspend fun hasInteracted(messageId: ID): Boolean {
        val lookup = { msg: ChatMessage ->
            msg.id == messageId ||
                    msg.contents.filterIsInstance<MessageContent.Exchange>()
                        .map { it.reference }
                        .filterIsInstance<Reference.IntentId>()
                        .any { it.id == messageId }
        }

        val chat = historyController.chats.value?.firstOrNull {
            it.messages.find { msg -> lookup(msg) } != null
        } ?: return false

        val conversation = db.conversationDao().findConversation(chat.id) ?: return false
        return db.conversationDao().hasInteracted(conversation.id)
    }

    override suspend fun revealIdentity(messageId: ID) {
    }

    override fun sendMessage(conversationId: ID, message: String) {

    }

    override fun conversationPagingData(conversationId: ID) =
        Pager(
            config = pagingConfig,
            initialKey = null,
        ) { conversationPagingSource(conversationId) }.flow

}