package com.flipcash.app.payments

import com.flipcash.app.core.bill.PaymentMetadata

sealed interface PaymentEvent {
    data class OnPaymentSuccess(
        val metadata: PaymentMetadata,
        val acknowledge: (Boolean, () -> Unit) -> Unit // Caller returns true if they want to proceed as success, false as error
    ) : PaymentEvent
    data object OnPaymentCancelled : PaymentEvent
    data class OnPaymentError(val error: Throwable): PaymentEvent
}