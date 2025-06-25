package com.flipcash.app.payments

import com.flipcash.app.payments.internal.PaymentMetadata
import com.getcode.opencode.model.core.ID

sealed interface PaymentEvent {
    data class OnPaymentSuccess(
        val intentId: ID,
        val metadata: PaymentMetadata,
        val acknowledge: (Boolean, () -> Unit) -> Unit // Caller returns true if they want to proceed as success, false as error
    ) : PaymentEvent
    data object OnPaymentCancelled : PaymentEvent
    data class OnRpcFailure(val error: Throwable): PaymentEvent
    data class OnPaymentError(val error: Throwable): PaymentEvent
}

sealed interface ConfirmationEvent {
    data object OnConfirmationSuccess : ConfirmationEvent
    data object OnConfirmationCancelled : ConfirmationEvent
}