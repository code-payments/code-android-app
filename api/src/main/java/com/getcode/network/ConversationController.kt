package com.getcode.network

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.getcode.db.AppDatabase
import com.getcode.db.Database
import com.getcode.manager.SessionManager
import com.getcode.mapper.ConversationMapper
import com.getcode.model.chat.ChatType
import com.getcode.model.Conversation
import com.getcode.model.ConversationMessage
import com.getcode.model.ID
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.Reference
import com.getcode.network.client.ChatMessageStreamReference
import com.getcode.network.client.Client
import com.getcode.network.client.openChatStream
import com.getcode.network.client.startChat
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.base58
import com.getcode.utils.bytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlin.jvm.Throws

interface ConversationController {
    fun observeConversationForMessage(messageId: ID): Flow<Conversation?>
    suspend fun createConversation(identifier: ID, type: ChatType): Conversation
    suspend fun getConversation(identifier: ID): Conversation?
    suspend fun getOrCreateConversation(identifier: ID, type: ChatType): Conversation?
    fun openChatStream(scope: CoroutineScope, identifier: ID)
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
): ConversationController {
    private val pagingConfig = PagingConfig(pageSize = 20)

    private val db: AppDatabase by lazy { Database.requireInstance() }

    private var stream: ChatMessageStreamReference? = null

    private val memberId = UUID.randomUUID()

    private fun conversationPagingSource(conversationId: ID) =
        db.conversationMessageDao().observeConversationMessages(conversationId.base58)

    override fun observeConversationForMessage(messageId: ID): Flow<Conversation?> {
        return db.conversationDao().observeConversationForMessage(messageId)
    }

    override suspend fun createConversation(identifier: ID, type: ChatType): Conversation {
        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: throw IllegalStateException()
        val lookup = { msg: ChatMessage ->
            msg.id == identifier ||
                msg.contents.filterIsInstance<MessageContent.Exchange>()
                    .map { it.reference }
                    .filterIsInstance<Reference.IntentId>()
                    .any { it.id == identifier  }
        }

        val message = historyController.chats.value?.firstOrNull {
            it.messages.find { msg -> lookup(msg) } != null
        }?.messages?.firstOrNull { msg -> lookup(msg) } ?: throw IllegalArgumentException()

        return client.startChat(owner, identifier, type)
            .map { conversationMapper.map(it to message) }
            .onSuccess {
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
        return getConversation(identifier) ?: createConversation(identifier, type)
    }

    @Throws(IllegalStateException::class)
    override fun openChatStream(scope: CoroutineScope, identifier: ID) {
        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: throw IllegalStateException()
        val chat = historyController.chats.value?.firstOrNull {
            it.messages.find { msg -> msg.id == identifier } != null
        } ?: throw IllegalArgumentException()

        stream = client.openChatStream(scope, chat, identifier, memberId.bytes, owner) { result ->
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
        val conversation = db.conversationDao().findConversationForMessage(messageId) ?: return false
        return db.conversationDao().hasInteracted(conversation.messageId)
    }

    override suspend fun revealIdentity(messageId: ID) {
    }

    override fun sendMessage(conversationId: ID, message: String) {

    }

    @OptIn(ExperimentalPagingApi::class)
    override fun conversationPagingData(conversationId: ID) =
        Pager(
            config = pagingConfig,
            initialKey = null,
            remoteMediator = ConversationMessagePageKeyedRemoteMediator(db)
        ) { conversationPagingSource(conversationId) }.flow

}

/**
 * Make sure to have the same sort from DB as it is from the backend side, otherwise items get mixed up and prevKey
 * and nextKey are no longer valid (the scroll might get stuck or the load might loop one of the pages)
 */
@OptIn(ExperimentalPagingApi::class)
class ConversationMessagePageKeyedRemoteMediator(
    private val db: AppDatabase,
) : RemoteMediator<Int, ConversationMessage>() {
    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ConversationMessage>
    ): MediatorResult {
        return try {
            // The network load method takes an optional after=<user.id>
            // parameter. For every page after the first, pass the last user
            // ID to let it continue from where it left off. For REFRESH,
            // pass null to load the first page.
            val cursor = when (loadType) {
                LoadType.REFRESH -> null
                // In this example, you never need to prepend, since REFRESH
                // will always load the first page in the list. Immediately
                // return, reporting end of pagination.
                LoadType.PREPEND ->
                    return MediatorResult.Success(endOfPaginationReached = true)

                LoadType.APPEND -> {
                    val lastItem = state.lastItemOrNull()
                        ?: return MediatorResult.Success(endOfPaginationReached = true)

                    // You must explicitly check if the last item is null when
                    // appending, since passing null to networkService is only
                    // valid for initial load. If lastItem is null it means no
                    // items were loaded after the initial REFRESH and there are
                    // no more items to load.

                    lastItem.cursor
                }
            }
            MediatorResult.Success(endOfPaginationReached = true)
        } catch (e: IOException) {
            MediatorResult.Error(e)
        }
    }
}