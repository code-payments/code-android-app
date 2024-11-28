package com.getcode.ui.components.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissState
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FixedThreshold
import androidx.compose.material.Icon
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Reply
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.Instant

sealed interface MessageListEvent {
    data class AdvancePointer(val messageId: ID): MessageListEvent
    data class OpenMessageActions(val actions: List<MessageControlAction>): MessageListEvent
    data class OnMarkupEvent(val markup: Markup): MessageListEvent
    data class ReplyToMessage(val message: ChatItem.Message): MessageListEvent
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
    LaunchedEffect(messages.itemSnapshotList, listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                val snapshot = messages.itemSnapshotList.toList()
                val closetChatMessage = snapshot.getClosestChat(index)
                val mostRecentRead = snapshot.filterIsInstance<ChatItem.Message>()
                    .maxByOrNull { it.date }?.date
                if (closetChatMessage != null) {
                    val (id, isFromSelf, date, status) = closetChatMessage
                    if (!isFromSelf && status != MessageStatus.Read) {
                        if (mostRecentRead != null && date >= mostRecentRead) {
                            dispatch(MessageListEvent.AdvancePointer(id))
                        }
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
                        .getOrNull()

                    // reverse layout so -1 to get next
                    val next = runCatching { messages[index - 1] }
                        .map { it as? ChatItem.Message }
                        .getOrNull()

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

                    val showTimestamp by remember(isPreviousGrouped, item, isNextGrouped) {
                        derivedStateOf {
                            // if the provided item requests the timestamp, then show it
                            if (item.showTimestamp) return@derivedStateOf true
                            // if not in a grouping, then always show a timestamp
                            if (!isPreviousGrouped && !isNextGrouped) return@derivedStateOf true
                            // If both previous and next are grouped, check minute boundary with next
                            if (isPreviousGrouped && isNextGrouped) {
                                val nextDate = next?.date
                                if (nextDate != null && item.date.epochSeconds / 60 == nextDate.epochSeconds / 60) {
                                    return@derivedStateOf false
                                }
                            }

                            // Show timestamp only if this is the last message in the group
                            val isLastInGroup = !isNextGrouped || next?.date?.let { nextDate ->
                                nextDate.epochSeconds / 60 != item.date.epochSeconds / 60
                            } ?: true

                            // Show timestamp if it's the last in the group or breaks with the next
                            return@derivedStateOf isLastInGroup
                        }
                    }

                    MessageNode(
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = spacingBefore, bottom = spacingAfter),
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
                        onReply = { dispatch(MessageListEvent.ReplyToMessage(item)) }
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


private fun List<ChatItem?>.getClosestChat(index: Int): Quad<ID, Boolean, Instant, MessageStatus>? {
    if (index !in indices) return null
    val item = this[index]
    return when {
        item is ChatItem.Message -> Quad(item.chatMessageId, item.sender.isSelf, item.date, item.status)
        index > 0 -> getClosestChat(index - 1)
        else -> null
    }
}

private data class Quad<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)