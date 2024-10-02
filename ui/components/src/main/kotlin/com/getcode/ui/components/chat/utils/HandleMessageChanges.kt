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
import androidx.paging.compose.LazyPagingItems
import com.getcode.ui.utils.isScrolledToTheBeginning
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

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
    LaunchedEffect(listState) {
        snapshotFlow { items.itemSnapshotList }
            .map { it.firstOrNull() }
            .filterNotNull()
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
                            if (listState.isScrolledToTheBeginning()) {
                                // Android 10 we have to utilize a mimic for IME nested scrolling
                                // using the [LazyListState#isScrollInProgress] which animateScrollToItem triggers
                                // thus causing the IME to be dismissed when we trigger the sync.
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                    listState.scrollToItem(0)
                                } else {
                                    listState.animateScrollToItem(0)
                                }
                            }

                            onMessageDelivered(newMessage)
                            lastMessageReceived = newMessage.date.toEpochMilliseconds()
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