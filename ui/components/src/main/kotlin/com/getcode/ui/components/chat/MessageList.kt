package com.getcode.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.getcode.model.ID
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.MessageStatus
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.messagecontents.MessageContentActionHandler
import com.getcode.ui.components.chat.messagecontents.MessageContextAction
import com.getcode.ui.components.chat.messagecontents.SelectedReaction
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.ui.components.chat.utils.MessageReaction
import com.getcode.ui.components.chat.utils.MessageTip
import com.getcode.ui.components.text.markup.Markup
import com.getcode.util.formatDateRelatively
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

sealed interface MessageListEvent {
    data class AdvancePointer(val messageId: ID) : MessageListEvent
    data class OpenMessageActions(val messageId: ID, val actions: List<MessageContextAction>) : MessageListEvent
    data class OnMarkupEvent(val markup: Markup.Interactive) : MessageListEvent
    data class ReplyToMessage(val message: ChatItem.Message) : MessageListEvent
    data class ViewOriginalMessage(val messageId: ID, val originalMessageId: ID) : MessageListEvent
    data object UnreadStateHandled : MessageListEvent
    data class TipMessage(val message: ChatItem.Message) : MessageListEvent
    data class ViewUserProfile(val userId: ID): MessageListEvent
    data class AddReaction(val messageId: ID, val emoji: String): MessageListEvent
    data class RemoveReaction(val originalMessageId: ID): MessageListEvent
    data class ShowMessageReactions(
        val tips: List<MessageTip>,
        val reactions: List<MessageReaction>,
        val startingWith: SelectedReaction,
    ) : MessageListEvent
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
    footer: (@Composable () -> Unit)? = null,
    dispatch: (MessageListEvent) -> Unit = { },
) {
    var hasSetAtUnread by rememberSaveable(key = "0") { mutableStateOf(false) }

    HandleMessageReads(listState, messages, hasSetAtUnread) {
        dispatch(MessageListEvent.AdvancePointer(it))
    }

    HandleStartAtUnread(listState, messages, hasSetAtUnread) {
        hasSetAtUnread = true
        dispatch(MessageListEvent.UnreadStateHandled)
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(
            top = CodeTheme.dimens.inset,
            bottom = CodeTheme.dimens.grid.x1,
        ),
        verticalArrangement = Arrangement.Top,
    ) {
        if (footer != null) {
            item {
                footer.invoke()
            }
        }

        items(
            count = messages.itemCount,
            key = messages.itemKey { item -> item.key },
            contentType = messages.itemContentType { item ->
                when (item) {
                    is ChatItem.Date -> "date"
                    is ChatItem.Message -> "message"
                    is ChatItem.UnreadSeparator -> "unread_divider"
                    is ChatItem.Separators -> "separator"
                }
            }
        ) { index ->
            when (val item = messages[index]) {
                is ChatItem.Date -> DateBubble(
                    modifier = Modifier
                        .padding(vertical = CodeTheme.dimens.grid.x2),
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
                        item.message is MessageContent.Announcement -> CodeTheme.dimens.grid.x2
                        isNextGrouped -> 3.dp
                        else -> CodeTheme.dimens.grid.x2
                    }

                    val showTimestamp =
                        remember(isPreviousGrouped, isNextGrouped, item.date, next?.date) {
                            !isPreviousGrouped
                                    || !isNextGrouped
                                    || next?.date?.epochSeconds?.div(60) != item.date.epochSeconds.div(
                                60
                            )
                        }

                    val updatedSender by rememberUpdatedState(item.sender)
                    val updatedActions by rememberUpdatedState(item.messageControls)

                    val updatedTips by rememberUpdatedState(item.tips)
                    val updatedReactions by rememberUpdatedState(item.reactions)

                    val actionHandler = remember(item.tips, item.reactions) {
                        object : MessageContentActionHandler {
                            override fun openMessageControls() {
                                dispatch(
                                    MessageListEvent.OpenMessageActions(item.chatMessageId, updatedActions.actions)
                                )
                            }

                            override fun giveTip() {
                                dispatch(MessageListEvent.TipMessage(item))
                            }

                            override fun addReaction(emoji: String) {
                                dispatch(MessageListEvent.AddReaction(item.chatMessageId, emoji))
                            }

                            override fun removeReaction(reactionMessageId: ID) {
                                dispatch(MessageListEvent.RemoveReaction(reactionMessageId))
                            }

                            override fun viewReactions(selected: SelectedReaction) {
                                dispatch(MessageListEvent.ShowMessageReactions(updatedTips, updatedReactions, selected))
                            }

                            override fun startReply() {
                                dispatch(MessageListEvent.ReplyToMessage(item))
                            }

                            override fun viewOriginalMessage() {
                                item.originalMessage?.id?.let {
                                    dispatch(MessageListEvent.ViewOriginalMessage(item.chatMessageId, it))
                                }
                            }

                            override fun openUserProfile() {
                                item.sender.id?.let {
                                    dispatch(MessageListEvent.ViewUserProfile(it))
                                }
                            }
                        }
                    }
                    MessageNode(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacingBefore, bottom = spacingAfter),
                        contents = item.message,
                        status = item.status,
                        isDeleted = item.isDeleted,
                        deletedBy = item.deletedBy,
                        sender = updatedSender,
                        date = item.date,
                        options = MessageNodeOptions(
                            showStatus = item.showStatus && showTimestamp,
                            showTimestamp = showTimestamp,
                            isPreviousGrouped = isPreviousGrouped,
                            isNextGrouped = isNextGrouped,
                            isInteractive = updatedActions.hasAny,
                            canReplyTo = item.enableReply,
                            canTip = item.enableTipping,
                            linkImagePreviewEnabled = item.enableLinkImagePreview,
                            canViewUserProfiles = item.enableAvatarClicks,
                            onMarkupClicked = if (item.enableMarkup) { markup: Markup.Interactive ->
                                dispatch(MessageListEvent.OnMarkupEvent(markup))
                            } else null,
                            contentStyle = contentStyle,
                        ),
                        tips = item.tips,
                        reactions = item.reactions,
                        originalMessage = item.originalMessage,
                        actionHandler = actionHandler,
                    )
                }

                is ChatItem.UnreadSeparator -> {
                    if (item.count > 0) {
                        UnreadSeparator(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .padding(vertical = CodeTheme.dimens.grid.x2),
                            count = item.count
                        )
                    }
                }

                is ChatItem.Separators -> {
                    item.separators.fastForEach { separator ->
                        when (separator) {
                            is ChatItem.Date -> {
                                DateBubble(
                                    modifier = Modifier
                                        .padding(vertical = CodeTheme.dimens.grid.x2),
                                    date = separator.dateString
                                )
                            }

                            is ChatItem.UnreadSeparator -> {
                                if (separator.count > 0) {
                                    UnreadSeparator(
                                        modifier = Modifier
                                            .fillParentMaxWidth()
                                            .padding(vertical = CodeTheme.dimens.grid.x2),
                                        count = separator.count
                                    )
                                }
                            }
                        }
                    }
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
                        modifier = Modifier
                            .padding(bottom = CodeTheme.dimens.grid.x2),
                        date = dateString
                    )
                }
            }
        }

        // opts out of the list maintaining
        // scroll position when adding elements before the first item
        // we are checking first visible item index to ensure
        // the list doesn't shift when viewing scroll back
        Snapshot.withoutReadObservation {
            if (listState.firstVisibleItemIndex == 0) {
                listState.requestScrollToItem(
                    index = listState.firstVisibleItemIndex,
                    scrollOffset = listState.firstVisibleItemScrollOffset
                )
            }
        }
    }
}

