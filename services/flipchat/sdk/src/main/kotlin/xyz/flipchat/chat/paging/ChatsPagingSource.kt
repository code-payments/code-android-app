package xyz.flipchat.chat.paging

import android.annotation.SuppressLint
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.paging.util.ThreadSafeInvalidationObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.flipchat.internal.db.FcAppDatabase
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastMessage

internal class ChatsPagingSource(
    private val db: FcAppDatabase
) : PagingSource<Int, ConversationWithMembersAndLastMessage>() {

    @SuppressLint("RestrictedApi")
    private val observer =
        ThreadSafeInvalidationObserver(arrayOf("conversations", "messages", "members")) {
            invalidate()
        }

    override fun getRefreshKey(state: PagingState<Int, ConversationWithMembersAndLastMessage>): Int? {
        val anchorPos = state.anchorPosition ?: return null
        val anchorItem = state.closestItemToPosition(anchorPos) ?: return null
        // The anchor item *knows* which page it was loaded from:
        return anchorItem.pageIndex
    }

    @SuppressLint("RestrictedApi")
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ConversationWithMembersAndLastMessage> {
        observer.registerIfNecessary(db)
        val currentPage = params.key ?: 0
        val pageSize = params.loadSize
        val offset = currentPage * pageSize

        return withContext(Dispatchers.IO) {
            try {
                val conversations = db.conversationDao().getPagedConversations(pageSize, offset)
                    .map { it.apply { pageIndex = currentPage } }

                val prevKey = null
                val nextKey = if (conversations.size < pageSize) null else currentPage + 1


                LoadResult.Page(conversations, prevKey, nextKey)
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }
    }
}