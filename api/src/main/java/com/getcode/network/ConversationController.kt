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
import com.getcode.model.ChatMessage
import com.getcode.model.Conversation
import com.getcode.model.ConversationMessage
import com.getcode.model.ConversationMessageContent
import com.getcode.model.ID
import com.getcode.model.MessageStatus
import com.getcode.network.client.ChatMessageStreamReference
import com.getcode.network.client.Client
import com.getcode.network.client.openChatStream
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.base58
import com.getcode.network.repository.decodeBase64
import com.getcode.network.source.ConversationMockProvider
import com.getcode.vendor.Base58
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.kin.sdk.base.tools.toByteArray
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlin.jvm.Throws

interface ConversationController {
    fun observeConversationForMessage(messageId: ID): Flow<Conversation?>
    fun openChatStream(scope: CoroutineScope, messageId: ID)
    fun closeChatStream()
    suspend fun hasThanked(messageId: ID): Boolean
    suspend fun thankTipper(messageId: ID)
    suspend fun revealIdentity(messageId: ID)
    fun sendMessage(conversationId: ID, message: String)
    fun conversationPagingData(chatId: ID): Flow<PagingData<ConversationMessage>>
}

class ConversationStreamController @Inject constructor(
    private val historyController: HistoryController,
    private val exchange: Exchange,
    private val client: Client
): ConversationController {
    private val pagingConfig = PagingConfig(pageSize = 20)

    private val db: AppDatabase by lazy { Database.requireInstance() }

    private var stream: ChatMessageStreamReference? = null

    private fun conversationPagingSource(conversationId: ID) =
        db.conversationMessageDao().observeConversationMessages(conversationId.base58)

    override fun observeConversationForMessage(messageId: ID): Flow<Conversation?> {
        return db.conversationDao().observeConversationForMessage(messageId)
    }

    @Throws(IllegalStateException::class)
    override fun openChatStream(scope: CoroutineScope, messageId: ID) {
        val chatId = "468f158662880905e966f7c27f36b39e368837887aa5cf889cb55d91537d1a76".decodeBase64().toList()
        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: throw IllegalStateException()
        stream = client.openChatStream(scope, chatId, owner) { result ->
            if (result.isSuccess) {
                println("chat messages: ${result.getOrNull()}")
            }
        }
    }

    override fun closeChatStream() {
        stream?.destroy()
    }

    override suspend fun hasThanked(messageId: ID): Boolean {
        val conversation = db.conversationDao().findConversationForMessage(messageId) ?: return false
        return db.conversationDao().hasThanked(conversation.messageId)
    }

    override suspend fun thankTipper(messageId: ID) {
    }

    override suspend fun revealIdentity(messageId: ID) {
    }

    override fun sendMessage(conversationId: ID, message: String) {

    }

    @OptIn(ExperimentalPagingApi::class)
    override fun conversationPagingData(chatId: ID) =
        Pager(
            config = pagingConfig,
            initialKey = null,
            remoteMediator = ConversationMessagePageKeyedRemoteMediator(db)
        ) { conversationPagingSource(chatId) }.flow

}
class ConversationMockController @Inject constructor(
    private val historyController: HistoryController,
    private val exchange: Exchange,
) : ConversationController {

    private val pagingConfig = PagingConfig(pageSize = 20)

    private val db: AppDatabase by lazy { Database.requireInstance() }

    private fun conversationPagingSource(conversationId: ID) =
        db.conversationMessageDao().observeConversationMessages(conversationId.base58)

    override fun observeConversationForMessage(messageId: ID): Flow<Conversation?> {
        return db.conversationDao().observeConversationForMessage(messageId)
    }

    override fun openChatStream(scope: CoroutineScope, messageId: ID) {
        scope.launch {
            Timber.d("creating conversation: ${messageId.base58}")
            val message =
                historyController.chats.value?.find {
                    Timber.d("messages=${it.messages.joinToString { it.id.base58 }}")
                    it.messages.firstOrNull { it.id == messageId } != null
                }?.messages?.find { it.id == messageId }

            if (message == null) {
                Timber.e("No message for ${messageId.base58} found")
                return@launch
            }

            val conversation = ConversationMockProvider.createConversation(exchange, message)
            if (conversation == null) {
                Timber.e("Failed to create conversation!")
                return@launch
            }

            db.conversationDao().upsertConversations(conversation)

            val tipMessage = ConversationMockProvider.createMessage(
                conversation.messageId,
                ConversationMessageContent.TipMessage
            )

            Timber.d("upserting tip message")
            db.conversationMessageDao().upsertMessages(tipMessage)
        }
    }

    override fun closeChatStream() {

    }

    @OptIn(ExperimentalPagingApi::class)
    override fun conversationPagingData(chatId: ID) =
        Pager(
            config = pagingConfig,
            initialKey = null,
            remoteMediator = ConversationMessagePageKeyedRemoteMediator(db)
        ) { conversationPagingSource(chatId) }.flow

    override suspend fun hasThanked(messageId: ID): Boolean {
        val conversation = db.conversationDao().findConversationForMessage(messageId) ?: return false
        return db.conversationDao().hasThanked(conversation.messageId)
    }

    override suspend fun thankTipper(messageId: ID) {
        val message = ConversationMockProvider.thankTipper(messageId) ?: return
        db.conversationMessageDao().upsertMessages(message)
    }

    override suspend fun revealIdentity(messageId: ID) {
        val message = ConversationMockProvider.revealIdentity(messageId) ?: return
        db.conversationMessageDao().upsertMessages(message)
    }

    override fun sendMessage(conversationId: ID, message: String) {
        GlobalScope.launch {
            val messageId = generateId()

            val tipAddress = SessionManager.getOrganizer()?.primaryVault
                ?.let { Base58.encode(it.byteArray) } ?: return@launch

            val content = ConversationMessageContent.Text(
                message = message,
                status = MessageStatus.Sent,
                from = tipAddress
            )

            val m = ConversationMessage(
                idBase58 = messageId.base58,
                cursorBase58 = messageId.base58,
                conversationIdBase58 = conversationId.base58,
                dateMillis = System.currentTimeMillis(),
                content = content,
            )

            db.conversationMessageDao().upsertMessages(m)

            // delay and mimic delivery
            delay(1_000)
            db.conversationMessageDao().upsertMessages(
                m.copy(content = content.copy(status = MessageStatus.Delivered))
            )

            // delay and deliver read
            delay(2_500)
            db.conversationMessageDao().upsertMessages(
                m.copy(content = content.copy(status = MessageStatus.Read))
            )

            // delay and mimic incoming response
            delay(2_000)

            val responseId = generateId()
            val response = ConversationMessage(
                idBase58 = responseId.base58,
                cursorBase58 = responseId.base58,
                conversationIdBase58 = conversationId.base58,
                dateMillis = System.currentTimeMillis(),
                content = ConversationMessageContent.Text(
                    "You're welcome!!",
                    status = MessageStatus.Incoming,
                    from = Base58.encode("reply".encodeToByteArray())
                ),
            )

            db.conversationMessageDao().upsertMessages(response)
        }
    }

    private fun generateId() = UUID.randomUUID().toByteArray().toList()
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