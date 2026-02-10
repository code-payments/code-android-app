package com.flipcash.app.tokens

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.flipcash.app.tokens.ui.BuySellSwapTokenViewModel.Event
import com.flipcash.app.tokens.internal.TokenTxProcessingScreen
import com.flipcash.app.tokens.ui.BuySellSwapTokenViewModel
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getStackScopedViewModel
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.ui.utils.DisableSheetGestures
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class TokenTxProcessingScreen(val swapId: SwapId) : ModalScreen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        val viewModel = getStackScopedViewModel<BuySellSwapTokenViewModel>(BuySellFlow.key)
        TokenTxProcessingScreen(viewModel)

        LaunchedEffect(viewModel, swapId) {
            viewModel.dispatchEvent(Event.OnSwapIdChanged(swapId))
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<Event.OnTransactionSuccessful>()
                .onEach {
                    if (BuySellFlow.isForNeededFunds) {
                        navigator.popAll()
                    } else {
                        navigator.popUntil { it is TokenInfoScreen }
                    }
                }.launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<Event.Exit>()
                .onEach {
                    navigator.popUntil { it is TokenInfoScreen }
                }.launchIn(this)
        }

        BackHandler { /* intercept */ }
        DisableSheetGestures()
    }
}