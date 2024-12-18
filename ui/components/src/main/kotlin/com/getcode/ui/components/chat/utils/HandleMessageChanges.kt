package com.getcode.ui.components.chat.utils

import android.os.Build
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.getcode.model.chat.MessageStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

@Composable
fun HandleMessageChanges(
    listState: LazyListState,
    items: LazyPagingItems<ChatItem>,
    onMessageDelivered: (ChatItem.Message) -> Unit,
) {
    var lastMessageSent by rememberSaveable {
        mutableLongStateOf(0L)
    }
    var lastMessageReceived by rememberSaveable {
        mutableLongStateOf(0L)
    }

    // handle incoming/outgoing messages - scroll to bottom to reset view in the following circumstances:
    // 1) New message is from self (e.g outgoing)
    // 2) New message is from participant and we are already at the bottom (to prevent rug pull)
    LaunchedEffect(listState, items) {
        snapshotFlow { items.loadState }
            .filter { it.refresh is LoadState.NotLoading }
            .mapNotNull { items.itemSnapshotList.firstOrNull() }
            .filterIsInstance<ChatItem.Message>()
            .distinctUntilChangedBy { it.date }
            .collect { newMessage ->
                if (newMessage.status.isOutgoing()) {
                    if (newMessage.date.toEpochMilliseconds() > lastMessageSent) {
                        listState.handleAndReplayAfter(300) {
                            scrollToItem(0)
                            lastMessageSent = newMessage.date.toEpochMilliseconds()
                        }
                    }
                } else {
                    listState.handleAndReplayAfter(300) {
                        if (newMessage.date.toEpochMilliseconds() > lastMessageReceived) {
                            if (listState.canScrollBackward) {
                                if (newMessage.status == MessageStatus.Unknown) {
                                    onMessageDelivered(newMessage)
                                }
                            }
                        }

                        lastMessageReceived = newMessage.date.toEpochMilliseconds()
                    }
                }
            }
    }
}

private suspend fun LazyListState.handleAndReplayAfter(
    delay: Long,
    block: suspend LazyListState.() -> Unit
) {
    block()
    delay(delay)
    block()
}