package xyz.flipchat.app.features.chat.info

import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.model.ID
import com.getcode.navigation.NavScreenProvider
import xyz.flipchat.app.features.home.TabbedHomeScreen
import com.getcode.navigation.RoomInfoArgs
import com.getcode.navigation.core.LocalCodeNavigator
import xyz.flipchat.app.ui.room.RoomCard
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class ChatInfoScreen(private val info: RoomInfoArgs) : Screen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel = getViewModel<ChatInfoViewModel>()
        val navigator = LocalCodeNavigator.current

        LaunchedEffect(info) {
            viewModel.dispatchEvent(ChatInfoViewModel.Event.OnInfoChanged(info))
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<ChatInfoViewModel.Event.OnLeftRoom>()
                .onEach {
                    navigator.popUntil { it is TabbedHomeScreen }
                }.launchIn(this)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                backButton = true,
                onBackIconClicked = { navigator.pop() }
            )
            ChatInfoScreenContent(viewModel) {
                navigator.push(ScreenRegistry.get(NavScreenProvider.Chat.ChangeCover(it)))
            }
        }
    }
}

@Composable
private fun ChatInfoScreenContent(viewModel: ChatInfoViewModel, onChangeCover: (ID) -> Unit) {
    val state by viewModel.stateFlow.collectAsState()
    CodeScaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset)
            ) {
                if (state.isHost) {
                    CodeButton(
                        modifier = Modifier.fillMaxWidth(),
                        buttonState = ButtonState.Filled,
                        text = "Change Cover Charge",
                    ) {
                        onChangeCover(state.roomInfo.id!!)
                    }
                }

                CodeButton(
                    modifier = Modifier.fillMaxWidth(),
                    buttonState = ButtonState.Filled,
                    text = "Leave Room",
                    isLoading = state.requestBeingSent,
                ) {
                    viewModel.dispatchEvent(ChatInfoViewModel.Event.LeaveRoom)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = CodeTheme.dimens.grid.x12)
                .padding(bottom = CodeTheme.dimens.grid.x20)
        ) {
            RoomCard(
                modifier = Modifier.align(Alignment.Center),
                roomInfo = state.roomInfo
            )
        }
    }
}