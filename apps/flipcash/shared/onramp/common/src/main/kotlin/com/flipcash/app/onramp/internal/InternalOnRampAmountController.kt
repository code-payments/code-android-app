package com.flipcash.app.onramp.internal

import com.flipcash.app.analytics.AnalyticsEvent
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.onramp.ConfirmationEvent
import com.flipcash.app.onramp.OnRampAmount
import com.flipcash.app.onramp.OnRampAmountController
import com.flipcash.app.onramp.State
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class InternalOnRampAmountController(
    private val analytics: FlipcashAnalyticsService,
) : OnRampAmountController {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state: MutableStateFlow<State> = MutableStateFlow(State())
    override val state: StateFlow<State>
        get() = _state.asStateFlow()

    private val _confirmationEvents: MutableSharedFlow<ConfirmationEvent> = MutableSharedFlow()
    override val confirmationEvents: SharedFlow<ConfirmationEvent> =
        _confirmationEvents.asSharedFlow()

    override fun requestAmountSelection(provider: OnRampProvider) {
        _state.update { it.copy(provider = provider, selectedAmount = null) }
    }

    override fun selectAmount(amount: OnRampAmount?) {
        val currentSelection = _state.value.selectedAmount
        _state.update {
            when (currentSelection) {
                is OnRampAmount.Predefined -> {
                    if (amount is OnRampAmount.Predefined) {
                        if (amount.fiat == currentSelection.fiat) {
                            it.copy(selectedAmount = null)
                        } else {
                            analytics.onrampPurchase(
                                purchaseEvent = AnalyticsEvent.OnRampPurchaseEvent.PresetSelected,
                                fiat = amount.fiat
                            )

                            it.copy(selectedAmount = amount)
                        }
                    } else {
                        it.copy(selectedAmount = amount)
                    }
                }
                is OnRampAmount.Custom -> {
                    if (amount is OnRampAmount.Custom) {
                        it.copy(selectedAmount = null)
                    } else {
                        analytics.onrampPurchase(
                            purchaseEvent = AnalyticsEvent.OnRampPurchaseEvent.EnterCustomAmount,
                        )

                        it.copy(selectedAmount = amount)
                    }
                }
                else -> {
                    it.copy(selectedAmount = amount)
                }
            }
        }
    }

    override fun confirmSelection() {
        val provider = _state.value.provider ?: return
        val selectedAmount = _state.value.selectedAmount ?: return
        scope.launch {
            cancel(false)
            delay(300)
            _confirmationEvents.emit(
                ConfirmationEvent.OnConfirmationSuccess(
                    provider,
                    selectedAmount
                )
            )
        }
    }

    override fun cancel(fromUser: Boolean) {
        _state.update { State() }
        if (fromUser) {
            scope.launch {
                _confirmationEvents.emit(ConfirmationEvent.Cancelled)
            }
        }

    }
}