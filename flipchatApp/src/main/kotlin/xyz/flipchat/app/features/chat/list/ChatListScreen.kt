package xyz.flipchat.app.features.chat.list

import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.getcode.model.ID
import xyz.flipchat.app.R
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.navigation.screens.NamedScreen
import xyz.flipchat.app.features.chat.openChatDirectiveBottomModal
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
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
    }
}

@Composable
private fun ChatListScreenContent(
    viewModel: ChatListViewModel,
    openChat: (ID) -> Unit,
) {
    val navigator = LocalCodeNavigator.current
    val context = LocalContext.current
    val chats = viewModel.chats.collectAsLazyPagingItems()
    val isLoading = chats.loadState.refresh is LoadState.Loading
    var isInitialLoad by rememberSaveable { mutableStateOf(true) }

    CodeScaffold { padding ->
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
                isLoading && isInitialLoad -> {
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
                else -> {
                    item {
                        CodeButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = CodeTheme.dimens.grid.x6)
                                .padding(horizontal = CodeTheme.dimens.inset),
                            buttonState = ButtonState.Filled,
                            text = stringResource(R.string.action_joinRoom)
                        ) {
                            openChatDirectiveBottomModal(
                                context =  context,
                                viewModel = viewModel,
                                navigator = navigator
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(chats.loadState.refresh) {
        if (chats.loadState.refresh !is LoadState.Loading || chats.itemCount == 0) {
            isInitialLoad = false
        }
    }
}