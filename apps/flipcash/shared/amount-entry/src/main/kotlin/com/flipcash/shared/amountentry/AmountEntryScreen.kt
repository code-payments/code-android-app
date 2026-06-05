package com.flipcash.shared.amountentry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.ui.AmountWithKeypad
import com.flipcash.app.core.ui.ConfirmationStyle
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.SlideToConfirm
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@Composable
fun AmountEntryScreen(
    controller: AmountEntryController,
    onConfirm: () -> Unit,
    onChangeCurrency: () -> Unit = {},
    appBar: (@Composable () -> Unit)? = null,
) {
    val delegateState by controller.state.collectAsStateWithLifecycle()
    val config by controller.config.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        appBar?.invoke()

        AmountWithKeypad(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            amountAnimatedModel = delegateState.amountAnimatedModel,
            currencyFlag = delegateState.currency.selected?.resId,
            prefix = delegateState.currency.selected?.symbol.orEmpty(),
            placeholder = "0",
            hint = when (val hint = config.hint) {
                is AmountEntryHint.None -> ""
                is AmountEntryHint.Info -> hint.text
                is AmountEntryHint.Error -> hint.text
            },
            decimalPlaces = delegateState.currency.fractionUnits,
            isClickable = config.canChangeCurrency,
            onAmountClicked = onChangeCurrency,
            isError = config.hint is AmountEntryHint.Error,
            onNumberPressed = { controller.onNumber(it) },
            onBackspace = { controller.onBackspace() },
            onDecimal = { controller.onDecimal() },
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            when (config.action.style) {
                ConfirmationStyle.Button -> {
                    CodeButton(
                        enabled = config.canConfirm && config.action.loadingState.isIdle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(bottom = CodeTheme.dimens.grid.x2)
                            .navigationBarsPadding(),
                        buttonState = ButtonState.Filled,
                        isLoading = config.action.loadingState.loading,
                        isSuccess = config.action.loadingState.success,
                        text = config.action.label,
                    ) {
                        onConfirm()
                    }
                }

                ConfirmationStyle.Slide -> {
                    SlideToConfirm(
                        onConfirm = onConfirm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(bottom = CodeTheme.dimens.grid.x2)
                            .navigationBarsPadding(),
                        enabled = config.canConfirm && config.action.loadingState.isIdle,
                        isLoading = config.action.loadingState.loading,
                        isSuccess = config.action.loadingState.success,
                        label = config.action.label,
                    )
                }
            }
        }
    }
}
