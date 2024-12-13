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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.getcode.model.ID
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.MessageStatus
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.messagecontents.MessageControlAction
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.ui.components.text.markup.Markup
import com.getcode.util.formatDateRelatively
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter

sealed interface MessageListEvent {
    data class AdvancePointer(val messageId: ID) : MessageListEvent
    data class OpenMessageActions(val actions: List<MessageControlAction>) : MessageListEvent
    data class OnMarkupEvent(val markup: Markup) : MessageListEvent
    data class ReplyToMessage(val message: ChatItem.Message) : MessageListEvent
    data class ViewOriginalMessage(val messageId: ID) : MessageListEvent
}

data class MessageListPointer(
    val current: ChatItem.Message,
    val previous: ChatItem.Message?,
    val next: ChatItem.Message?
)

data class MessageListPointerResult(
    val isPreviousGrouped: Boolean,
    val isNextGrouped: Boolean,
)

@Composable
fun MessageList(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentStyle: TextStyle = MessageNodeDefaults.ContentStyle,
    messages: LazyPagingItems<ChatItem>,
    handleMessagePointers: (MessageListPointer) -> MessageListPointerResult = { (current, previous, next) ->
        val prevGrouped = previous?.chatMessageId == current.chatMessageId
        val nextGrouped = next?.chatMessageId == current.chatMessageId
        MessageListPointerResult(prevGrouped, nextGrouped)
    },
    dispatch: (MessageListEvent) -> Unit = { },
) {
    var hasSetAtUnread by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    LaunchedEffect(listState, messages, hasSetAtUnread) {
        combine(
            snapshotFlow {
                messages.loadState.prepend is LoadState.NotLoading ||
                        messages.loadState.append is LoadState.NotLoading
            }.distinctUntilChanged(),
            snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged()
        ) { loadState, firstVisibleIndex ->
            Pair(loadState, firstVisibleIndex)
        }.filter { (loadStateIsNotLoading, _) ->
            // Filter out when messages are loading or unread flag is not set
            loadStateIsNotLoading && messages.itemCount > 0 && hasSetAtUnread
        }.distinctUntilChanged().collect { (_, firstVisibleIndex) ->
                val closestChatMessage =
                    messages[firstVisibleIndex]?.let { it as? ChatItem.Message }

                val mostRecentReadMessage =
                    messages.itemSnapshotList.filterIsInstance<ChatItem.Message>()
                        .filter { it.status == MessageStatus.Read }
                        .maxByOrNull { it.date }

                val mostRecentReadAt = mostRecentReadMessage?.date

                closestChatMessage?.let { message ->
                    if (!message.sender.isSelf &&
                        message.status != MessageStatus.Read
                    ) {
                        if (mostRecentReadAt == null || message.date >= mostRecentReadAt) {
                            dispatch(MessageListEvent.AdvancePointer(message.chatMessageId))
                        }
                    }
                }
            }
    }

    LaunchedEffect(listState, messages) {
        snapshotFlow { messages.loadState }
            .collect { loadState ->
                if (loadState.refresh is LoadState.NotLoading && messages.itemCount > 0) {
                    val separatorIndex = messages.itemSnapshotList
                        .indexOfFirst { it is ChatItem.UnreadSeparator }

                    if (separatorIndex >= 0 && !hasSetAtUnread) {
                        // Calculate the center offset
                        val centerOffset = with(density) {
                            val viewportHeight = listState.layoutInfo.viewportSize.height.toDp()
                            viewportHeight.roundToPx() / 2
                        }

                        hasSetAtUnread = true
                        listState.scrollToItem(separatorIndex, scrollOffset = -centerOffset)
                    } else {
                        hasSetAtUnread = true
                    }
                }
            }
    }


    LazyColumn(
        modifier = modifier,
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(
            vertical = CodeTheme.dimens.inset,
        ),
        verticalArrangement = Arrangement.Top,
    ) {
        items(
            count = messages.itemCount,
            key = messages.itemKey { item -> item.key },
            contentType = messages.itemContentType { item ->
                when (item) {
                    is ChatItem.Date -> "separators"
                    is ChatItem.Message -> "messages"
                    is ChatItem.UnreadSeparator -> "unread_divider"
                }
            }
        ) { index ->
            when (val item = messages[index]) {
                is ChatItem.Date -> DateBubble(
                    modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x2),
                    date = item.dateString
                )

                is ChatItem.Message -> {
                    // reverse layout so +1 to get previous
                    val prev = messages.safeGet(index + 1) as? ChatItem.Message
                    val next = messages.safeGet(index - 1) as? ChatItem.Message

                    val pointerRef = MessageListPointer(item, prev, next)

                    val (isPreviousGrouped, isNextGrouped) = handleMessagePointers(pointerRef)

                    val spacingBefore = when {
                        item.message is MessageContent.Announcement -> CodeTheme.dimens.grid.x1
                        else -> 0.dp
                    }
                    val spacingAfter = when {
                        index > messages.itemCount -> 0.dp
                        item.message is MessageContent.Announcement -> CodeTheme.dimens.inset
                        isNextGrouped -> 3.dp
                        else -> CodeTheme.dimens.grid.x3
                    }

                    val showTimestamp =
                        remember(isPreviousGrouped, isNextGrouped, item.date, next?.date) {
                            !isPreviousGrouped || !isNextGrouped || next?.date?.epochSeconds?.div(60) != item.date.epochSeconds.div(
                                60
                            )
                        }

                    MessageNode(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacingBefore, bottom = spacingAfter)
                            .animateItem(),
                        contents = item.message,
                        status = item.status,
                        isDeleted = item.isDeleted,
                        sender = item.sender,
                        date = item.date,
                        options = MessageNodeOptions(
                            showStatus = item.showStatus && showTimestamp,
                            showTimestamp = showTimestamp,
                            isPreviousGrouped = isPreviousGrouped,
                            isNextGrouped = isNextGrouped,
                            isInteractive = item.messageControls.hasAny,
                            canReplyTo = item.enableReply,
                            onMarkupClicked = if (item.enableMarkup) { markup: Markup ->
                                dispatch(MessageListEvent.OnMarkupEvent(markup))
                            } else null,
                            contentStyle = contentStyle,
                        ),
                        openMessageControls = {
                            dispatch(
                                MessageListEvent.OpenMessageActions(
                                    item.messageControls.actions
                                )
                            )
                        },
                        onReply = { dispatch(MessageListEvent.ReplyToMessage(item)) },
                        originalMessage = item.originalMessage,
                        onViewOriginalMessage = {
                            dispatch(MessageListEvent.ViewOriginalMessage(it))
                        }
                    )
                }

                is ChatItem.UnreadSeparator -> {
                    UnreadSeparator(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(vertical = CodeTheme.dimens.grid.x2),
                        count = item.count
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

private data class Quad<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

fun <T : Any> LazyPagingItems<T>.safeGet(index: Int): T? {
    return if (index in 0 until itemCount) get(index) else null
}