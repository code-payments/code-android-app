package com.flipcash.app.tokens

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.flipcash.app.onramp.LocalExternalWalletState
import com.flipcash.app.onramp.internal.ExternalWalletState
import com.flipcash.app.tokens.internal.TokenTxProcessingScreen
import com.flipcash.app.tokens.ui.BuySellSwapTokenViewModel
import com.flipcash.app.tokens.ui.BuySellSwapTokenViewModel.Event
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getStackScopedViewModel
import com.getcode.navigation.screens.ModalScreen
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.ui.utils.DisableSheetGestures
import com.getcode.view.LoadingSuccessState
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class TokenTxProcessingScreen(
    val swapId: SwapId,
    val awaitExternalWallet: Boolean = false,
) : ModalScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @IgnoredOnParcel
    override val testTag: String = "token_buy_sell_processing_screen"

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        val viewModel = getStackScopedViewModel<BuySellSwapTokenViewModel>(BuySellFlow.key)

        // When awaiting external wallet, show a local loading indicator that doesn't
        // affect the ViewModel's processingProgress timer. Once OnSwapIdChanged is
        // dispatched the ViewModel takes over with its own loading state and fresh timer.
        var awaitingWallet by remember { mutableStateOf(awaitExternalWallet) }

        TokenTxProcessingScreen(
            viewModel = viewModel,
            processingProgressOverride = if (awaitingWallet) LoadingSuccessState(loading = true) else null,
        )

        if (awaitExternalWallet) {
            val externalWalletState = LocalExternalWalletState.current
            LaunchedEffect(viewModel, swapId) {
                // Wait for the transaction to be submitted before starting swap polling
                snapshotFlow { externalWalletState.deeplinkState }
                    .first { it == ExternalWalletState.TRANSACTED }

                externalWalletState.reset()
                viewModel.dispatchEvent(Event.OnSwapIdChanged(swapId))

                // Wait for the ViewModel's own loading state before dropping override.
                // Both are LoadingSuccessState(loading=true) — data class equality means
                // the indicator's remember(processingState) won't reset, so the timer
                // and progress continue seamlessly with no jump.
                snapshotFlow { viewModel.stateFlow.value.processingProgress }
                    .first { it.loading }

                awaitingWallet = false
            }
        } else {
            LaunchedEffect(viewModel, swapId) {
                viewModel.dispatchEvent(Event.OnSwapIdChanged(swapId))
            }
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
