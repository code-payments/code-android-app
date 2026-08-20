package com.flipcash.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.ui.components.text.AmountArea
import com.getcode.ui.theme.CodeKeyPad
import com.getcode.utils.network.LocalNetworkObserver

@Composable
fun AmountWithKeypad(
    amountAnimatedModel: AmountAnimatedInputUiModel,
    modifier: Modifier = Modifier,
    prefix: String = "",
    placeholder: String = "",
    currencyFlag: Int? = null,
    decimalPlaces: Int = 2,
    hint: String = "",
    isError: Boolean = false,
    isClickable: Boolean = false,
    /**
     * v2 layout: instead of the centred amount with an "Enter up to" caption, the amount is
     * top-anchored and left-aligned at display-extra-large, over an "$X available" line.
     */
    largeHeader: Boolean = false,
    /** Optional row rendered between the amount and the keypad (e.g. a Convert destination picker). */
    accessory: (@Composable () -> Unit)? = null,
    onNumberPressed: (Int) -> Unit,
    onAmountClicked: () -> Unit = { },
    onBackspace: () -> Unit,
    onDecimal: () -> Unit = { },
) {
    val networkObserver = LocalNetworkObserver.current
    val networkState by networkObserver.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f)
        ) {
            if (largeHeader) {
                // The header carries no currency flag or chevron — currency is fixed for the flow.
                AmountArea(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        // 24dp above the amount, per spec — AmountArea's own row contributes 8dp.
                        .padding(top = CodeTheme.dimens.grid.x4)
                        .padding(horizontal = CodeTheme.dimens.inset),
                    amountPrefix = prefix,
                    amountText = "",
                    placeholder = placeholder,
                    captionText = hint,
                    currencyResId = null,
                    isAltCaptionKinIcon = false,
                    isAltCaption = isError,
                    uiModel = amountAnimatedModel,
                    isAnimated = true,
                    isClickable = false,
                    networkState = networkState,
                    textStyle = CodeTheme.typography.displayExtraLarge,
                    horizontalAlignment = Alignment.Start,
                    contentColor = if (amountAnimatedModel.amountData.amount == 0.0) {
                        CodeTheme.colors.textTertiary
                    } else {
                        CodeTheme.colors.textMain
                    },
                    decimalPlaces = decimalPlaces,
                )
            } else {
                AmountArea(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = CodeTheme.dimens.inset),
                    amountPrefix = prefix,
                    amountText = "",
                    placeholder = placeholder,
                    captionText = hint,
                    currencyResId = currencyFlag,
                    isAltCaptionKinIcon = false,
                    isAltCaption = isError,
                    uiModel = amountAnimatedModel,
                    isAnimated = true,
                    isClickable = isClickable,
                    onClick = { onAmountClicked.invoke() },
                    networkState = networkState,
                    textStyle = CodeTheme.typography.displayLarge,
                    decimalPlaces = decimalPlaces
                )
            }
        }

        accessory?.let {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x3),
            ) { it() }
        }

        CodeKeyPad(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CodeTheme.dimens.inset)
                .weight(1f),
            onNumber = onNumberPressed,
            onClear = onBackspace,
            onDecimal = onDecimal,
            isDecimal = decimalPlaces > 0,
        )
    }
}
