package com.flipcash.app.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.ui.components.text.AmountArea
import com.getcode.ui.theme.CodeKeyPad
import com.getcode.utils.network.LocalNetworkObserver

/**
 * Amount entry: an amount field stacked over a keypad, with an optional row between the two.
 *
 * The upper region and the strip above the keypad are slots, so a flow supplies whatever header
 * it needs rather than this component collecting a flag per variant. [AmountEntryField] is the
 * default header; [LargeAmountField] is the v2 (Get/Convert) one.
 */
@Composable
fun AmountWithKeypad(
    onNumberPressed: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    decimalPlaces: Int = 2,
    onDecimal: () -> Unit = { },
    /** Optional row rendered between the amount and the keypad (e.g. a Convert destination picker). */
    accessory: (@Composable () -> Unit)? = null,
    amountField: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f),
            content = amountField,
        )

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

/**
 * The default amount field: centred in its region at display-large, with an optional currency
 * flag that doubles as the currency picker affordance.
 */
@Composable
fun BoxScope.AmountEntryField(
    amountAnimatedModel: AmountAnimatedInputUiModel,
    prefix: String = "",
    placeholder: String = "",
    currencyFlag: Int? = null,
    decimalPlaces: Int = 2,
    hint: String = "",
    isError: Boolean = false,
    isClickable: Boolean = false,
    onClick: () -> Unit = { },
) {
    val networkObserver = LocalNetworkObserver.current
    val networkState by networkObserver.state.collectAsStateWithLifecycle()

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
        onClick = onClick,
        networkState = networkState,
        textStyle = CodeTheme.typography.displayLarge,
        decimalPlaces = decimalPlaces,
    )
}

/**
 * v2 amount field: top-anchored and left-aligned at display-extra-large over an "$X available"
 * line. Carries no currency flag or chevron — the currency is fixed for the flows that use it.
 */
@Composable
fun LargeAmountField(
    amountAnimatedModel: AmountAnimatedInputUiModel,
    modifier: Modifier = Modifier,
    prefix: String = "",
    placeholder: String = "",
    decimalPlaces: Int = 2,
    hint: String = "",
    isError: Boolean = false,
) {
    val networkObserver = LocalNetworkObserver.current
    val networkState by networkObserver.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        // The gap below the app bar, so the amount isn't jammed against the chrome — AmountArea's
        // own row contributes 8dp on top of this. height() rather than heightIn(max=): the latter
        // only lifts the max constraint, and a bare Spacer has no intrinsic size, so it measures to
        // the minimum and collapses to nothing. height() fixes both bounds, and still coerces into
        // the incoming constraints — so on a screen too short to grant the weighted share it gives
        // way rather than squeezing the 74sp digits.
        Spacer(
            modifier = Modifier
                .weight(1f, fill = false)
                .height(CodeTheme.dimens.grid.x8)
        )
        AmountArea(
            modifier = Modifier
                .fillMaxWidth()
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
                CodeTheme.colors.textPlaceholder
            } else {
                CodeTheme.colors.textMain
            },
            decimalPlaces = decimalPlaces,
        )
    }
}
