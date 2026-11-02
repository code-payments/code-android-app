package com.flipcash.shared.chat.ui

import androidx.compose.runtime.Composable
import com.flipcash.app.core.ui.inDisplayCurrency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat

/** [cents] of USD, the unit a boost travels and is stored in. */
private fun usdCents(cents: Long) = Fiat(quarks = cents * (Fiat.MULTIPLIER / 100).toLong(), currencyCode = CurrencyCode.USD)

/**
 * A boost of [cents] (USD on the wire and in storage) shown in the viewer's own currency, the only
 * one the app displays. [wholeDollars] drops the decimals of a round figure for a preset chip.
 */
@Composable
fun boostCentsText(cents: Long, wholeDollars: Boolean = false): String {
    val shown = usdCents(cents).inDisplayCurrency()
    return if (wholeDollars) shown.formatted(Fiat.FormattingRule.Truncated) else shown.formatted()
}

/** The symbol the "$" chip leads with: the viewer's currency, or "$" when it has no one-character mark. */
@Composable
internal fun boostChipSymbol(): String =
    usdCents(100).inDisplayCurrency().currencyCode.singleCharacterCurrencySymbol ?: "$"
