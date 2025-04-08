package xyz.flipchat.app.features.chat.lookup

import android.os.Parcelable
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
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import xyz.flipchat.app.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.NamedScreen
import xyz.flipchat.app.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.ui.AmountWithKeypad

@Parcelize
class LookupRoomScreen : Screen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_lookupRoom)

    @Composable
    override fun Content() {
        val viewModel = getViewModel<LookupRoomViewModel>()
        val navigator = LocalCodeNavigator.current

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = name,
                backButton = true,
                onBackIconClicked = navigator::pop
            )
            LookupRoomScreenContent(viewModel)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<LookupRoomViewModel.Event.OpenExistingRoom>()
                .map { it.roomId }
                .onEach {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Room.Messages(it)))
                }.launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<LookupRoomViewModel.Event.OnOpenConfirmation>()
                .map { it.args }
                .onEach {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Room.Info(it, returnToSender = true)))
                }.launchIn(this)
        }
    }

    @Composable
    private fun LookupRoomScreenContent(
        viewModel: LookupRoomViewModel,
    ) {
        val state by viewModel.stateFlow.collectAsState()

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            AmountWithKeypad(
                modifier = Modifier.weight(1f),
                state.amountAnimatedModel,
                prefix = "#",
                hint = stringResource(R.string.subtitle_enterRoomNumber),
                onNumberPressed = { viewModel.dispatchEvent(LookupRoomViewModel.Event.OnNumberPressed(it)) },
                onBackspace = { viewModel.dispatchEvent(LookupRoomViewModel.Event.OnBackspace) },
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                CodeButton(
                    enabled = state.canLookup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(bottom = CodeTheme.dimens.grid.x2)
                        .navigationBarsPadding(),
                    buttonState = ButtonState.Filled,
                    text = stringResource(R.string.action_next),
                    isLoading = state.lookingUp,
                    isSuccess = state.success,
                ) {
                    viewModel.dispatchEvent(LookupRoomViewModel.Event.OnLookupRoom)
                }
            }
        }
    }
}