fun <T : Any> LazyPagingItems<T>.safeGet(index: Int): T? {
    return if (index in 0 until itemCount) get(index) else null
}

@Composable
private fun HandleMessageReads(
    listState: LazyListState,
    messages: LazyPagingItems<ChatItem>,
    hasSetAtUnread: Boolean,
    markAsRead: (ID) -> Unit,
) {
    var lastMarkedMessageId: ID? by remember { mutableStateOf(null) }

    LaunchedEffect(listState, messages, hasSetAtUnread) {
        combine(
            snapshotFlow {
                messages.loadState.prepend is LoadState.NotLoading ||
                        messages.loadState.append is LoadState.NotLoading
            }.distinctUntilChanged(),
            snapshotFlow { listState.isScrollInProgress },
            snapshotFlow { listState.firstVisibleItemIndex },
            snapshotFlow { messages.itemCount }.distinctUntilChanged() // Avoid rapid duplicate emissions
        ) { loadState, isScrolling, firstVisibleIndex, itemCount ->
            Triple(loadState, isScrolling, firstVisibleIndex) to itemCount
        }
            .filter { (state, itemCount) ->
                val (loadStateIsNotLoading, isScrolling, firstVisibleIndex) = state
                // Ensure we react to new messages only when truly at the bottom
                loadStateIsNotLoading && !isScrolling && hasSetAtUnread &&
                        (firstVisibleIndex == itemCount - 1 || firstVisibleIndex == 0) && itemCount > 0
            }
            .distinctUntilChanged() // Ensure we don't process the same event multiple times
            .onEach { (state, _) ->
                val (_, _, firstVisibleIndex) = state

                val closestChatMessage =
                    messages[firstVisibleIndex]?.let { it as? ChatItem.Message }

                val mostRecentReadMessage =
                    messages.itemSnapshotList.filterIsInstance<ChatItem.Message>()
                        .filter { it.status == MessageStatus.Read }
                        .maxByOrNull { it.date }

                val mostRecentReadAt = mostRecentReadMessage?.date

                closestChatMessage?.let { message ->
                    // Prevent duplicate mark-as-read calls for the same message
                    if (message.status != MessageStatus.Read && message.chatMessageId != lastMarkedMessageId) {
                        if (mostRecentReadAt == null || message.date >= mostRecentReadAt) {
                            lastMarkedMessageId = message.chatMessageId
                            markAsRead(message.chatMessageId)
                        }
                    }
                }
            }.launchIn(this)
    }

}

