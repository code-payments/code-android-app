package com.getcode.view.main.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.getcode.R
import com.getcode.manager.BottomBarManager
import com.getcode.theme.BrandDark
import com.getcode.theme.CodeTheme
import com.getcode.util.formatDateRelatively
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.Pill
import com.getcode.ui.components.Row
import com.getcode.ui.components.VerticalDivider
import com.getcode.ui.components.chat.MessageNode
import com.getcode.ui.components.chat.localized
import com.getcode.ui.utils.withTopBorder

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
                            date = item.date,
                            isPreviousSameMessage = prev == item.chatMessageId,
                            isNextSameMessage = next == item.chatMessageId,
                            openTipChat = { dispatch(ChatViewModel.Event.OpenTipChat(item.chatMessageId)) }
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

        val context = LocalContext.current
        val title = state.title.localized

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .withTopBorder()
        ) {
            if (state.canMute) {
                CodeButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        BottomBarManager.showMessage(
                            BottomBarManager.BottomBarMessage(
                                title = context.getString(
                                    if (state.isMuted) R.string.prompt_title_unmute else R.string.prompt_title_mute,
                                    title
                                ),
                                subtitle = context.getString(
                                    if (state.isMuted) R.string.prompt_description_unmute else R.string.prompt_description_mute,
                                    title
                                ),
                                positiveText = context.getString(if (state.isMuted) R.string.action_unmute else R.string.action_mute),
                                negativeText = context.getString(R.string.action_nevermind),
                                onPositive = { dispatch(ChatViewModel.Event.OnMuteToggled) },
                            )
                        )
                    },
                    shape = RectangleShape,
                    buttonState = ButtonState.Subtle,
                    text = stringResource(if (state.isMuted) R.string.action_unmute else R.string.action_mute)
                )
            }

            if (state.canMute && state.canUnsubscribe) {
                VerticalDivider(
                    thickness = Dp.Hairline,
                )
            }

            if (state.canUnsubscribe) {
                CodeButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (!state.isSubscribed) {
                            dispatch(ChatViewModel.Event.OnSubscribeToggled)
                            return@CodeButton
                        }

                        BottomBarManager.showMessage(
                            BottomBarManager.BottomBarMessage(
                                title = context.getString(R.string.prompt_title_unsubscribe, title),
                                subtitle = context.getString(R.string.prompt_description_unsubscribe, title),
                                positiveText = context.getString(R.string.action_unsubscribe),
                                negativeText = context.getString(R.string.action_nevermind),
                                onPositive = { dispatch(ChatViewModel.Event.OnSubscribeToggled) },
                            )
                        )
                    },
                    shape = RectangleShape,
                    buttonState = ButtonState.Subtle,
                    text = if (state.isSubscribed) stringResource(R.string.action_unsubscribe) else stringResource(id = R.string.action_subscribe)
                )
            }
        }
    }
}


@Composable
private fun DateBubble(
    modifier: Modifier = Modifier,
    date: String,
) = Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Pill(
        text = date,
        backgroundColor = BrandDark
    )
}