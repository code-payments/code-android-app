@file:OptIn(ExperimentalFoundationApi::class)

package com.getcode.view.main.chat.conversation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
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
import kotlinx.coroutines.delay

@Composable
fun ChatConversationScreen(
    state: ConversationViewModel.State,
    messages: LazyPagingItems<ConversationMessage>,
    dispatchEvent: (ConversationViewModel.Event) -> Unit,
) {
    CodeScaffold(
        topBar = {
            IdentityRevealHeader(state = state) {
                dispatchEvent(ConversationViewModel.Event.RevealIdentity)
            }
        },
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
                                state.user?.username.orEmpty()
                            )
                        )
                    }

                    ConversationMessageContent.IdentityRevealedToYou -> {
                        AnnouncementMessage(
                            text = stringResource(
                                id = R.string.title_chat_announcement_identityRevealedToYou,
                                state.user?.username.orEmpty()
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
                                state.user?.username.orEmpty()
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

@Composable
private fun IdentityRevealHeader(
    state: ConversationViewModel.State,
    onClick: () -> Unit
) {
    var showRevealHeader by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(state.identityRevealed, state.user) {
        if (!state.identityRevealed) {
            delay(500)
        }
        showRevealHeader = !state.identityRevealed && state.user != null
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
                        style = CodeTheme.typography.button.copy(fontWeight = FontWeight.W700),
                    )
                    val text = buildAnnotatedString {
                        pushStringAnnotation(
                            tag = "reveal",
                            annotation = "reveal identity"
                        )
                        withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                            append("Tap to Reveal Your Identity")
                        }
                    }

                    ClickableText(
                        text = text,
                        style = CodeTheme.typography.button.copy(
                            color = Color.White,
                            fontWeight = FontWeight.W700),
                    ) { offset ->
                        text.getStringAnnotations(
                            tag = "reveal",
                            start = offset,
                            end = offset
                        ).firstOrNull()?.let { onClick() }
                    }
                }
            }
        }
    }

}

