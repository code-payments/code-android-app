package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.paging.compose.LazyPagingItems
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.LocalChatActionHandler
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/**
 * Reports the newest incoming message the viewer has actually had on screen, so the read pointer
 * follows the scroll rather than the arrival.
 */
@Composable
internal fun HandleMessageReads(
    listState: LazyListState,
    messages: LazyPagingItems<ChatListItem>,
) {
    val actionHandler = LocalChatActionHandler.current
    var lastAdvanced by remember { mutableLongStateOf(0L) }

    LaunchedEffect(listState, messages) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val count = messages.itemCount
            val visibleRange = layout.visibleItemsInfo
            if (visibleRange.isEmpty() || count == 0) return@snapshotFlow null

            var highestId = 0L
            for (info in visibleRange) {
                if (info.index !in 0 until count) continue
                val bubble = messages.peek(info.index) as? ChatListItem.ContentBubble ?: continue
                if (!bubble.isFromSelf && bubble.messageId > highestId) {
                    highestId = bubble.messageId
                }
            }
            if (highestId > 0L) highestId else null
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest { messageId ->
                if (messageId > lastAdvanced) {
                    lastAdvanced = messageId
                    actionHandler(ChatAction.AdvanceReadPointer(messageId))
                }
            }
    }
}
