package com.getcode.oct24.network.controllers

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.getcode.model.chat.Chat
import javax.inject.Inject

//class ConversationListController @Inject constructor(
//    private val historyController: ChatHistoryController,
//) {
//    val isLoading: Boolean
//        get() = false // historyController.loadingMessages
//
//    fun observeConversations() = historyController.chats
//
//    suspend fun fetchChats() = historyController.fetch(true)
//}

class ChatPagingSource(
    private val chats: List<Chat>
) : PagingSource<Int, Chat>() {

    override fun getRefreshKey(state: PagingState<Int, Chat>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Chat> {
        val currentList = chats
        val position = params.key ?: 0
        val pageSize = params.loadSize

        return try {
            val items = currentList.subList(
                position.coerceAtMost(currentList.size),
                (position + pageSize).coerceAtMost(currentList.size)
            )

            LoadResult.Page(
                data = items,
                prevKey = if (position > 0) position - pageSize else null,
                nextKey = if (position + pageSize < currentList.size) position + pageSize else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}