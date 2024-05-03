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
import com.getcode.model.Conversation
import com.getcode.model.ConversationMessage
import com.getcode.model.ConversationMessageContent
import com.getcode.model.ID
import com.getcode.model.MessageStatus
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.base58
import com.getcode.network.source.ConversationMockProvider
import com.getcode.vendor.Base58
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.kin.sdk.base.tools.toByteArray
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

interface ConversationController {
    suspend fun getConversationForMessage(messageId: ID): Conversation?
    suspend fun getConversation(conversationId: ID): Conversation?
    suspend fun createConversation(messageId: ID)
    suspend fun hasThanked(messageId: ID): Boolean
    suspend fun thankTipper(messageId: ID)
    fun sendMessage(conversationId: ID, message: String)
    fun conversationPagingData(conversationId: ID): Flow<PagingData<ConversationMessage>>
}

class ConversationMockController @Inject constructor(
    private val historyController: HistoryController,
    private val exchange: Exchange,
) : ConversationController, CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val pagingConfig = PagingConfig(pageSize = 20)

    private val db: AppDatabase by lazy { Database.requireInstance() }

    private fun conversationPagingSource(conversationId: ID) =
        db.conversationMessageDao().observeConversationMessages(conversationId.base58)

    override suspend fun getConversationForMessage(messageId: ID): Conversation? {
        return db.conversationDao().findConversationForMessage(messageId)
    }

    override suspend fun getConversation(conversationId: ID): Conversation? {
        return db.conversationDao().findConversation(conversationId)
    }

    @OptIn(ExperimentalPagingApi::class)
    override fun conversationPagingData(conversationId: ID) =
        Pager(
            config = pagingConfig,
            initialKey = null,
            remoteMediator = ConversationMessagePageKeyedRemoteMediator(db)
        ) { conversationPagingSource(conversationId) }.flow

    override suspend fun createConversation(messageId: ID) {
        val message =
            historyController.chats.value?.find { it.messages.firstOrNull { it.id == messageId } != null }
                ?.messages?.find { it.id == messageId } ?: return

        val conversation = ConversationMockProvider.createConversation(exchange, message) ?: return

        db.conversationDao().upsertConversations(conversation)

        val tipMessage = ConversationMockProvider.createMessage(
            conversation.id,
            ConversationMessageContent.TipMessage
        )

        Timber.d("upserting tip message")
        db.conversationMessageDao().upsertMessages(tipMessage)
    }

    override suspend fun hasThanked(messageId: ID): Boolean {
        val conversation = db.conversationDao().findConversationForMessage(messageId) ?: return false
        return db.conversationDao().hasThanked(conversation.id)
    }

    override suspend fun thankTipper(messageId: ID) {
        val conversation = db.conversationDao().findConversationForMessage(messageId)
        if (conversation == null) {
            Timber.d("conversation doesn't exist.. creating")
            val message =
                historyController.chats.value?.find { it.messages.firstOrNull { it.id == messageId } != null }
                    ?.messages?.find { it.id == messageId } ?: return

            createConversation(message.id)
        }

        val message = ConversationMockProvider.thankTipper(messageId) ?: return
        db.conversationMessageDao().upsertMessages(message)
    }

    override fun sendMessage(conversationId: ID, message: String) {
        launch {
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