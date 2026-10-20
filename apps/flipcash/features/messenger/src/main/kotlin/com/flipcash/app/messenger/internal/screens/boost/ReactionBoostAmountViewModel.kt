package com.flipcash.app.messenger.internal.screens.boost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.ui.ConfirmationStyle
import com.flipcash.features.messenger.R
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryLabel
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * The keypad behind the Paid Reactions sheet's "…" chip. Entry is in US dollars whatever the
 * viewer's preferred currency, since the boost is a USD figure end to end, and the result is whole
 * cents.
 */
@HiltViewModel
internal class ReactionBoostAmountViewModel @Inject constructor(
    exchange: Exchange,
    resources: ResourceHelper,
) : ViewModel() {

    val amountDelegate = AmountEntryDelegate(
        exchange = exchange,
        scope = viewModelScope,
        style = AmountEntryStyle(
            actionLabel = AmountEntryLabel.Plain(resources.getString(R.string.action_done)),
            actionStyle = ConfirmationStyle.Button,
            canChangeCurrency = false,
        ),
    )

    init {
        exchange.getCurrency(CurrencyCode.USD.name)?.let(amountDelegate::onCurrencyChanged)
    }

    /** The entry in whole cents, or null when nothing above zero was entered. */
    fun enteredCents(): Long? = dollarsToCents(amountDelegate.state.value.enteredAmount)

    internal companion object {
        fun dollarsToCents(dollars: Double): Long? =
            BigDecimal.valueOf(dollars)
                .setScale(2, RoundingMode.HALF_EVEN)
                .movePointRight(2)
                .longValueExact()
                .takeIf { it > 0 }
    }
}
