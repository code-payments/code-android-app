package com.flipcash.app.persistence.sources.mediator

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Every remaining source of the merged feed failed, so there is nothing left to page. */
class FeedSourcesUnavailableException(keys: List<String>) :
    Exception("No chat feed source could be paged: ${keys.joinToString()}")

/**
 * Pages the merged conversation feed.
 *
 * Room is the [androidx.paging.PagingSource]; this only decides what to fetch next and writes
 * the result into the same tables the feed delegates write, so the list re-emits off the Room
 * change like everything else.
 *
 * Each load advances exactly one source — the one [FeedWatermark] says the merged list is
 * waiting on. That is the difference between this and paging each feed a page at a time: two
 * feeds ordered by recency do not have pages that line up, so "one page of each" leaves a
 * window where one source has items the other has not been asked for.
 *
 * **Known trade-off.** The Room query is not filtered to the watermark. Rows below it — fetched
 * from the deeper source, not yet matched by the shallower one — are rendered, so a chat can
 * move up the list when the other source catches up. Filtering would need an observable
 * watermark to invalidate the `PagingSource` on, and would show an empty list on a cold start
 * until the first page lands. A briefly over-complete list is the cheaper wrong.
 */
@OptIn(ExperimentalPagingApi::class)
class ChatFeedRemoteMediator(
    private val chatTypes: List<ChatType>,
    private val controller: ChatController,
    private val metadataDataSource: ChatMetadataDataSource,
    private val memberDataSource: ChatMemberDataSource,
    private val messageDataSource: ChatMessageDataSource,
) : RemoteMediator<Int, ChatMetadataEntity>() {

    private val watermark = FeedWatermark(chatTypes.map { it.name })

    // Every group id this pass has seen, for the reconciliation at the end of it.
    private val seenGroupIds = mutableSetOf<String>()

    /**
     * The delegates fetch the first page on login, and Room already holds whatever the last
     * session left. Refreshing on every subscribe would re-fetch that first page each time the
     * list is opened.
     */
    override suspend fun initialize(): InitializeAction = InitializeAction.SKIP_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ChatMetadataEntity>,
    ): MediatorResult {
        return try {
            when (loadType) {
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)

                LoadType.REFRESH -> {
                    watermark.reset()
                    seenGroupIds.clear()
                    // Concurrent, so a refresh costs the slower source rather than their sum.
                    coroutineScope {
                        chatTypes
                            .map { type -> async { fetch(type, token = null, limit = state.config.pageSize) } }
                            .awaitAll()
                    }
                }

                LoadType.APPEND -> {
                    val source = watermark.next()
                        ?: return if (watermark.isComplete) {
                            MediatorResult.Success(endOfPaginationReached = true)
                        } else {
                            // Only failed sources remain. Reporting the end of pagination here
                            // would strand the rest of the feed behind a transient error with
                            // nothing to retry it; an error lets Paging offer the retry.
                            MediatorResult.Error(FeedSourcesUnavailableException(watermark.failedKeys))
                        }
                    fetch(
                        chatType = chatTypes.first { it.name == source.key },
                        token = source.token,
                        limit = state.config.pageSize,
                    )
                }
            }

            if (watermark.isComplete) reconcileGroupRemovals()

            MediatorResult.Success(endOfPaginationReached = watermark.isComplete)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }

    private suspend fun fetch(chatType: ChatType, token: PagingToken?, limit: Int) {
        val options = QueryOptions(limit = limit, token = token, descending = true)
        val page = when (chatType) {
            ChatType.GROUP -> controller.getGroupChatFeed(options)
            else -> controller.getDmChatFeed(chatType, options)
        }.getOrElse {
            // One source erroring stops that source advancing and nothing else. It does keep the
            // pass from reporting itself complete, which is what holds back the removal
            // reconciliation below — that needs a whole feed to be sound.
            watermark.fail(chatType.name)
            return
        }

        persist(page.chats)

        if (chatType == ChatType.GROUP) {
            seenGroupIds += page.chats.map { metadataDataSource.chatIdHex(it.chatId) }
        }

        watermark.advance(
            key = chatType.name,
            tail = page.chats.minOfOrNull { it.lastActivity.toEpochMilliseconds() },
            token = page.pagingToken,
            hasMore = page.hasMore,
        )
    }

    private suspend fun persist(chats: List<ChatMetadata>) {
        withContext(Dispatchers.IO) {
            metadataDataSource.upsert(chats)
            for (chat in chats) {
                memberDataSource.upsert(chat.chatId, chat.members)
                chat.lastMessage?.let { messageDataSource.upsert(chat.chatId, listOf(it)) }
            }
        }
    }

    /**
     * Clears the membership of any group this device holds that a complete pass did not return.
     *
     * The event stream is the primary path — `MemberLeft` naming the recipient, and `LeaveChat`
     * clearing it optimistically. This is the backstop for a removal that happened while the
     * stream was down, and it only runs at the end of pagination: a chat missing from a partial
     * pass has not been left, it has not been reached.
     */
    private suspend fun reconcileGroupRemovals() {
        if (ChatType.GROUP !in chatTypes) return
        for (chatIdHex in metadataDataSource.getChatIdsOfType(ChatType.GROUP)) {
            if (chatIdHex in seenGroupIds) continue
            metadataDataSource.setMembership(chatIdHex, isMember = false)
        }
    }
}
