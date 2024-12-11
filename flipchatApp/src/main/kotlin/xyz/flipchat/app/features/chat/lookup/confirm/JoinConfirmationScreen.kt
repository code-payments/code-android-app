package xyz.flipchat.app.features.chat.lookup.confirm

import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.model.Currency
import xyz.flipchat.app.features.home.TabbedHomeScreen
import com.getcode.navigation.RoomInfoArgs
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import xyz.flipchat.app.R
import xyz.flipchat.app.ui.room.RoomCard
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.util.resources.LocalResources
import com.getcode.utils.Kin
import com.getcode.utils.formatAmountString
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class JoinConfirmationScreen(val args: RoomInfoArgs, val returnToSender: Boolean = false) :
    Screen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel = getViewModel<JoinConfirmationViewModel>()
        val navigator = LocalCodeNavigator.current

        LaunchedEffect(args) {
            viewModel.dispatchEvent(JoinConfirmationViewModel.Event.OnJoinArgsChanged(args))
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<JoinConfirmationViewModel.Event.OnBecameMember>()
                .map { it.roomId }
                .onEach {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Chat.Conversation(it)))
                }.launchIn(this)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                backButton = true,
                onBackIconClicked = {
                    if (returnToSender) {
                        navigator.pop()
                    } else {
                        navigator.popUntil { it is TabbedHomeScreen }
                    }
                }
            )
            JoinRoomScreenContent(viewModel)
        }
    }
}

@Composable
private fun JoinRoomScreenContent(viewModel: JoinConfirmationViewModel) {
    val state by viewModel.stateFlow.collectAsState()

    CodeScaffold(
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
            ) {
                CodeButton(
                    modifier = Modifier.fillMaxWidth(),
                    buttonState = ButtonState.Filled,
                    text = stringResource(R.string.action_watchRoom),
                    enabled = state.canJoin,
                    isLoading = state.following.loading,
                    isSuccess = state.following.success,
                ) {
                    viewModel.dispatchEvent(JoinConfirmationViewModel.Event.OnWatchRoomClicked)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = CodeTheme.dimens.grid.x12)
                .padding(bottom = CodeTheme.dimens.grid.x15)
        ) {
            RoomCard(
                modifier = Modifier.align(Alignment.Center),
                roomInfo = state.roomInfo
            )
        }
    }
}