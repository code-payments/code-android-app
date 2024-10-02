package com.getcode.view.main.chat.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.ChatByUsernameScreen
import com.getcode.navigation.screens.ConversationScreen
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.components.chat.ChatNode

@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
) {
    val state by viewModel.stateFlow.collectAsState()
    val navigator = LocalCodeNavigator.current

    val chatsEmpty by remember(state.conversations) {
        derivedStateOf { state.conversations.isEmpty() }
    }

    CodeScaffold(
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset),
                    buttonState = ButtonState.Filled,
                    text = stringResource(R.string.action_startNewChat)
                ) {
                    navigator.push(ChatByUsernameScreen)
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(state.conversations, key = { it.id }) { chat ->
                ChatNode(chat = chat, showAvatar = true) {
                    navigator.push(ConversationScreen(chatId = chat.id))
                }
                Divider(
                    modifier = Modifier.padding(start = CodeTheme.dimens.inset),
                    color = White10,
                )
            }

            when {
                state.loading -> {
                    item {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(
                                CodeTheme.dimens.grid.x2,
                                CenterVertically
                            ),
                        ) {
                            CodeCircularProgressIndicator()
                            Text(
                                modifier = Modifier.fillMaxWidth(0.6f),
                                text = stringResource(R.string.subtitle_loadingChats),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                chatsEmpty -> {
                    item {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(
                                CodeTheme.dimens.grid.x2,
                                CenterVertically
                            ),
                        ) {
                            Text(
                                modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x1),
                                text = stringResource(R.string.subtitle_dontHaveChats),
                                color = CodeTheme.colors.textSecondary,
                                style = CodeTheme.typography.textMedium
                            )
                        }
                    }
                }
            }
        }
    }
}