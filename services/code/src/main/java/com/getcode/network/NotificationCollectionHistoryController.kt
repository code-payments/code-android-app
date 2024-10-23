package com.getcode.network

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.manager.SessionManager
import com.getcode.model.chat.ChatMessage
import com.getcode.model.Cursor
import com.getcode.model.ID
import com.getcode.model.chat.MessageStatus
import com.getcode.model.chat.NotificationCollectionEntity
import com.getcode.model.chat.Title
import com.getcode.network.client.Client
import com.getcode.network.client.advancePointer
import com.getcode.network.client.fetchChats
import com.getcode.network.client.fetchMessagesFor
import com.getcode.network.client.setMuted
import com.getcode.network.client.setSubscriptionState
import com.getcode.network.source.CollectionPagingSource
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.resources.ResourceType
import com.getcode.utils.TraceType
import com.getcode.utils.encodeBase64
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
class NotificationCollectionHistoryController @Inject constructor(
    private val client: Client,
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    private val collectionEntries = MutableStateFlow<List<NotificationCollectionEntity>?>(null)

    val notifications: StateFlow<List<NotificationCollectionEntity>?>
        get() = collectionEntries
            .stateIn(this, SharingStarted.Eagerly, emptyList())

    var loadingCollections: Boolean = false

    private val pagerMap = mutableMapOf<ID, PagingSource<Cursor, ChatMessage>>()
    private val collectionFlows = mutableMapOf<ID, Flow<PagingData<ChatMessage>>>()

    private val pagingConfig = PagingConfig(pageSize = 20)

    fun reset() {
        pagerMap.clear()
        collectionFlows.clear()
    }

    private fun collectionPager(collectionId: ID) = Pager(pagingConfig) {
        pagerMap[collectionId] ?: CollectionPagingSource(
            client = client,
            owner = owner()!!,
            collection = collectionEntries.value?.find { it.id == collectionId },
            onMessagesFetched = { messages ->
                val collection = collectionEntries.value?.find { it.id == collectionId } ?: return@CollectionPagingSource
                updateCollectionWithMessages(collection, messages)
            }
        ).also {
            pagerMap[collectionId] = it
        }
    }

    private fun updateCollectionWithMessages(collection: NotificationCollectionEntity, messages: List<ChatMessage>) {
        val updatedMessages = (collection.messages + messages).distinctBy { it.id }
        val updatedChat = collection.copy(messages = updatedMessages)
        val collections = collectionEntries.value?.map {
            if (it.id == updatedChat.id) {
                updatedChat
            } else {
                it
            }
        }?.sortedByDescending { it.lastMessageMillis }
        collectionEntries.update { collections }
    }

    fun collectionFlow(collectionId: ID) =
        collectionFlows[collectionId] ?: collectionPager(collectionId).flow.cachedIn(GlobalScope).also {
            collectionFlows[collectionId] = it
        }

    val unreadCount = notifications
        .filterNotNull()
        // Ignore muted collections and unsubscribed collections
        .map { it.filter { c -> !c.isMuted && c.isSubscribed } }
        .map { it.sumOf { c -> c.unreadCount } }

    private fun owner(): KeyPair? = SessionManager.getKeyPair()

    suspend fun fetch(update: Boolean = false) {
        if (loadingCollections) return

        val updatedWithMessages = mutableListOf<NotificationCollectionEntity>()
        val containers = fetchCollectionsWithoutMessages()
        trace(
            message = "Fetched ${containers.count()} collections",
            type = TraceType.Silent
        )

        if (!update) {
            pagerMap.clear()
            collectionFlows.clear()
            collectionEntries.value = containers

            loadingCollections = true
        }

        containers.onEach { collection ->
            val result = fetchLatestMessageForCollection(collection)
            result.onSuccess { message ->
                if (message != null) {
                    updatedWithMessages.add(collection.copy(messages = listOf(message)))
                }
            }.onFailure {
                updatedWithMessages.add(collection)
            }
        }

        loadingCollections = false
        collectionEntries.value = updatedWithMessages.sortedByDescending { it.lastMessageMillis }
    }

    suspend fun advanceReadPointer(collectionId: ID) {
        val owner = owner() ?: return

        collectionEntries.update {
            it?.toMutableList()?.apply collections@{
                indexOfFirst { collection -> collection.id == collectionId }
                    .takeIf { index -> index >= 0 }
                    ?.let { index ->
                        val collection = this[index]
                        val newestMessage = collection.newestMessage
                        if (newestMessage != null) {
                            client.advancePointer(
                                owner = owner,
                                chat = collection,
                                to = newestMessage.id,
                                status = MessageStatus.Read
                            ).onSuccess {
                                this[index] = collection.resetUnreadCount()
                            }
                        }
                    }
            }?.toList()
        }
    }

    suspend fun setMuted(collection: NotificationCollectionEntity, muted: Boolean): Result<Boolean> {
        val owner = owner() ?: return Result.failure(Throwable("No owner detected"))

        collectionEntries.update {
            it?.toMutableList()?.apply collections@{
                indexOfFirst { item -> item.id == collection.id }
                    .takeIf { index -> index >= 0 }
                    ?.let { index ->
                        val c = this[index]
                        Timber.d("changing mute state for collection locally")
                        this[index] = c.setMuteState(muted)
                    }
            }?.toList()
        }

        return client.setMuted(owner, collection, muted)
    }

    suspend fun setSubscribed(collection: NotificationCollectionEntity, subscribed: Boolean): Result<Boolean> {
        val owner = owner() ?: return Result.failure(Throwable("No owner detected"))

        collectionEntries.update {
            it?.toMutableList()?.apply collections@{
                indexOfFirst { item -> item.id == collection.id }
                    .takeIf { index -> index >= 0 }
                    ?.let { index ->
                        val c = this[index]
                        Timber.d("changing subscribed state for collection locally")
                        this[index] = c.setSubscriptionState(subscribed)
                    }
            }?.toList()
        }

        return client.setSubscriptionState(owner, collection, subscribed)
    }

    private suspend fun fetchLatestMessageForCollection(collection: NotificationCollectionEntity): Result<ChatMessage?> {
        val encodedId = collection.id.toByteArray().encodeBase64()
        Timber.d("fetching last message for $encodedId")
        val owner = owner() ?: return Result.success(null)
        return client.fetchMessagesFor(owner, collection, limit = 1)
            .onFailure {
                Timber.e(t = it, "Failed to fetch messages for $encodedId.")
            }.map { it.getOrNull(0) }
    }

    private suspend fun fetchCollectionsWithoutMessages(): List<NotificationCollectionEntity> {
        val owner = owner() ?: return emptyList()
        val result = client.fetchChats(owner)
        return result.getOrNull().orEmpty()
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