package com.getcode.view.main.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.paging.compose.LazyPagingItems
import com.getcode.R
import com.getcode.manager.BottomBarManager
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.Row
import com.getcode.ui.components.VerticalDivider
import com.getcode.ui.components.chat.MessageList
import com.getcode.ui.components.chat.MessageListEvent
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.ui.components.chat.utils.localized
import com.getcode.ui.utils.withTopBorder

@Composable
fun ChatScreen(
    state: NotificationCollectionViewModel.State,
    messages: LazyPagingItems<ChatItem>,
    dispatch: (NotificationCollectionViewModel.Event) -> Unit,
) {
    val listState = rememberLazyListState()

    val context = LocalContext.current
    val title = state.title.localized

    Column(modifier = Modifier.fillMaxSize()) {
        MessageList(
            modifier = Modifier.weight(1f),
            listState = listState,
            messages = messages,
            dispatch = {
                when (it) {
                    is MessageListEvent.OpenMessageChat -> dispatch(NotificationCollectionViewModel.Event.OpenMessageChat(it.reference))
                    is MessageListEvent.AdvancePointer -> Unit // handled on conversation open
                }
            }
        )

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
                                onPositive = { dispatch(NotificationCollectionViewModel.Event.OnMuteToggled) },
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
                            dispatch(NotificationCollectionViewModel.Event.OnSubscribeToggled)
                            return@CodeButton
                        }

                        BottomBarManager.showMessage(
                            BottomBarManager.BottomBarMessage(
                                title = context.getString(R.string.prompt_title_unsubscribe, title),
                                subtitle = context.getString(
                                    R.string.prompt_description_unsubscribe,
                                    title
                                ),
                                positiveText = context.getString(R.string.action_unsubscribe),
                                negativeText = context.getString(R.string.action_nevermind),
                                onPositive = { dispatch(NotificationCollectionViewModel.Event.OnSubscribeToggled) },
                            )
                        )
                    },
                    shape = RectangleShape,
                    buttonState = ButtonState.Subtle,
                    text = if (state.isSubscribed) stringResource(R.string.action_unsubscribe) else stringResource(
                        id = R.string.action_subscribe
                    )
                )
            }
        }
    }
}