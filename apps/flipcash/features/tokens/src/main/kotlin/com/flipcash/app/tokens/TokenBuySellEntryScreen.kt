package com.flipcash.app.tokens

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenSwapPurpose
import com.flipcash.app.onramp.LocalExternalWalletState
import com.flipcash.app.tokens.internal.BuySellTokenEntryScreen
import com.flipcash.features.tokens.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getStackScopedViewModel
import com.getcode.navigation.modal.ModalScreen
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.util.permissions.LocalPermissionChecker
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize


@Parcelize
class TokenBuySellEntryScreen(
    private val purpose: TokenSwapPurpose
): ModalScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun ModalContent() {
        val context = LocalContext.current
        val navigator = LocalCodeNavigator.current
        val viewModel = getStackScopedViewModel<BuySellSwapTokenViewModel>(BuySellFlow.key)
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        val externalWalletOnRamp = LocalExternalWalletState.current
        val permissions = LocalPermissionChecker.current
        val composeScope = rememberCoroutineScope()

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                isInModal = true,
                title = when (purpose) {
                    is TokenSwapPurpose.BalanceIncrease -> stringResource(R.string.title_amountToBuy)
                    is TokenSwapPurpose.BalanceDecrease -> stringResource(R.string.title_amountToSell)
                },
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                onBackIconClicked = {
                    if (state.buyProgress.loading) {
                        // swallow
                    } else {
                        navigator.pop()
                    }
                }
            )

            BuySellTokenEntryScreen(viewModel)
        }

        LaunchedEffect(viewModel) {
            viewModel.dispatchEvent(BuySellSwapTokenViewModel.Event.OnPurposeChanged(purpose))
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<BuySellSwapTokenViewModel.Event.ShowSellReceipt>()
                .onEach {
                    navigator.push(ScreenRegistry.get(AppRoute.Token.SellReceipt))
                }.launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<BuySellSwapTokenViewModel.Event.CreateAndSendTransactionToWallet>()
                .onEach { (token, amount) ->
                    externalWalletOnRamp.tokenToPurchase = token
                    externalWalletOnRamp.amount = amount
                }.launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<BuySellSwapTokenViewModel.Event.Exit>()
                .onEach {
                    navigator.popUntil { it is TokenInfoScreen }
                }.launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<BuySellSwapTokenViewModel.Event.OnPurchaseSubmitted>()
                .map { it.swapId }
                .onEach { swapId ->
                    navigator.push(ScreenRegistry.get(AppRoute.Token.TxProcessing(swapId)))
                }.launchIn(this)
        }
    }
}