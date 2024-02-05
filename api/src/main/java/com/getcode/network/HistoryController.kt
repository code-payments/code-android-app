package com.getcode.network

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.manager.SessionManager
import com.getcode.model.Chat
import com.getcode.model.ChatMessage
import com.getcode.model.Cursor
import com.getcode.model.ID
import com.getcode.network.client.Client
import com.getcode.network.client.fetchChats
import com.getcode.network.client.fetchMessagesFor
import com.getcode.network.client.setMuted
import com.getcode.network.repository.encodeBase64
import com.getcode.network.source.ChatMessagePagingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import okhttp3.internal.toImmutableList
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryController @Inject constructor(
    private val client: Client,
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    val hasFetchedChats: Boolean
        get() = _chats.value.orEmpty().isNotEmpty()

    private val _chats = MutableStateFlow<List<Chat>?>(null)
    val chats: StateFlow<List<Chat>?>
        get() = _chats.asStateFlow()


    private val pagerMap = mutableMapOf<ID, PagingSource<Cursor, ChatMessage>>()
    private val chatFlows = mutableMapOf<ID, Flow<PagingData<ChatMessage>>>()

    private val pagingConfig = PagingConfig(pageSize = 20)

    private fun chatMessagePager(chatId: ID) = Pager(pagingConfig) {
        pagerMap[chatId] ?: ChatMessagePagingSource(client, owner()!!, chatId).also {
            pagerMap[chatId] = it
        }
    }

    fun chatFlow(chatId: ID) =
        chatFlows[chatId] ?: chatMessagePager(chatId).flow.cachedIn(GlobalScope).also {
            chatFlows[chatId] = it
        }

    val unreadCount = chats
        .filterNotNull()
        .map { it.filter { c -> !c.isMuted } }
        .map { it.sumOf { c -> c.unreadCount } }

    private fun owner(): KeyPair? = SessionManager.getKeyPair()

    suspend fun fetchChats() {
        val containers = fetchChatsWithoutMessages()
        Timber.d("chats fetched = ${containers.count()}")
        _chats.value = containers

        val updatedWithMessages = mutableListOf<Chat>()
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

        pagerMap.entries.onEach { (id, pagingSource) ->
            pagingSource.invalidate()
        }
    }

    suspend fun setMuted(chatId: ID, muted: Boolean): Result<Boolean> {
        val owner = owner() ?: return Result.failure(Throwable("No owner detected"))

        _chats.update {
            it?.toMutableList()?.apply chats@{
                indexOfFirst { chat -> chat.id == chatId }
                    .takeIf { index -> index >= 0 }
                    ?.let { index ->
                        val chat = this[index]
                        Timber.d("changing mute state for chat locally")
                        this[index] = chat.copy(isMuted = muted)
                    }
            }?.toList()
        }

        return client.setMuted(owner, chatId, muted)
    }

    suspend fun fetchMessagesForChat(
        id: List<Byte>,
        cursor: Cursor? = null,
        limit: Int? = null
    ): Result<ChatMessage?> {
        val encodedId = id.toByteArray().encodeBase64()
        val owner = owner() ?: return Result.success(null)
        return client.fetchMessagesFor(owner, id, cursor, limit)
            .onFailure {
                Timber.e(t = it, "Failed to fetch messages for $encodedId.")
            }.map { it.getOrNull(0) }
    }

    private suspend fun fetchLatestMessageForChat(id: List<Byte>): Result<ChatMessage?> {
        val encodedId = id.toByteArray().encodeBase64()
        Timber.d("fetching last message for $encodedId")
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
            result.exceptionOrNull()?.printStackTrace()
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