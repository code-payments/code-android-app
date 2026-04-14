package com.flipcash.app.onramp

import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@ActivityRetainedScoped
class CoinbaseOnRampManager @Inject constructor() {

    private val _state = MutableStateFlow<CoinbaseOnRampState>(CoinbaseOnRampState.Idle)
    val state: StateFlow<CoinbaseOnRampState> = _state.asStateFlow()

    fun startPayment(order: OnrampOrder, token: Token, amount: LocalFiat) {
        _state.value = CoinbaseOnRampState.Paying(order, token, amount)
    }

    fun onPaymentSuccess(orderId: String) {
        val current = _state.value
        if (current is CoinbaseOnRampState.Paying) {
            _state.update {
                CoinbaseOnRampState.Processing(orderId, current.token, current.amount)
            }
        }
    }

    fun onPaymentFailure(error: CoinbaseOnRampWebError) {
        _state.update { CoinbaseOnRampState.Failed(error) }
    }

    fun onPaymentCancel() {
        _state.update { CoinbaseOnRampState.Idle }
    }

    fun onCompleted(swapId: SwapId) {
        _state.update { CoinbaseOnRampState.Completed(swapId) }
    }

    fun reset() {
        _state.update { CoinbaseOnRampState.Idle }
    }
}
