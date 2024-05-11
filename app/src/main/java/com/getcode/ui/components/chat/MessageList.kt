package com.getcode.ui.components.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.getcode.model.ID
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.util.formatDateRelatively

sealed interface MessageListEvent {
    data class ThankUser(val messageId: ID): MessageListEvent
    data class OpenMessageChat(val messageId: ID): MessageListEvent
}
@Composable
fun MessageList(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    messages: LazyPagingItems<ChatItem>,
    dispatch: (MessageListEvent) -> Unit = { },
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        reverseLayout = true,

        contentPadding = PaddingValues(
            horizontal = CodeTheme.dimens.inset,
            vertical = CodeTheme.dimens.inset,
        ),
        verticalArrangement = verticalArrangement,
    ) {
        items(
            count = messages.itemCount,
            key = messages.itemKey { item -> item.key },
            contentType = messages.itemContentType { item ->
                when (item) {
                    is ChatItem.Date -> "separators"
                    is ChatItem.Message -> "messages"
                }
            }
        ) { index ->
            when (val item = messages[index]) {
                is ChatItem.Date -> DateBubble(
                    modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x2),
                    date = item.date
                )
                is ChatItem.Message -> {
                    // reverse layout so +1 to get previous
                    val prev = runCatching { messages[index + 1] }
                        .map { it as? ChatItem.Message }
                        .map { it?.chatMessageId }
                        .getOrNull()
                    // reverse layout so -1 to get next
                    val next = runCatching { messages[index - 1] }
                        .map { it as? ChatItem.Message }
                        .map { it?.chatMessageId }
                        .getOrNull()

                    MessageNode(
                        modifier = Modifier.fillMaxWidth(),
                        contents = item.message,
                        date = item.date,
                        isPreviousSameMessage = prev == item.chatMessageId,
                        isNextSameMessage = next == item.chatMessageId,
                        thankUser = { dispatch(MessageListEvent.ThankUser(item.chatMessageId)) },
                        openMessageChat = { dispatch(MessageListEvent.OpenMessageChat(item.chatMessageId)) }
                    )
                }

                else -> Unit
            }
        }
        // add last separator
        // this isn't handled by paging separators due to no `beforeItem` to reference against
        // at end of list due to reverseLayout
        if (messages.itemCount > 0) {
            (messages[messages.itemCount - 1] as? ChatItem.Message)?.date?.let { date ->
                item {
                    val dateString = remember(date) {
                        date.formatDateRelatively()
                    }
                    DateBubble(
                        modifier = Modifier.padding(bottom = CodeTheme.dimens.grid.x2),
                        date = dateString
                    )
                }
            }
        }
    }
}