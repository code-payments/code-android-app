package com.flipcash.app.onramp

import androidx.compose.runtime.staticCompositionLocalOf
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.opencode.model.financial.Fiat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface OnRampAmountController {
    val state: StateFlow<State>
    val confirmationEvents: SharedFlow<ConfirmationEvent>
    fun requestAmountSelection(provider: OnRampProvider)
    fun selectAmount(amount: OnRampAmount?)
    fun confirmSelection()
    fun cancel(fromUser: Boolean)
}

sealed interface OnRampAmount {
    data object Custom : OnRampAmount
    data class Predefined(val fiat: Fiat) : OnRampAmount
}

sealed interface ConfirmationEvent {
    data class OnConfirmationSuccess(val provider: OnRampProvider, val amount: OnRampAmount) :
        ConfirmationEvent

    data object Cancelled : ConfirmationEvent
}

data class State(
    val provider: OnRampProvider? = null,
    val selectedAmount: OnRampAmount? = null,
)

internal object StubOnRampAmountController : OnRampAmountController {
    override val state: StateFlow<State> = MutableStateFlow(State())
    override val confirmationEvents: SharedFlow<ConfirmationEvent> = MutableSharedFlow()

    override fun requestAmountSelection(provider: OnRampProvider) = Unit
    override fun selectAmount(amount: OnRampAmount?) = Unit
    override fun confirmSelection() = Unit
    override fun cancel(fromUser: Boolean) = Unit
}

val LocalOnRampAmountController =
    staticCompositionLocalOf<OnRampAmountController> { StubOnRampAmountController }
