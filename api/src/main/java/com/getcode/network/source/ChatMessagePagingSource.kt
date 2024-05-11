package com.getcode.network.source

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.Chat
import com.getcode.model.ChatMessage
import com.getcode.model.Cursor
import com.getcode.model.ID
import com.getcode.network.client.Client
import com.getcode.network.client.fetchMessagesFor

class ChatMessagePagingSource(
    private val client: Client,
    private val owner: KeyPair,
    private val chat: Chat?,
    private val onMessagesFetched: (List<ChatMessage>) -> Unit,
) : PagingSource<Cursor, ChatMessage>() {
    override suspend fun load(
        params: LoadParams<Cursor>
    ): LoadResult<Cursor, ChatMessage> {
        val nextCursor = params.key

        chat ?: return LoadResult.Error(Throwable("Chat not found"))
        val response = client.fetchMessagesFor(owner, chat, cursor = nextCursor, limit = 20)

        response.exceptionOrNull()?.let {
            return LoadResult.Error(it)
        }

        val messages = response.getOrDefault(emptyList())
        onMessagesFetched(messages)
        return LoadResult.Page(
            data = messages,
            prevKey = null, // Only paging forward.
            nextKey = messages.lastOrNull()?.cursor
        )
    }

    override fun getRefreshKey(state: PagingState<Cursor, ChatMessage>): Cursor? {
        // Try to find the page key of the closest page to anchorPosition from
        // either the prevKey or the nextKey; you need to handle nullability
        // here.
        //  * prevKey == null -> anchorPage is the first page.
        //  * nextKey == null -> anchorPage is the last page.
        //  * both prevKey and nextKey are null -> anchorPage is the
        //    initial page, so return null.
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}