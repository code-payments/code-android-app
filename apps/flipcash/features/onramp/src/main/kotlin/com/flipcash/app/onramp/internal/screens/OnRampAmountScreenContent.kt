package com.flipcash.app.onramp.internal.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.AppRoute
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
import com.getcode.ui.theme.getButtonColors
import kotlin.collections.emptyMap
import kotlin.collections.mapOf
import kotlin.to

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
                    ScreenRegistry.get(
                        AppRoute.Main.CurrencySelection(
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
    val buttonColors = getButtonColors(state.canAdd, ButtonState.Filled, Color.Unspecified)
    val (buttonText, assets) = when (provider) {
        is OnRampProvider.Coinbase -> when (provider.type) {
            // https://developers.google.com/pay/api/android/guides/brand-guidelines#using-pay-in-text
            OnRampType.Virtual -> AnnotatedString(stringResource(R.string.action_addCashWithGooglePay)) to emptyMap()
            OnRampType.PhysicalDebit -> AnnotatedString(stringResource(R.string.action_addCashWithDebitCard)) to emptyMap()
            OnRampType.PhysicalCredit -> AnnotatedString(stringResource(R.string.action_addCashWithCreditCard)) to emptyMap()
        }

        OnRampProvider.Phantom -> buildAnnotatedString {
            append(stringResource(R.string.label_confirmIn))
            appendInlineContent("[icon]", alternateText = " ")
            append(stringResource(R.string.label_phantom))
        } to mapOf(
            "[icon]" to InlineTextContent(
                placeholder = Placeholder(
                    width = 25.sp,
                    height = 14.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                ),
                children = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            modifier = Modifier.padding(
                                start = CodeTheme.dimens.staticGrid.x1 + 2.dp,
                                end = CodeTheme.dimens.staticGrid.x1
                            ),
                            painter = painterResource(R.drawable.ic_phantom),
                            colorFilter = ColorFilter.tint(buttonColors.contentColor(state.canAdd).value),
                            contentDescription = null
                        )
                    }
                }
            )
        )

        null -> AnnotatedString(stringResource(R.string.action_addCash)) to emptyMap<String, InlineTextContent>()
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