package xyz.flipchat.app.features.chat.list

import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.currentOrThrow
import com.getcode.model.ID
import xyz.flipchat.app.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.navigation.screens.AppScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.navigation.screens.OnScreenResult
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.core.addIf
import com.getcode.util.resources.LocalResources
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R
import xyz.flipchat.app.features.chat.openChatDirectiveBottomModal

@Parcelize
class RoomListScreen : AppScreen(), NamedScreen, Parcelable {

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
                navigator.push(ScreenRegistry.get(NavScreenProvider.Room.Messages(chatId = it)))
            }
        )

        OnScreenResult {
            if (it is List<*>) {
                val roomId = runCatching { it as? ID }.getOrNull()
                if (roomId != null) {
                    delay(400)
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Room.Messages(chatId = roomId)))
                }
            } else if (it is Boolean) {
                if (it) {
                    viewModel.dispatchEvent(ChatListViewModel.Event.OnAccountCreated)
                }
            }
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<ChatListViewModel.Event.NeedsAccountCreated>()
                .onEach {
                    navigator.show(ScreenRegistry.get(NavScreenProvider.CreateAccount.Start))
                }.launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<ChatListViewModel.Event.CreateRoom>()
                .onEach {
                    navigator.show(ScreenRegistry.get(NavScreenProvider.Room.Create))
                }.launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<ChatListViewModel.Event.OpenRoom>()
                .onEach {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Room.Messages(it.roomId)))
                }.launchIn(this)
        }
    }
}

@Composable
private fun ChatListScreenContent(
    viewModel: ChatListViewModel,
    openChat: (ID) -> Unit,
) {
    val navigator = LocalCodeNavigator.current
    val resources = LocalResources.currentOrThrow
    val state by viewModel.stateFlow.collectAsState()
    val chats = viewModel.chats.collectAsLazyPagingItems()
    val isLoading = chats.loadState.refresh is LoadState.Loading
    var isInitialLoad by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()

    CodeScaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = CodeTheme.dimens.inset),
            state = listState
        ) {
            items(
                count = chats.itemCount,
//                key = chats.itemKey { it.id },
                contentType = chats.itemContentType { "chat" }
            ) { index ->
                chats[index]?.let {
                    Column {
                        ChatNode(
                            chat = it,
                            onToggleMute = { mute ->
                                if (mute) {
                                    viewModel.dispatchEvent(ChatListViewModel.Event.MuteRoom(it.id))
                                } else {
                                    viewModel.dispatchEvent(ChatListViewModel.Event.UnmuteRoom(it.id))
                                }
                            },
                        ) { openChat(it.conversation.id) }
                    }
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
                                .padding(horizontal = CodeTheme.dimens.inset)
                                .addIf(!state.isLoggedIn) { Modifier.navigationBarsPadding() },
                            buttonState = ButtonState.Filled,
                            text = stringResource(R.string.action_findRoom)
                        ) {
                            openChatDirectiveBottomModal(
                                resources = resources,
                                createCost = state.createRoomCost,
                                viewModel = viewModel,
                                navigator = navigator
                            )
                        }
                    }
                }
            }

            // opts out of the list maintaining
            // scroll position when adding elements before the first item
            // we are checking first visible item index to ensure
            // the list doesn't shift when scrolled
            Snapshot.withoutReadObservation {
                if (listState.firstVisibleItemIndex == 0) {
                    listState.requestScrollToItem(
                        index = listState.firstVisibleItemIndex,
                        scrollOffset = listState.firstVisibleItemScrollOffset
                    )
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