package com.flipcash.app.onramp

import android.os.Parcelable
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.exchange.VerifiedFiat

import com.getcode.opencode.model.financial.Token
import kotlinx.parcelize.Parcelize

@Parcelize
data class OnrampOrder(
    val orderId: String,
    val paymentLink: String,
) : Parcelable

sealed interface CoinbaseOnRampState {
    data object Idle : CoinbaseOnRampState
    data class Paying(val order: OnrampOrder, val token: Token, val amount: VerifiedFiat) : CoinbaseOnRampState
    data class Processing(val orderId: String, val token: Token, val amount: VerifiedFiat) : CoinbaseOnRampState
    data class Completed(val swapId: SwapId) : CoinbaseOnRampState
    data class Failed(val error: CoinbaseOnRampWebError) : CoinbaseOnRampState
}
