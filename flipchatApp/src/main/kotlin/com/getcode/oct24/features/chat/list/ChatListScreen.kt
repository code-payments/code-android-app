package com.flipchat.features.chat.list

import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.lifecycle.LifecycleEffect
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.getcode.model.ID
import com.getcode.oct24.R
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.navigation.screens.NamedScreen
import com.getcode.oct24.features.chat.list.ChatNode
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.components.chat.ChatNode
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data object ChatListScreen : Screen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_chat)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val viewModel = getActivityScopedViewModel<ChatListViewModel>()
        ChatListScreenContent(
            viewModel = viewModel,
            openChat = {
                navigator.push(ScreenRegistry.get(NavScreenProvider.Chat.Conversation(chatId = it)))
            }
        )

        LifecycleEffect(
            onStarted = {
                val disposedScreen = navigator.lastItem
                if (disposedScreen !is ChatListScreen) {
                    viewModel.dispatchEvent(ChatListViewModel.Event.OnOpen)
                }
            }
        )
    }
}

@Composable
private fun ChatListScreenContent(
    viewModel: ChatListViewModel,
    openChat: (ID) -> Unit,
) {
    val state by viewModel.stateFlow.collectAsState()
    val navigator = LocalCodeNavigator.current

    val chats = viewModel.chats.collectAsLazyPagingItems()
    val isLoading = chats.loadState.refresh is LoadState.Loading
    val isEmpty = chats.itemCount == 0 && chats.loadState.refresh is LoadState.NotLoading

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
            items(chats.itemCount) { index ->
                chats[index]?.let {
                    ChatNode(chat = it) { openChat(it.conversation.id) }
                    Divider(
                        modifier = Modifier.padding(start = CodeTheme.dimens.inset),
                        color = CodeTheme.colors.divider,
                    )
                }
            }

            when {
                isLoading -> {
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

                isEmpty -> {
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