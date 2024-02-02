package com.getcode.view.main.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.getcode.model.ChatMessage
import com.getcode.theme.BrandDark
import com.getcode.theme.BrandLight
import com.getcode.util.formatRelatively
import com.getcode.view.components.CodeCircularProgressIndicator
import com.getcode.view.components.Pill
import kotlinx.datetime.Instant

@Composable
fun ChatScreen(
    state: ChatViewModel.State,
    messages: LazyPagingItems<ChatItem>,
    dispatch: (ChatViewModel.Event) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier.matchParentSize(),
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.Top
        ) {
            items(
                count = messages.itemCount,
                key = messages.itemKey { item -> item.key },
                contentType = messages.itemContentType { item ->
                    when (item) {
                        is ChatItem.Date -> "date"
                        is ChatItem.Message -> "messages"
                    }
                }
            ) { index ->
                when (val item = messages[index]) {
                    is ChatItem.Date -> DateBubble(item.date)
                    is ChatItem.Message -> {
                        Text(text = item.message.dateMillis.toString())
                    }

                    else -> Unit
                }
            }
            if (messages.loadState.append == LoadState.Loading) {
                item {
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CodeCircularProgressIndicator()
                    }
                }
            }
        }
    }
}


@Composable
private fun DateBubble(
    date: String,
) = Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Pill(
        text = date,
        backgroundColor = BrandDark
    )
}
