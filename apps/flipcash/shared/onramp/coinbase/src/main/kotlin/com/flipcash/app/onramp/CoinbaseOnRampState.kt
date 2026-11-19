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
    /** [amount] is null for a collection's top-up, which reports on [CoinbaseOnRampOutcome] only. */
    data class Paying(val order: OnrampOrder, val token: Token, val amount: VerifiedFiat?, val swapId: SwapId? = null) : CoinbaseOnRampState
    data class Completed(val swapId: SwapId?, val token: Token, val amount: VerifiedFiat?, val orderId: String) : CoinbaseOnRampState
    data class Failed(val error: CoinbaseOnRampWebError) : CoinbaseOnRampState
}

/**
 * Coinbase's progress through a payment, for timing it. Unlike [CoinbaseOnRampState] these are
 * not conflated, so a collector sees each one even when the state moves on straight after.
 */
enum class CoinbaseOnRampMilestone {
    /** Coinbase's `onramp_api.payment_authorized` webview event. */
    PaymentAuthorized,

    /** Coinbase's `onramp_api.polling_success` webview event. */
    PaymentCompleted,
}

/**
 * How the payment webview ended for an order. Unlike [CoinbaseOnRampState] these are not
 * conflated, and each names its order, so a caller that subscribed before opening the webview
 * sees its own end even when the state is reset straight after.
 */
sealed interface CoinbaseOnRampOutcome {
    val orderId: String

    /** The user authorized the payment. Coinbase may still settle the order whatever follows. */
    data class Authorized(override val orderId: String) : CoinbaseOnRampOutcome

    /** Coinbase reported the payment complete. The asset lands on-chain later. */
    data class Succeeded(override val orderId: String) : CoinbaseOnRampOutcome

    /** The webview reported a failure. It may come after authorization, so the order can still settle. */
    data class Failed(override val orderId: String) : CoinbaseOnRampOutcome

    /** The webview was closed before Coinbase reported an end. */
    data class Cancelled(override val orderId: String) : CoinbaseOnRampOutcome
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
