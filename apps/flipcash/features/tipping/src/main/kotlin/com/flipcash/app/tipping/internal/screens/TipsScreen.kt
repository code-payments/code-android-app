package com.flipcash.app.tipping.internal.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.tipping.TipStep
import com.flipcash.app.tipping.internal.TipFlowViewModel
import com.flipcash.features.tipping.R
import com.flipcash.shared.chat.ui.ChatListRow
import com.flipcash.shared.chat.ui.ChatRowSubtitle
import com.flipcash.shared.chat.ui.ChatRowTrailing
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.chat.ui.SubtitleText
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun TipsScreen() {
    val viewModel = flowSharedViewModel<TipFlowViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    val navigator = LocalCodeNavigator.current
    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_tips),
                titleAlignment = Alignment.CenterHorizontally,
                endContent = {
                    AppBarDefaults.Close { navigator.hide() }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            stickyHeader {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = CodeTheme.dimens.inset)
                        .background(CodeTheme.colors.background),
                    contentAlignment = Alignment.Center
                ) {
                    CodeButton(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset),
                        text = stringResource(R.string.action_showTipCard),
                        buttonState = ButtonState.Filled,
                        onClick = {
                            navigator.navigate(TipStep.TipCard)
                        },
                    )
                }
            }

            itemsIndexed(state.tipChats) { index, chat ->
                TipChatRow(
                    chat = chat,
                    showDivider = index < state.tipChats.lastIndex,
                ) {
                    navigator.push(AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(chat.chatId)))
                }
            }
        }
    }
}

@Composable
private fun TipChatRow(
    chat: ConversationReference,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    ChatListRow(
        modifier = modifier,
        avatar = {
            ContactAvatar(
                image = chat.image,
                displayName = chat.displayName.orEmpty(),
                modifier = Modifier
                    .requiredSize(CodeTheme.dimens.staticGrid.x8)
                    .clip(CircleShape),
            )
        },
        title = {
            Text(
                modifier = Modifier.weight(1f),
                text = chat.displayName.orEmpty(),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )

            ChatRowTrailing(
                lastActivity = chat.lastActivity,
                unreadCount = chat.unreadCount,
                canOpen = true,
            )
        },
        subtitle = {
            ChatRowSubtitle(
                isTyping = chat.isTyping,
                preview = chat.lastMessagePreview,
                fallback = {
                    SubtitleText("")
                }
            )
        },
        showDivider = showDivider,
        onClick = onClick,
    )
}