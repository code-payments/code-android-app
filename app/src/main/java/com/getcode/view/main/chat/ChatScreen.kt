package com.getcode.view.main.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.getcode.theme.BrandDark
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.util.formatDateRelatively
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.CodeCircularProgressIndicator
import com.getcode.view.components.Pill
import com.getcode.view.components.chat.MessageNode

@Composable
fun ChatScreen(
    state: ChatViewModel.State,
    messages: LazyPagingItems<ChatItem>,
    dispatch: (ChatViewModel.Event) -> Unit,
) {
    val listState = rememberLazyListState()
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(
                horizontal = CodeTheme.dimens.inset,
                vertical = CodeTheme.dimens.grid.x6,
            ),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2, Alignment.Top),
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
                    is ChatItem.Date -> DateBubble(item.date)
                    is ChatItem.Message -> {
                        MessageNode(
                            modifier = Modifier.fillMaxWidth(),
                            contents = item.message,
                            date = item.date
                        )
                    }

                    else -> Unit
                }
            }
            // add last separator
            // this isn't handled by paging separators due to no `beforeItem` to reference against
            // at end of list
            if (messages.itemCount > 0) {
                (messages[messages.itemCount - 1] as? ChatItem.Message)?.date?.let { date ->
                    item {
                        val dateString = remember(date) {
                            date.formatDateRelatively()
                        }
                        DateBubble(dateString)
                    }
                }
            }
        }

        val borderWidth = CodeTheme.dimens.border
        CodeButton(
            modifier = Modifier.fillMaxWidth()
                .drawBehind {

                    val strokeWidth = borderWidth.toPx()
                    drawLine(
                        color = BrandLight,
                        Offset(0f, 0f),
                        Offset(size.width, 0f),
                        strokeWidth
                    )
                },
            onClick = { },
            shape = RectangleShape,
            buttonState = ButtonState.Subtle,
            text = if (state.isMuted) "Unmute" else "Mute"
        )
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
