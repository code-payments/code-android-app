package xyz.flipchat.chat.paging

import android.annotation.SuppressLint
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.paging.util.ThreadSafeInvalidationObserver
import com.getcode.model.ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.flipchat.internal.db.FcAppDatabase
import xyz.flipchat.services.domain.model.chat.InflatedConversationMessage

internal class MessagingPagingSource(
    private val chatId: ID,
    private val userId: () -> ID?,
    private val db: FcAppDatabase
) : PagingSource<Int, InflatedConversationMessage>() {

    @SuppressLint("RestrictedApi")
    private val observer =
        ThreadSafeInvalidationObserver(arrayOf("conversations", "messages", "members")) {
            invalidate()
        }

    override fun getRefreshKey(state: PagingState<Int, InflatedConversationMessage>): Int? {
        val anchorPos = state.anchorPosition ?: return null
        val anchorItem = state.closestItemToPosition(anchorPos) ?: return null
        // The anchor item *knows* which page it was loaded from:
        return anchorItem.pageIndex
    }

    @SuppressLint("RestrictedApi")
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, InflatedConversationMessage> {
        observer.registerIfNecessary(db)
        val currentPage = params.key ?: 0
        val pageSize = params.loadSize
        val offset = currentPage * pageSize

        return withContext(Dispatchers.Default) {
            try {
                val messages =
                    db.conversationMessageDao()
                        .getPagedMessagesWithDetails(chatId, pageSize, offset, userId())
                        .map { it.copy(pageIndex = currentPage) }

                val prevKey = if (currentPage > 0 && messages.isNotEmpty()) currentPage - 1 else null
                val nextKey = if (messages.size < pageSize) null else currentPage + 1

                LoadResult.Page(
                    data = messages,
                    prevKey = prevKey,
                    nextKey = nextKey,
                )
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }
    }
}

