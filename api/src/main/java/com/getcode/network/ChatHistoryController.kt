package com.getcode.network

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import com.getcode.db.AppDatabase
import com.getcode.db.Database
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.manager.SessionManager
import com.getcode.mapper.ConversationMapper
import com.getcode.mapper.ConversationMessageMapper
import com.getcode.model.chat.Chat
import com.getcode.model.chat.ChatMessage
import com.getcode.model.Cursor
import com.getcode.model.ID
import com.getcode.model.MessageStatus
import com.getcode.model.chat.ChatMember
import com.getcode.model.chat.Identity
import com.getcode.model.chat.Platform
import com.getcode.model.chat.Title
import com.getcode.model.chat.isConversation
import com.getcode.model.chat.isNotification
import com.getcode.model.chat.selfId
import com.getcode.network.client.Client
import com.getcode.network.client.advancePointer
import com.getcode.network.client.fetchChats
import com.getcode.network.client.fetchMessagesFor
import com.getcode.network.client.setMuted
import com.getcode.network.client.setSubscriptionState
import com.getcode.network.repository.encodeBase64
import com.getcode.network.source.ChatMessagePagingSource
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.ResourceType
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatHistoryController @Inject constructor(
    private val client: Client,
    private val tipController: TipController,
    private val conversationMapper: ConversationMapper,
    private val conversationMessageMapper: ConversationMessageMapper,
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val chatEntries = MutableStateFlow<List<Chat>?>(null)
    val notifications: StateFlow<List<Chat>?>
        get() = chatEntries
            .map { it?.filter { entry -> entry.isNotification } }
            .stateIn(this, SharingStarted.Eagerly, emptyList())

    val chats: StateFlow<List<Chat>?>
        get() = chatEntries
            .map { it?.filter { entry -> entry.isConversation } }
            .stateIn(this, SharingStarted.Eagerly, emptyList())

    var loadingMessages: Boolean = false

    private val db: AppDatabase by lazy { Database.requireInstance() }

    private val pagerMap = mutableMapOf<ID, PagingSource<Cursor, ChatMessage>>()
    private val chatFlows = mutableMapOf<ID, Flow<PagingData<ChatMessage>>>()

    private val pagingConfig = PagingConfig(pageSize = 20)

    fun reset() {
        pagerMap.clear()
        chatFlows.clear()
    }

    private fun chatMessagePager(chatId: ID) = Pager(pagingConfig) {
        pagerMap[chatId] ?: ChatMessagePagingSource(
            client = client,
            owner = owner()!!,
            chat = chatEntries.value?.find { it.id == chatId },
            onMessagesFetched = { messages ->
                val chat = chatEntries.value?.find { it.id == chatId } ?: return@ChatMessagePagingSource
                updateChatWithMessages(chat, messages)
            }
        ).also {
            pagerMap[chatId] = it
        }
    }

    fun updateChatWithMessages(chat: Chat, messages: List<ChatMessage>) {
        val updatedMessages = (chat.messages + messages).distinctBy { it.id }
        val updatedChat = chat.copy(messages = updatedMessages)
        val chats = chatEntries.value?.map {
            if (it.id == updatedChat.id) {
                updatedChat
            } else {
                it
            }
        }?.sortedByDescending { it.lastMessageMillis }
        chatEntries.update { chats }
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

    suspend fun fetchChats(update: Boolean = false) {
        if (loadingMessages) return

        val updatedWithMessages = mutableListOf<Chat>()
        val containers = fetchChatsWithoutMessages()
        trace(message = "Fetched ${containers.count()} chats", type = TraceType.Silent)

        if (!update) {
            pagerMap.clear()
            chatFlows.clear()
            chatEntries.value = containers

            loadingMessages = true
        }

        containers.onEach { chat ->
            val members = fetchMemberImages(chat)
            val updatedChat = chat.copy(members = members)
            val result = fetchLatestMessageForChat(updatedChat)
            result.onSuccess { message ->
                if (message != null) {
                    updatedWithMessages.add(updatedChat.copy(messages = listOf(message)))
                }
            }.onFailure {
                updatedWithMessages.add(updatedChat)
            }
        }

        loadingMessages = false
        chatEntries.value = updatedWithMessages.sortedByDescending { it.lastMessageMillis }
    }

    suspend fun advanceReadPointer(chatId: ID) {
        val owner = owner() ?: return

        chatEntries.update {
            it?.toMutableList()?.apply chats@{
                indexOfFirst { chat -> chat.id == chatId }
                    .takeIf { index -> index >= 0 }
                    ?.let { index ->
                        val chat = this[index]
                        val newestMessage = chat.newestMessage
                        if (newestMessage != null) {
                            client.advancePointer(
                                owner = owner,
                                chat = chat,
                                to = newestMessage.id,
                                status = MessageStatus.Read
                            ).onSuccess {
                                this[index] = chat.resetUnreadCount()
                            }
                        }
                    }
            }?.toList()
        }
    }

    fun advanceReadPointerUpTo(chatId: ID, timestamp: Long) {
        chatEntries.update {
            it?.toMutableList()?.apply chats@{
                indexOfFirst { chat -> chat.id == chatId }
                    .takeIf { index -> index >= 0 }
                    ?.let { index ->
                        val chat = this[index]
                        val newestMessage = chat.newestMessage
                        if (newestMessage != null) {
                            this[index] = chat.resetUnreadCount()
                        }
                    }
            }?.toList()
        }
    }

    suspend fun setMuted(chat: Chat, muted: Boolean): Result<Boolean> {
        val owner = owner() ?: return Result.failure(Throwable("No owner detected"))

        chatEntries.update {
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

        chatEntries.update {
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
            .onSuccess { result ->
                if (chat.isConversation) {
                    val messages =
                        result.map { message -> conversationMessageMapper.map(chat.id to message) }
                    val memberId = chat.selfId ?: return@onSuccess
                    val latestRef = messages.maxBy { it.dateMillis }
                    client.advancePointer(
                        owner,
                        chat,
                        latestRef.id,
                        memberId,
                        MessageStatus.Delivered
                    )
                }
            }
            .onFailure {
                Timber.e(t = it, "Failed to fetch messages for $encodedId.")
            }.map { it.getOrNull(0) }
    }

    private suspend fun fetchChatsWithoutMessages(): List<Chat> {
        val owner = owner() ?: return emptyList()
        val result = client.fetchChats(owner)
            .map { chats ->
                chats.map { chat ->
                    // map revealed identity as title if known
                    if (chat.isConversation) {
                        val conversation = conversationMapper.map(chat)
                        conversation.name?.let { chat.copy(title = Title.Localized(it)) } ?: chat
                    } else {
                        chat
                    }
                }
            }
            .onSuccess { result ->
                result.filter { it.isConversation }
                    .let { chats ->
                        val chatIds = chats.map { it.id }
                        db.conversationDao().purgeConversationsNotIn(chatIds)
                        db.conversationMessageDao().purgeMessagesNotIn(chatIds)
                        db.conversationPointersDao().purgePointersNoLongerNeeded(chatIds)
                        db.conversationIntentMappingDao().purgeMappingNoLongerNeeded(chatIds)
                        chats
                    }
                    .onEach {
                        val conversation = conversationMapper.map(it)
                        db.conversationDao().upsertConversations(conversation)
                    }
            }
        return result.getOrNull().orEmpty()
    }

    private suspend fun fetchMemberImages(chat: Chat): List<ChatMember> {
        return chat.members
            .map { member ->
                if (member.isSelf) return@map member
                if (member.identity == null) return@map member
                if (member.identity.imageUrl != null) return@map member
                val metadata = runCatching {
                    tipController.fetch(member.identity.username)
                }.getOrNull() ?: return@map member

                member.copy(
                    identity = Identity(
                        platform = Platform.named(metadata.platform),
                        username = metadata.username,
                        imageUrl = metadata.imageUrl
                    )
                )
            }
    }
}

fun Title?.localized(resources: ResourceHelper): String {
    return when (val t = this) {
        is Title.Domain -> {
            t.value.capitalize(Locale.getDefault())
        }

        is Title.Localized -> {
            val resId = resources.getIdentifier(
                t.value,
                ResourceType.String,
            ).let { if (it == 0) null else it }

            resId?.let { resources.getString(it) } ?: t.value
        }

        else -> "Anonymous"
    }
}