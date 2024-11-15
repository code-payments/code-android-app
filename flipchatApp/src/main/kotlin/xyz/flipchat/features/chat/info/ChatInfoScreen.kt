package xyz.flipchat.features.chat.info

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
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import xyz.flipchat.features.home.TabbedHomeScreen
import com.getcode.navigation.RoomInfoArgs
import com.getcode.navigation.core.LocalCodeNavigator
import xyz.flipchat.ui.room.RoomCard
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
            ChatInfoScreenContent(viewModel)
        }
    }
}

@Composable
private fun ChatInfoScreenContent(viewModel: ChatInfoViewModel) {
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
                        enabled = false,
                        text = "Change Cover Charge",
                    ) {

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