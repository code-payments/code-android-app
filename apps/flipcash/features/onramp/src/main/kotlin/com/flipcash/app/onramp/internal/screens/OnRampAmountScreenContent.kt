package com.flipcash.app.onramp.internal.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.money.CurrencySelectionKind
import com.flipcash.app.core.ui.AmountWithKeypad
import com.flipcash.app.onramp.internal.AmountEntryState
import com.flipcash.app.onramp.internal.OnRampViewModel
import com.flipcash.features.onramp.R
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun OnRampAmountScreen(
    viewModel: OnRampViewModel,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    OnRampAmountScreenContent(
        state = state.amountEntryState,
        providerType = (state.selectedProvider as? OnRampProvider.Coinbase)?.type,
        dispatchEvent = viewModel::dispatchEvent,
    )
}

@Composable
private fun OnRampAmountScreenContent(
    state: AmountEntryState,
    providerType: OnRampType?,
    dispatchEvent: (OnRampViewModel.Event) -> Unit,
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
                stringResource(R.string.subtitle_onrampPurchaseExceeded, state.maxAvailableToAdd)
            } else {
                stringResource(R.string.subtitle_onrampPurchaseHint, state.maxAvailableToAdd)
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
            onNumberPressed = { dispatchEvent(OnRampViewModel.Event.OnNumberPressed(it)) },
            onBackspace = { dispatchEvent(OnRampViewModel.Event.OnBackspace) },
            onDecimal = { dispatchEvent(OnRampViewModel.Event.OnDecimalPressed) }
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            CodeButton(
                enabled = state.canAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .navigationBarsPadding(),
                buttonState = ButtonState.Filled,
                isLoading = state.confirmingAmount.loading,
                isSuccess = state.confirmingAmount.success,
                text = when (providerType) {
                    OnRampType.Virtual ->  "Add Cash with Google Pay"
                    OnRampType.PhysicalDebit -> "Add Cash with a Debit Card"
                    OnRampType.PhysicalCredit -> "Add Cash with a Credit Card"
                    null -> "Add Cash"
                },
            ) {
                dispatchEvent(OnRampViewModel.Event.OnAmountConfirmed)
            }
        }
    }
}