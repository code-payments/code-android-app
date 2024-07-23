@file:OptIn(ExperimentalFoundationApi::class)

package com.getcode.view.main.chat.conversation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.paging.compose.LazyPagingItems
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.ConnectAccount
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.CodeScaffold
import com.getcode.ui.components.chat.utils.ChatItem
import com.getcode.ui.components.chat.ChatInput
import com.getcode.ui.components.chat.MessageList
import com.getcode.ui.components.chat.MessageListEvent
import com.getcode.ui.components.chat.utils.HandleMessageChanges
import com.getcode.view.main.tip.IdentityConnectionReason
import kotlinx.coroutines.delay

@Composable
fun ChatConversationScreen(
    state: ConversationViewModel.State,
    messages: LazyPagingItems<ChatItem>,
    dispatchEvent: (ConversationViewModel.Event) -> Unit,
) {
    val navigator = LocalCodeNavigator.current
    CodeScaffold(
        topBar = {
            IdentityRevealHeader(state = state) {
                if (state.identityAvailable) {
                    dispatchEvent(ConversationViewModel.Event.RevealIdentity)
                } else {
                    navigator.push(ConnectAccount(IdentityConnectionReason.IdentityReveal))
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .imePadding()
            ) {
                ChatInput(
                    state = state.textFieldState,
                    sendCashEnabled = state.tipChatCash.enabled,
                    onSendMessage = { dispatchEvent(ConversationViewModel.Event.SendMessage) },
                    onSendCash = { dispatchEvent(ConversationViewModel.Event.SendCash) }
                )
            }
        }
    ) { padding ->
        val lazyListState = rememberLazyListState()
        MessageList(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            messages = messages,
            listState = lazyListState,
            dispatch = { event ->
                if (event is MessageListEvent.AdvancePointer) {
                    dispatchEvent(ConversationViewModel.Event.MarkRead(event.messageId))
                }
            }
        )

        HandleMessageChanges(listState = lazyListState, items = messages) { message ->
            dispatchEvent(ConversationViewModel.Event.MarkDelivered(message.chatMessageId))
        }
    }
}

@Composable
private fun IdentityRevealHeader(
    state: ConversationViewModel.State,
    onClick: () -> Unit
) {
    var showRevealHeader by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(state.identityRevealed, state.user) {
        if (state.identityRevealed == false) {
            delay(500)
        }
        showRevealHeader = state.identityRevealed == false
    }

    AnimatedContent(
        targetState = showRevealHeader,
        label = "show/hide identity reveal header"
    ) { show ->
        if (show) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = CodeTheme.dimens.grid.x2),
                color = CodeTheme.colors.background,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Your messages are showing up anonymously.",
                        style = CodeTheme.typography.linkSmall.copy(
                            textDecoration = null,
                            fontWeight = FontWeight.W700),
                    )

                    ClickableText(
                        text = AnnotatedString("Tap to Reveal Your Identity"),
                        style = CodeTheme.typography.linkSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.W700
                        ),
                    ) {
                        onClick()
                    }
                }
            }
        }
    }

}

