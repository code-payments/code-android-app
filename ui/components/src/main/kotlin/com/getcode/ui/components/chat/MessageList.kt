package com.getcode.ui.components.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.getcode.model.ID
import com.getcode.model.MessageStatus
import com.getcode.model.chat.Reference
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.util.formatDateRelatively

sealed interface MessageListEvent {
    data class OpenMessageChat(val reference: Reference): MessageListEvent
    data class AdvancePointer(val messageId: ID): MessageListEvent
}
@Composable
fun MessageList(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    messages: LazyPagingItems<ChatItem>,
    dispatch: (MessageListEvent) -> Unit = { },
) {

    LaunchedEffect(messages.itemSnapshotList, listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                val closetChatMessage = messages.itemSnapshotList.toList().getClosestChat(index)
                if (closetChatMessage != null) {
                    val (id, isFromSelf, status) = closetChatMessage
                    if (!isFromSelf) {
                        dispatch(MessageListEvent.AdvancePointer(id))
                    }
                }
            }
    }

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
                        status = item.status,
                        date = item.date,
                        isPreviousSameMessage = prev == item.chatMessageId,
                        isNextSameMessage = next == item.chatMessageId,
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


private fun List<ChatItem?>.getClosestChat(index: Int): Triple<ID, Boolean, MessageStatus>? {
    if (index !in indices) return null
    val item = this[index]
    return when {
        item is ChatItem.Message -> Triple(item.chatMessageId, item.isFromSelf, item.status)
        index > 0 -> getClosestChat(index - 1)
        else -> null
    }
}