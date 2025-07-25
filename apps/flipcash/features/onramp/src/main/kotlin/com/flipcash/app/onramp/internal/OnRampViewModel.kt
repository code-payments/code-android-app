package com.flipcash.app.onramp.internal

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.onramp.OnRampAuthError
import com.flipcash.app.onramp.OnRampController
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.financial.toFiat
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
internal class OnRampViewModel @Inject constructor(
    private val ramp: OnRampController,
) : BaseViewModel2<OnRampViewModel.State, OnRampViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {

    data class State(
        val loading: Boolean = false,
        val paymentLink: String? = null,
        val authUrl: String? = null,
    )

    sealed interface Event {
        data object OnPlaceOrder : Event
        data class OnOrderPlaced(val paymentLink: String) : Event
        data class OnPhoneVerificationRequired(val url: String) : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.OnPlaceOrder>()
            .map { ramp.placeOrderExclusiveOfFees(10.toFiat()) }
            .onResult(
                onSuccess = {
                    dispatchEvent(Event.OnOrderPlaced(it.url))
                },
                onError = { error ->
                    when (error) {
                        is OnRampAuthError.EmailVerificationRequired -> {
                            BottomBarManager.showError(
                                title = "Email verification required",
                                message = "Please verify your email address to continue",
                            )
                        }
                        is OnRampAuthError.PhoneVerificationRequired -> {
                            BottomBarManager.showError(
                                title = "Phone verification required",
                                message = "Please verify your phone number to continue",
                            )
                        }
                        is OnRampAuthError.CoinbasePhoneVerificationRequired -> {
                            BottomBarManager.showError(
                                title = "Phone verification required",
                                message = "Please verify your phone number with Coinbase to continue",
                            ) {
                                dispatchEvent(Event.OnPhoneVerificationRequired(error.url))
                            }
                        }
                        else -> {
                            BottomBarManager.showError(
                                title = "Error",
                                message = error.message ?: "Unknown error",
                            )
                        }
                    }
                }
            )
            .launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                Event.OnPlaceOrder -> { state -> state.copy(loading = true) }
                is Event.OnOrderPlaced -> { state -> state.copy(paymentLink = event.paymentLink) }
                is Event.OnPhoneVerificationRequired -> { state -> state.copy(authUrl = event.url) }
            }
        }
    }
}