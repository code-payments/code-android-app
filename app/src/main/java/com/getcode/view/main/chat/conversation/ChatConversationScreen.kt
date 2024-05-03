@file:OptIn(ExperimentalFoundationApi::class)

package com.getcode.view.main.chat.conversation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.getcode.R
import com.getcode.model.ConversationMessage
import com.getcode.model.ConversationMessageContent
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.CodeScaffold
import com.getcode.ui.components.conversation.AnnouncementMessage
import com.getcode.ui.components.conversation.ChatInput
import com.getcode.ui.components.conversation.MessageBubble
import com.getcode.ui.components.conversation.utils.HandleMessageChanges
import com.getcode.util.toInstantFromMillis

@Composable
fun ChatConversationScreen(
    state: ConversationViewModel.State,
    messages: LazyPagingItems<ConversationMessage>,
    dispatchEvent: (ConversationViewModel.Event) -> Unit,
) {
    CodeScaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .imePadding()
            ) {
                ChatInput(
                    state = state.textFieldState,
                    onSend = { dispatchEvent(ConversationViewModel.Event.SendMessage) })
            }
        }
    ) { padding ->
        val lazyListState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = lazyListState,
            reverseLayout = true,
            contentPadding = PaddingValues(
                horizontal = CodeTheme.dimens.inset,
                vertical = CodeTheme.dimens.inset,
            ),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3, Alignment.Top),
        ) {
            items(
                count = messages.itemCount,
                key = messages.itemKey { item -> item.id },
            ) { index ->
                val message = messages[index]
                when (val content = message?.content) {
                    ConversationMessageContent.IdentityRevealed -> {
                        AnnouncementMessage(
                            text = stringResource(
                                id = R.string.title_chat_announcement_identityRevealed,
                                state.user.orEmpty()
                            )
                        )
                    }

                    ConversationMessageContent.IdentityRevealedToYou -> {
                        AnnouncementMessage(
                            text = stringResource(
                                id = R.string.title_chat_announcement_identityRevealedToYou,
                                state.user.orEmpty()
                            )
                        )
                    }

                    is ConversationMessageContent.Text -> {
                        MessageBubble(
                            content = content,
                            date = message.dateMillis.toInstantFromMillis()
                        )
                    }

                    ConversationMessageContent.ThanksSent -> {
                        AnnouncementMessage(
                            text = stringResource(id = R.string.title_chat_announcement_thanksSent)
                        )
                    }

                    ConversationMessageContent.TipMessage -> {
                        AnnouncementMessage(
                            text = stringResource(
                                id = R.string.title_chat_announcement_tipHeader,
                                state.tipAmountFormatted.orEmpty()
                            )
                        )
                    }

                    ConversationMessageContent.ThanksReceived -> {
                        AnnouncementMessage(
                            text = stringResource(
                                id = R.string.title_chat_announcement_thanksReceived,
                                state.user.orEmpty()
                            )
                        )
                    }

                    else -> Unit
                }
            }
        }
        
        HandleMessageChanges(listState = lazyListState, items = messages)
    }
}

