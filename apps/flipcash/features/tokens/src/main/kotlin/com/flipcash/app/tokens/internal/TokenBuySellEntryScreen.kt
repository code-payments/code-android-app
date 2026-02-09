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
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.money.RegionSelectionKind
import com.flipcash.app.core.onramp.ui.buildPhantomButtonLabel
import com.flipcash.app.core.tokens.TokenSwapPurpose
import com.flipcash.app.core.ui.AmountWithKeypad
import com.flipcash.app.tokens.BuySellSwapTokenViewModel
import com.flipcash.features.tokens.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
internal fun BuySellTokenEntryScreen(
    viewModel: BuySellSwapTokenViewModel,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    BuySellTokenEntryScreen(state, viewModel::dispatchEvent)
}

@Composable
internal fun BuySellTokenEntryScreen(
    state: BuySellSwapTokenViewModel.State,
    dispatchEvent: (BuySellSwapTokenViewModel.Event) -> Unit,
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
            hint = if (state.isError) {
                when (state.purpose) {
                    is TokenSwapPurpose.Buy -> stringResource(
                        R.string.subtitle_buyHintLimitExceeded,
                        state.maxAvailableToSwap
                    )

                    is TokenSwapPurpose.Sell -> stringResource(
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
            isClickable = state.purpose !is TokenSwapPurpose.FundWithWallet,
            onAmountClicked = {
                navigator.push(
                    ScreenRegistry.get(
                        AppRoute.Main.RegionSelection(
                            kind = RegionSelectionKind.Entry
                        )
                    )
                )
            },
            isError = state.isError,
            onNumberPressed = {
                dispatchEvent(
                    BuySellSwapTokenViewModel.Event.OnNumberPressed(
                        it
                    )
                )
            },
            onBackspace = { dispatchEvent(BuySellSwapTokenViewModel.Event.OnBackspace) },
            onDecimal = { dispatchEvent(BuySellSwapTokenViewModel.Event.OnDecimalPressed) }
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            val (text, inlineContent) = when (state.purpose) {
                is TokenSwapPurpose.Buy -> AnnotatedString(stringResource(R.string.action_buy)) to emptyMap()
                is TokenSwapPurpose.FundWithWallet -> buildPhantomButtonLabel(
                    prefix = stringResource(R.string.label_confirmIn),
                    isEnabled = state.canTransact
                )
                is TokenSwapPurpose.Sell -> AnnotatedString(stringResource(R.string.action_next)) to emptyMap()
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
                dispatchEvent(BuySellSwapTokenViewModel.Event.OnAmountConfirmed)
            }
        }
    }
}