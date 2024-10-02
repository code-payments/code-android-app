package com.flipchat.features.chat.list

import android.os.Parcelable
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
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.flipchat.R
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.navigation.screens.NamedScreen
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.components.chat.ChatNode
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data object ChatListScreen: Screen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_chat)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val viewModel = getActivityScopedViewModel<ChatListViewModel>()
        ChatListScreenContent(viewModel)

        LifecycleEffect(
            onStarted = {
                val disposedScreen = navigator.lastItem
                if (disposedScreen !is ChatListScreen) {
                    viewModel.dispatchEvent(ChatListViewModel.Event.OnOpened)
                }
            }
        )
    }
}

@Composable
private fun ChatListScreenContent(
    viewModel: ChatListViewModel,
) {
    val state by viewModel.stateFlow.collectAsState()
    val navigator = LocalCodeNavigator.current

    val chatsEmpty by remember(state.conversations) {
        derivedStateOf { state.conversations.isEmpty() }
    }

    CodeScaffold(
        bottomBar = {
//            Box(modifier = Modifier.fillMaxWidth()) {
//                CodeButton(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(horizontal = CodeTheme.dimens.inset),
//                    buttonState = ButtonState.Filled,
//                    text = stringResource(R.string.action_startNewChat)
//                ) {
//                    navigator.push(ScreenRegistry.get(NavScreenProvider.Chat.ChatByUsername))
//                }
//            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(state.conversations, key = { it.id }) { chat ->
                ChatNode(chat = chat, showAvatar = true) {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Chat.Conversation(chatId = chat.id)))
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
                            modifier = Modifier.fillParentMaxSize(),
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
                            modifier = Modifier.fillParentMaxSize(),
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