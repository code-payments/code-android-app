package com.flipcash.app.tipping.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.tipping.TipAmount
import com.flipcash.app.core.ui.ConfirmationStyle
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryLabel
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.flipcash.shared.tipping.TippingCoordinator
import com.flipcash.features.tipping.R
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.financial.Fiat
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Backs the custom tip-amount entry sheet. Hosts an [AmountEntryDelegate] keypad (in the user's
 * preferred currency, mirroring the tip presets) and commits the entered value to the shared tip
 * selection on [TippingCoordinator], so the still-visible tip modal reflects it once the sheet is
 * dismissed.
 */
@HiltViewModel
internal class TipAmountEntryViewModel @Inject constructor(
    exchange: Exchange,
    resources: ResourceHelper,
    private val tippingCoordinator: TippingCoordinator,
) : BaseViewModel<TipAmountEntryViewModel.State, TipAmountEntryViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {

    data class State(
        /** Currency the keypad is entering in — kept in sync with the preferred rate. */
        val currency: CurrencyCode = CurrencyCode.USD,
    )

    sealed interface Event {
        /** Preferred currency resolved/changed; keeps the entry currency in sync. */
        data class CurrencyChanged(val currency: CurrencyCode) : Event

        /** User asked to confirm the currently entered amount. */
        data object ConfirmRequested : Event

        /** The entered amount was committed to the tip selection — the screen should dismiss. */
        data object Confirmed : Event
    }

    val amountDelegate = AmountEntryDelegate(
        exchange = exchange,
        scope = viewModelScope,
        style = AmountEntryStyle(
            actionLabel = AmountEntryLabel.Plain(resources.getString(R.string.action_next)),
            actionStyle = ConfirmationStyle.Button,
            infoHint = { resources.getString(R.string.subtitle_sendHint, it) },
            overMaxHint = { resources.getString(R.string.subtitle_sendHintLimitExceeded, it) },
            belowMinHint = { resources.getString(R.string.subtitle_tipHintMinimum, it) },
        ),
        // Cap entry at the selected token's min(send limit, balance) — the "enter up to" hint —
        // and floor it at the lowest preset so a custom amount can't undercut the presets.
        maxAmount = tippingCoordinator.maxTipAmount,
        minimumAmount = tippingCoordinator.minTipAmount,
    )

    init {
        exchange.observePreferredRate()
            .onEach { rate ->
                exchange.getCurrency(rate.currency.name)?.let { amountDelegate.onCurrencyChanged(it) }
                dispatchEvent(Event.CurrencyChanged(rate.currency))
            }
            .launchIn(viewModelScope)

        // Prefill only when the current selection is itself a custom amount, so re-opening the sheet
        // keeps a custom value — but opening it with a preset picked starts blank.
        (tippingCoordinator.selection.value.amount as? TipAmount.Custom)?.let { custom ->
            amountDelegate.prefill(custom.value.decimalValue)
        }

        // Commit the entered amount on confirm, then signal the screen to dismiss.
        eventFlow
            .filterIsInstance<Event.ConfirmRequested>()
            .onEach {
                val entered = amountDelegate.state.value.enteredAmount
                if (entered <= 0.0) return@onEach
                val amount = Fiat(entered, stateFlow.value.currency)
                // Floor at the lowest preset — a custom amount can't undercut the presets.
                val min = tippingCoordinator.minTipAmount.value
                if (min != null && amount.valueLessThan(min)) {
                    BottomBarManager.showAlert(
                        title = resources.getString(R.string.error_title_tipMinimum, min.formatted()),
                        message = resources.getString(R.string.error_description_tipMinimum),
                    )
                    return@onEach
                }
                // Over the send limit → block with the limit message.
                if (tippingCoordinator.exceedsSendLimit(amount)) {
                    BottomBarManager.showAlert(
                        title = resources.getString(R.string.error_title_sendLimitReached),
                        message = resources.getString(R.string.error_description_sendLimitReached),
                    )
                    return@onEach
                }
                // Over the max (min(limit, balance)) but within the limit → over balance. Prompt to
                // add money instead of committing, mirroring the send-time gate in confirmTip.
                val max = tippingCoordinator.maxTipAmount.value
                if (max != null && amount.valueGreaterThan(max)) {
                    tippingCoordinator.promptInsufficientBalance()
                    return@onEach
                }
                tippingCoordinator.selectAmount(TipAmount.Custom(amount))
                dispatchEvent(Event.Confirmed)
            }
            .launchIn(viewModelScope)
    }

    companion object {
        private val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.CurrencyChanged -> { state -> state.copy(currency = event.currency) }
                is Event.ConfirmRequested -> { state -> state }
                is Event.Confirmed -> { state -> state }
            }
        }
    }
}
