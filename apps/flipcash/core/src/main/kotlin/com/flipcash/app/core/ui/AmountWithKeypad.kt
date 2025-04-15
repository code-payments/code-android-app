package com.flipcash.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
    modifier: Modifier = Modifier,
    amountAnimatedModel: AmountAnimatedInputUiModel,
    prefix: String = "",
    placeholder: String = "",
    currencyFlag: Int? = null,
    allowDecimals: Boolean = true,
    hint: String = "",
    isError: Boolean = false,
    onNumberPressed: (Int) -> Unit,
    onBackspace: () -> Unit,
    onDecimal: () -> Unit = { },
) {
    val networkObserver = LocalNetworkObserver.current
    val networkState by networkObserver.state.collectAsState()

    Column(
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f)
        ) {
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
                isClickable = false,
                networkState = networkState,
                textStyle = CodeTheme.typography.displayLarge,
            )
        }

        CodeKeyPad(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CodeTheme.dimens.inset)
                .weight(1f),
            onNumber = onNumberPressed,
            onClear = onBackspace,
            onDecimal = onDecimal,
            isDecimal = allowDecimals,
        )
    }
}