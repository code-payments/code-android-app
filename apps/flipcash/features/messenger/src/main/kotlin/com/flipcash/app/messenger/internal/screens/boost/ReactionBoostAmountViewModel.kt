package com.flipcash.app.messenger.internal.screens.boost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.ui.ConfirmationStyle
import com.flipcash.features.messenger.R
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryLabel
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val splitResolver: ReactionBoostSplitResolver,
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

    private val _split = MutableStateFlow<ReactionBoostViewModel.SplitCopy?>(null)

    /** Who the boost pays, for the keypad's hint; null until the message's author and chat are known. */
    val split: StateFlow<ReactionBoostViewModel.SplitCopy?> = _split.asStateFlow()

    /** Resolves the split for the message the keypad was opened from. */
    suspend fun load(chatId: ChatId, messageId: Long) {
        _split.value = splitResolver.resolve(chatId, messageId)
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
