package com.getcode.network

import android.annotation.SuppressLint
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.manager.SessionManager
import com.getcode.model.Chat
import com.getcode.model.ChatMessage
import com.getcode.model.HistoricalTransaction
import com.getcode.model.ID
import com.getcode.network.client.Client
import com.getcode.network.client.fetchChats
import com.getcode.network.client.fetchMessagesFor
import com.getcode.network.repository.TransactionRepository
import com.getcode.network.repository.encodeBase64
import com.getcode.network.source.ChatMessagePagingSource
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import okhttp3.internal.toImmutableList
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryController @Inject constructor(
    private val client: Client,
    private val transactionRepository: TransactionRepository,
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    val hasFetchedTransactions: Boolean
        get() = transactions.isNotEmpty()

    private val transactions: List<HistoricalTransaction>
        get() = transactionRepository.transactionCache
            .value.orEmpty()
            .sortedByDescending { it.date }
            .toImmutableList()


    private val _chats = MutableStateFlow<List<Chat>?>(null)
    val chats: StateFlow<List<Chat>?>
        get() = _chats.asStateFlow()

    fun chatMessagePager(chatId: ID) = Pager(
        PagingConfig(pageSize = 20)
    ) { ChatMessagePagingSource(client, owner()!!, chatId) }

    val unreadCount = chats
        .filterNotNull()
        .map { it.filter { c -> !c.isMuted } }
        .map { it.sumOf { c -> c.unreadCount } }

    private fun owner(): KeyPair? = SessionManager.getKeyPair()

    suspend fun fetchDelta() {
        val latestTransaction = transactions.firstOrNull()

        if (latestTransaction == null) {
            fetchAllTransactions()
            return
        }

        val owner = owner() ?: return

        transactionRepository.fetchPaymentHistoryDelta(
            owner = owner,
            afterId = latestTransaction.id.toByteArray()
        )
    }

    @SuppressLint("CheckResult")
    suspend fun fetchAllTransactions() {
        val owner = owner() ?: return
        transactionRepository.fetchPaymentHistoryDelta(owner)
            .toFlowable()
            .asFlow()
            .catch { ErrorUtils.handleError(it) }
            .collect()
    }

    suspend fun fetchChats() {
        val containers = fetchChatsWithoutMessages()
        Timber.d("chats fetched = ${containers.count()}")
        _chats.value = containers

        val updatedWithMessages= mutableListOf<Chat>()
        containers.onEach { chat ->
            val result = fetchLatestMessageForChat(chat.id)
            result.onSuccess { message ->
                if (message != null) {
                    updatedWithMessages.add(chat.copy(messages = listOf(message)))
                }
            }.onFailure {
                updatedWithMessages.add(chat)
            }
        }

        _chats.value = updatedWithMessages.sortedByDescending { it.lastMessageMillis }
    }

    private suspend fun fetchMessagesForChat(id: List<Byte>): Result<List<ChatMessage>> {
        val encodedId = id.toByteArray().encodeBase64()
        Timber.d("fetching messages for $encodedId")
        val owner = owner() ?: return Result.success(emptyList())
        return client.fetchMessagesFor(owner, id)
            .onFailure {
                Timber.e(t = it, "Failed to fetch messages for $encodedId.")
            }
    }

    private suspend fun fetchLatestMessageForChat(id: List<Byte>): Result<ChatMessage?> {
        val encodedId = id.toByteArray().encodeBase64()
        Timber.d("fetching messages for $encodedId")
        val owner = owner() ?: return Result.success(null)
        return client.fetchMessagesFor(owner, id, limit = 1)
            .onFailure {
                Timber.e(t = it, "Failed to fetch messages for $encodedId.")
            }.map { it.getOrNull(0) }
    }

    private suspend fun fetchChatsWithoutMessages(): List<Chat> {
        val owner = owner() ?: return emptyList()
        val result = client.fetchChats(owner)
        return if (result.isSuccess) {
            result.getOrNull().orEmpty()
        } else {
            val exception = result.exceptionOrNull()
            exception?.let { ErrorUtils.handleError(it) }
            emptyList()
        }
    }
}

fun List<Chat>.mapInPlace(mutator: (Chat) -> (Chat)): List<Chat> {
    val updated = toMutableList().apply {
        this.forEachIndexed { i, value ->
            val changedValue = mutator(value)

            this[i] = changedValue
        }
    }
    return updated.toImmutableList()
}