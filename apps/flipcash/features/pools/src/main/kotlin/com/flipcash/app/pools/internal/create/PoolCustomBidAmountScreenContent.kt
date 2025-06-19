package com.flipcash.app.pools.internal.create

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.money.CurrencySelectionKind
import com.flipcash.app.core.ui.AmountWithKeypad
import com.flipcash.features.pools.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun PoolCustomBidAmountScreen(viewModel: PoolCreateViewModel) {
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    PoolCustomBidAmountScreenContent(state.bidEntryState, viewModel::dispatchEvent)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<PoolCreateViewModel.Event.OnAmountAccepted>()
            .onEach {
                navigator.push(ScreenRegistry.get(NavScreenProvider.HomeScreen.Pools.Create.Confirmation))
            }.launchIn(this)
    }
}

@Composable
private fun PoolCustomBidAmountScreenContent(
    state: BidEntryState,
    dispatchEvent: (PoolCreateViewModel.Event) -> Unit
) {
    val navigator = LocalCodeNavigator.current

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AmountWithKeypad(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            amountAnimatedModel = state.amountAnimatedModel,
            currencyFlag = state.currencyModel.selected?.resId,
            prefix = state.currencyModel.selected?.symbol.orEmpty(),
            placeholder = "0",
            hint = if (state.isError) {
                stringResource(R.string.subtitle_poolBidLimitExceeded, state.maxAvailableForBid)
            } else {
                stringResource(R.string.subtitle_poolBidHint, state.maxAvailableForBid)
            },
            decimalPlaces = state.currencyModel.fractionUnits,
            isClickable = true,
            onAmountClicked = {
                navigator.push(
                    ScreenRegistry.get(
                        NavScreenProvider.HomeScreen.CurrencySelection(
                            kind = CurrencySelectionKind.Entry
                        )
                    )
                )
            },
            isError = state.isError,
            onNumberPressed = { dispatchEvent(PoolCreateViewModel.Event.OnNumberPressed(it)) },
            onBackspace = { dispatchEvent(PoolCreateViewModel.Event.OnBackspace) },
            onDecimal = { dispatchEvent(PoolCreateViewModel.Event.OnDecimalPressed) }
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            CodeButton(
                enabled = state.canSet,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .navigationBarsPadding(),
                buttonState = ButtonState.Filled,
                isLoading = state.confirmingAmount.loading,
                isSuccess = state.confirmingAmount.success,
                text = stringResource(R.string.action_set),
            ) {
                dispatchEvent(PoolCreateViewModel.Event.OnAmountConfirmed)
            }
        }
    }
}