@Composable
private fun HandleStartAtUnread(
    listState: LazyListState,
    messages: LazyPagingItems<ChatItem>,
    hasSetAtUnread: Boolean,
    onHandled: () -> Unit,
) {
    // Flag to ensure scroll logic runs only once
    var hasScrolledToUnread by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(listState, messages) {
        snapshotFlow { messages.loadState }
            .filterNot { hasScrolledToUnread }
            .collect { loadState ->
                if (loadState.refresh is LoadState.NotLoading && messages.itemCount > 0) {
                    val separatorIndex = messages.itemSnapshotList
                        .indexOfFirst { it is ChatItem.UnreadSeparator || (it is ChatItem.Separators && it.separators.any { it is ChatItem.UnreadSeparator }) }

                    if (separatorIndex > 0 && !hasSetAtUnread) {
                        val previousItemIndex = separatorIndex - 1

                        // First scroll to bring the item into view
                        listState.scrollToItem(previousItemIndex)

                        // Dynamically calculate the correct offset
                        val itemInfo = listState.layoutInfo.visibleItemsInfo
                            .find { it.index == previousItemIndex }

                        itemInfo?.let {
                            val viewportEnd = listState.layoutInfo.viewportEndOffset
                            val offsetFromEnd = viewportEnd - (it.offset + it.size)

                            // Scroll only if the item isn't sufficiently visible
                            if (offsetFromEnd > 0) {
                                listState.scrollToItem(previousItemIndex, scrollOffset = it.offset)
                            }
                        }
                        hasScrolledToUnread = true
                        onHandled()
                    } else {
                        hasScrolledToUnread = true
                        onHandled()
                    }
                }
            }
    }
}