package com.flipcash.app.payments

import com.flipcash.app.core.bill.PaymentMetadata
import com.getcode.opencode.model.core.ID

sealed interface PaymentEvent {
    data class OnPaymentSuccess(
        val intentId: ID,
        val metadata: PaymentMetadata,
        val acknowledge: (Boolean, () -> Unit) -> Unit // Caller returns true if they want to proceed as success, false as error
    ) : PaymentEvent
    data object OnPaymentCancelled : PaymentEvent
    data class OnPaymentError(val error: Throwable): PaymentEvent
}