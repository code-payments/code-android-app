package com.flipcash.app.onramp.internal.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.money.RegionSelectionKind
import com.flipcash.app.core.onramp.ui.buildExternalWalletButtonLabel
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
        provider = state.selectedProvider,
        dispatchEvent = viewModel::dispatchEvent,
    )
}

@Composable
private fun OnRampAmountScreenContent(
    state: AmountEntryState,
    provider: OnRampProvider.ThirdParty?,
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
            isClickable = provider !is OnRampProvider.Phantom,
            onAmountClicked = {
                navigator.push(
                    AppRoute.Main.RegionSelection(
                        kind = RegionSelectionKind.Entry
                    )
                )
            },
            isError = state.isError,
            onNumberPressed = { dispatchEvent(OnRampViewModel.Event.OnNumberPressed(it)) },
            onBackspace = { dispatchEvent(OnRampViewModel.Event.OnBackspace) },
            onDecimal = { dispatchEvent(OnRampViewModel.Event.OnDecimalPressed) }
        )

        ConfirmationButton(
            modifier = Modifier
                .fillMaxWidth(),
            state = state,
            provider = provider,
            dispatchEvent = dispatchEvent
        )
    }
}

@Composable
private fun ConfirmationButton(
    state: AmountEntryState,
    provider: OnRampProvider.ThirdParty?,
    modifier: Modifier = Modifier,
    dispatchEvent: (OnRampViewModel.Event) -> Unit
) {
    val (buttonText, assets) = when (provider) {
        is OnRampProvider.Coinbase -> AnnotatedString(stringResource(R.string.action_buy)) to emptyMap()
        is OnRampProvider.UsesDeeplinks -> {
            buildExternalWalletButtonLabel(
                prefix = stringResource(R.string.label_confirmIn),
                provider = provider,
                isEnabled = state.canAdd
            )
        }

        null -> AnnotatedString(stringResource(R.string.action_addCash)) to emptyMap()
    }
    CodeButton(
        enabled = state.canAdd,
        modifier = modifier
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(bottom = CodeTheme.dimens.grid.x2)
            .navigationBarsPadding(),
        buttonState = ButtonState.Filled,
        isLoading = state.confirmingAmount.loading,
        isSuccess = state.confirmingAmount.success,
        text = buttonText,
        inlineContent = assets,
    ) {
        dispatchEvent(OnRampViewModel.Event.OnAmountConfirmed)
    }
}
