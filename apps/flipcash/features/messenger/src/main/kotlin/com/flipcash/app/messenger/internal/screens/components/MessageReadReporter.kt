package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.alerts.LocalReactionAlerts
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
 *
 * Nothing is reported until [enabled]: a chat opening at its unread divider lays out at the bottom
 * first, and reporting that frame would mark read everything the divider is there to point at.
 */
@Composable
internal fun HandleMessageReads(
    listState: LazyListState,
    messages: LazyPagingItems<ChatListItem>,
    enabled: Boolean,
) {
    val actionHandler = LocalChatActionHandler.current
    var lastAdvanced by remember { mutableLongStateOf(0L) }

    LaunchedEffect(listState, messages, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val count = messages.itemCount
            val visibleRange = layout.visibleItemsInfo
            if (visibleRange.isEmpty() || count == 0) return@snapshotFlow null

            var highestId = 0L
            for (info in visibleRange) {
                if (info.index !in 0 until count) continue
                // A notice counts as read once seen, or the pointer would stall under a newest one.
                val message = messages.peek(info.index) as? ChatListItem.TranscriptMessage ?: continue
                if (!message.isFromSelf && message.messageId > highestId) {
                    highestId = message.messageId
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

/**
 * Tells the reaction alerts which messages are on screen, so a reaction on one of them animates its
 * bubble instead of raising a pill. Cleared when the chat leaves composition.
 */
@Composable
internal fun ReportVisibleToReactionAlerts(
    chatId: ChatId?,
    listState: LazyListState,
    messages: LazyPagingItems<ChatListItem>,
) {
    val alerts = LocalReactionAlerts.current ?: return
    if (chatId == null) return
    DisposableEffect(chatId, alerts) {
        onDispose { alerts.clearOnScreen(chatId) }
    }
    LaunchedEffect(chatId, listState, messages, alerts) {
        snapshotFlow {
            val count = messages.itemCount
            buildSet {
                for (info in listState.layoutInfo.visibleItemsInfo) {
                    if (info.index !in 0 until count) continue
                    val message = messages.peek(info.index) as? ChatListItem.TranscriptMessage ?: continue
                    add(message.messageId)
                }
            }
        }
            .distinctUntilChanged()
            .collect { visible -> alerts.reportOnScreen(chatId, visible) }
    }
}
