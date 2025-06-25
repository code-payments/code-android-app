package com.flipcash.app.payments

import com.flipcash.app.payments.internal.PaymentMetadata
import com.flipcash.app.payments.internal.PoolResolutionPaymentMetadata
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey

data class PaymentState(
    val request: PaymentRequest?,
    val poolBidConfirmation: PublicPaymentConfirmation?,
    val poolResolutionConfirmation: PoolResolutionConfirmation?,
) {
    companion object {
        val Default = PaymentState(
            request = null,
            poolBidConfirmation = null,
            poolResolutionConfirmation = null,
        )
    }
}

sealed class Confirmation(
    open val showScrim: Boolean = false,
    open val state: ConfirmationState,
    open val cancellable: Boolean = false,
)


data class PublicPaymentConfirmation(
    override val state: ConfirmationState,
    val amount: Fiat,
    val destination: PublicKey,
    val metadata: PaymentMetadata,
    override val showScrim: Boolean = true,
    override val cancellable: Boolean = true,
): Confirmation(showScrim, state)

data class PoolResolutionConfirmation(
    override val state: ConfirmationState,
    val poolId: ID,
    val metadata: PoolResolutionPaymentMetadata,
    override val showScrim: Boolean = true,
    override val cancellable: Boolean = true,
): Confirmation(showScrim, state)

sealed interface ConfirmationState {
    data object AwaitingConfirmation : ConfirmationState
    data object Sending : ConfirmationState
    data object Sent : ConfirmationState
}