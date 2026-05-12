package com.flipcash.app.tokens.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.money.RegionSelectionKind
import com.flipcash.app.core.onramp.ui.buildPhantomButtonLabel
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.ui.AmountWithKeypad
import com.flipcash.app.tokens.ui.SwapViewModel
import com.flipcash.features.tokens.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun SwapEntryScreenContent(
    viewModel: SwapViewModel,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    SwapEntryScreenContent(state, viewModel::dispatchEvent)
}

@Composable
internal fun SwapEntryScreenContent(
    state: SwapViewModel.State,
    dispatchEvent: (SwapViewModel.Event) -> Unit,
) {
    val navigator = LocalCodeNavigator.current

    val entryState = remember(state.amountEntryState) {
        state.amountEntryState
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AmountWithKeypad(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            amountAnimatedModel = entryState.amountAnimatedModel,
            currencyFlag = entryState.currencyModel.selected?.resId,
            prefix = entryState.currencyModel.selected?.symbol.orEmpty(),
            placeholder = "0",
            hint = if (state.isBelowMinimum) {
                stringResource(
                    R.string.subtitle_buyHintBelowMinimum,
                    state.minimumBuyAmount?.formatted().orEmpty()
                )
            } else if (state.isError) {
                when (state.purpose) {
                    is SwapPurpose.BalanceIncrease -> stringResource(
                        R.string.subtitle_buyHintLimitExceeded,
                        state.maxAvailableToSwap
                    )

                    is SwapPurpose.BalanceDecrease -> stringResource(
                        R.string.subtitle_sellHintLimitExceeded,
                        state.maxAvailableToSwap
                    )

                    else -> ""
                }
            } else {
                stringResource(
                    R.string.subtitle_buySellCashHint,
                    state.maxAvailableToSwap
                )
            },
            decimalPlaces = entryState.currencyModel.fractionUnits,
            isClickable = state.purpose !is SwapPurpose.FundWithWallet,
            onAmountClicked = {
                navigator.push(
                    AppRoute.Main.RegionSelection(
                        kind = RegionSelectionKind.Entry
                    )
                )
            },
            isError = state.isError,
            onNumberPressed = {
                dispatchEvent(
                    SwapViewModel.Event.OnNumberPressed(
                        it
                    )
                )
            },
            onBackspace = { dispatchEvent(SwapViewModel.Event.OnBackspace) },
            onDecimal = { dispatchEvent(SwapViewModel.Event.OnDecimalPressed) }
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            val (text, inlineContent) = when (state.purpose) {
                is SwapPurpose.Buy -> AnnotatedString(stringResource(R.string.action_buy)) to emptyMap()
                is SwapPurpose.FundWithWallet -> buildPhantomButtonLabel(
                    prefix = stringResource(R.string.label_confirmIn),
                    isEnabled = state.canTransact
                )
                is SwapPurpose.Sell -> AnnotatedString(stringResource(R.string.action_next)) to emptyMap()
                else -> AnnotatedString("") to emptyMap()
            }

            CodeButton(
                enabled = state.canTransact,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .navigationBarsPadding(),
                buttonState = ButtonState.Filled,
                isLoading = state.buyProgress.loading,
                isSuccess = state.buyProgress.success,
                text = text,
                inlineContent = inlineContent,
            ) {
                dispatchEvent(SwapViewModel.Event.OnAmountConfirmed)
            }
        }
    }
}