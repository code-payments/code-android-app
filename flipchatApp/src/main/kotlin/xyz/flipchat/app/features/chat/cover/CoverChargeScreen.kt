package xyz.flipchat.app.features.chat.cover

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
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.model.ID
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.NamedScreen
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R
import xyz.flipchat.app.ui.AmountWithKeypad

@Parcelize
data class CoverChargeScreen(val roomId: ID) : Screen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_changeCoverCharge)


    @Composable
    override fun Content() {
        val viewModel = getViewModel<CoverChargeViewModel>()
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
            ChangeCoverScreenContent(viewModel)
        }

        LaunchedEffect(viewModel) {
            viewModel.dispatchEvent(CoverChargeViewModel.Event.OnRoomIdChanged(roomId))
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<CoverChargeViewModel.Event.OnCoverChangedSuccessfully>()
                .onEach {
                    navigator.pop()
                }.launchIn(this)
        }
    }
}

@Composable
private fun ChangeCoverScreenContent(
    viewModel: CoverChargeViewModel,
) {
    val state by viewModel.stateFlow.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AmountWithKeypad(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            amountAnimatedModel = state.amountAnimatedModel,
            isKin = true,
            placeholder = "0",
            onNumberPressed = { viewModel.dispatchEvent(CoverChargeViewModel.Event.OnNumberPressed(it)) },
            onBackspace = { viewModel.dispatchEvent(CoverChargeViewModel.Event.OnBackspace) },
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            CodeButton(
                enabled = state.canChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .navigationBarsPadding(),
                buttonState = ButtonState.Filled,
                text = stringResource(R.string.action_saveChanges),
                isLoading = state.submitting,
                isSuccess = state.success,
            ) {
                viewModel.dispatchEvent(CoverChargeViewModel.Event.OnChangeCover)
            }
        }
    }
}