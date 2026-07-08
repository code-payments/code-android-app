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
    data class Paying(val order: OnrampOrder, val token: Token, val amount: VerifiedFiat, val swapId: SwapId? = null) : CoinbaseOnRampState
    data class Completed(val swapId: SwapId?, val token: Token, val amount: VerifiedFiat, val orderId: String) : CoinbaseOnRampState
    data class Failed(val error: CoinbaseOnRampWebError) : CoinbaseOnRampState
}

sealed interface CoinbaseOnRampCompletion {
    data class SwapSubmitted(val swapId: SwapId) : CoinbaseOnRampCompletion
    data class DepositSubmitted(val orderId: String) : CoinbaseOnRampCompletion
}

/**
 * Outcome of waiting for a Coinbase order's on-chain crypto delivery. After the
 * payment succeeds the order sits in PROCESSING until Coinbase actually sends the
 * asset on-chain; the send is only confirmed once the order reports a `txHash`.
 */
sealed interface OrderDeliveryResult {
    /** The send was confirmed on-chain (the order reported a transaction hash). */
    data class Delivered(val txHash: String) : OrderDeliveryResult
    /** The order reached a terminal failed/cancelled state. */
    data object Failed : OrderDeliveryResult
    /** Delivery did not confirm within the polling window; it may still land later. */
    data object TimedOut : OrderDeliveryResult
}
