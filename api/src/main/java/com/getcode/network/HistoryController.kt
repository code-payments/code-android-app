package com.getcode.network

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.manager.SessionManager
import com.getcode.model.chat.Chat
import com.getcode.model.chat.ChatMessage
import com.getcode.model.Cursor
import com.getcode.model.ID
import com.getcode.network.client.Client
import com.getcode.network.client.advancePointer
import com.getcode.network.client.fetchChats
import com.getcode.network.client.fetchMessagesFor
import com.getcode.network.client.setMuted
import com.getcode.network.client.setSubscriptionState
import com.getcode.network.repository.encodeBase64
import com.getcode.network.source.ChatMessagePagingSource
import com.getcode.utils.TraceType
import com.getcode.utils.trace
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
    
    fun reset() {
        pagerMap.clear()
        chatFlows.clear()
    }

    private fun chatMessagePager(chatId: ID) = Pager(pagingConfig) {
        val chat = _chats.value?.find { it.id == chatId }
        pagerMap[chatId] ?: ChatMessagePagingSource(
            client = client,
            owner = owner()!!,
            chat = chat,
            onMessagesFetched = { messages ->
                chat ?: return@ChatMessagePagingSource
                val updatedMessages = (chat.messages + messages).distinctBy { it.id }
                val updatedChat = chat.copy(messages = updatedMessages)
                _chats.update { chats ->
                    val index = chats.orEmpty().indexOfFirst { it.id == chat.id }
                    if (index >= 0) {
                        chats.orEmpty().toMutableList().apply {
                            this[index] = updatedChat
                        }.toList()
                    } else {
                        chats
                    }
                }
            }
        ).also {
            pagerMap[chatId] = it
        }
    }

    fun chatFlow(chatId: ID) =
        chatFlows[chatId] ?: chatMessagePager(chatId).flow.cachedIn(GlobalScope).also {
            chatFlows[chatId] = it
        }

    val unreadCount = chats
        .filterNotNull()
        // Ignore muted chats and unsubscribed chats
        .map { it.filter { c -> !c.isMuted && c.isSubscribed } }
        .map { it.sumOf { c -> c.unreadCount } }

    private fun owner(): KeyPair? = SessionManager.getKeyPair()

    suspend fun fetchChats() {
        pagerMap.clear()
        chatFlows.clear()

        val containers = fetchChatsWithoutMessages()
        trace(message = "Fetched ${containers.count()} chats", type = TraceType.Silent)
        _chats.value = containers

        val updatedWithMessages = mutableListOf<Chat>()
        containers.onEach { chat ->
            val result = fetchLatestMessageForChat(chat)
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

    suspend fun advanceReadPointer(chatId: ID) {
        val owner = owner() ?: return

        _chats.update {
            it?.toMutableList()?.apply chats@{
                indexOfFirst { chat -> chat.id == chatId }
                    .takeIf { index -> index >= 0 }
                    ?.let { index ->
                        val chat = this[index]
                        val newestMessage = chat.newestMessage
                        if (newestMessage != null) {
                            client.advancePointer(owner, chat, newestMessage.id)
                                .onSuccess {
                                    this[index] = chat.resetUnreadCount()
                                }
                        }
                    }
            }?.toList()
        }
    }

    suspend fun setMuted(chat: Chat, muted: Boolean): Result<Boolean> {
        val owner = owner() ?: return Result.failure(Throwable("No owner detected"))

        _chats.update {
            it?.toMutableList()?.apply chats@{
                indexOfFirst { item -> item.id == chat.id }
                    .takeIf { index -> index >= 0 }
                    ?.let { index ->
                        val c = this[index]
                        Timber.d("changing mute state for chat locally")
                        this[index] = c.setMuteState(muted)
                    }
            }?.toList()
        }

        return client.setMuted(owner, chat, muted)
    }

    suspend fun setSubscribed(chat: Chat, subscribed: Boolean): Result<Boolean> {
        val owner = owner() ?: return Result.failure(Throwable("No owner detected"))

        _chats.update {
            it?.toMutableList()?.apply chats@{
                indexOfFirst { item -> item.id == chat.id }
                    .takeIf { index -> index >= 0 }
                    ?.let { index ->
                        val c = this[index]
                        Timber.d("changing subscribed state for chat locally")
                        this[index] = c.setSubscriptionState(subscribed)
                    }
            }?.toList()
        }

        return client.setSubscriptionState(owner, chat, subscribed)
    }

    private suspend fun fetchLatestMessageForChat(chat: Chat): Result<ChatMessage?> {
        val encodedId = chat.id.toByteArray().encodeBase64()
        Timber.d("fetching last message for $encodedId")
        val owner = owner() ?: return Result.success(null)
        return client.fetchMessagesFor(owner, chat, limit = 1)
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