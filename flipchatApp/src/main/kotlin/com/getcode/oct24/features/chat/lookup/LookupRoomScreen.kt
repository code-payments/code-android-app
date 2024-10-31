package com.getcode.oct24.features.chat.lookup

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
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.navigation.ConfirmJoinArgs
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.NamedScreen
import com.getcode.oct24.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.text.AmountArea
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeKeyPad
import com.getcode.utils.network.LocalNetworkObserver
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.Parcelize

@Parcelize
data object LookupRoomScreen : Screen, NamedScreen, Parcelable {

    override val name: String
        @Composable get() = stringResource(R.string.title_joinRoom)

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
                .filterIsInstance<LookupRoomViewModel.Event.OnOpenConfirmation>()
                .map { it.args }
                .onEach {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Chat.Lookup.Confirm(it)))
                }.launchIn(this)
        }
    }

    @Composable
    private fun LookupRoomScreenContent(
        viewModel: LookupRoomViewModel,
    ) {
        val networkObserver = LocalNetworkObserver.current
        val networkState by networkObserver.state.collectAsState()

        val state by viewModel.stateFlow.collectAsState()
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier.weight(0.65f)
            ) {
                AmountArea(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = CodeTheme.dimens.inset),
                    amountPrefix = "#",
                    amountText = "",
                    placeholder = " ",
                    captionText = "Enter Room Number",
                    isAltCaptionKinIcon = false,
                    uiModel = state.amountAnimatedModel,
                    isAnimated = true,
                    isClickable = false,
                    networkState = networkState,
                    textStyle = CodeTheme.typography.displayLarge,
                )
            }

            CodeKeyPad(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = CodeTheme.dimens.inset)
                    .weight(1f),
                onNumber = { viewModel.dispatchEvent(LookupRoomViewModel.Event.OnNumberPressed(it)) },
                onClear = { viewModel.dispatchEvent(LookupRoomViewModel.Event.OnBackspace) },
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