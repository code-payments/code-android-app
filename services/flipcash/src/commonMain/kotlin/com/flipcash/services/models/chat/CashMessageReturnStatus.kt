package com.flipcash.services.models.chat

import com.flipcash.services.models.paymentrequest.PaymentIntentId

/**
 * Whether the payment of a DM cash message can still be returned to its sender
 * (`GetCashMessageReturnStatus`).
 *
 * @param paymentIntentId The intent id the return payment must be submitted under. Set only for
 * the message's single receiver, and kept private to that one payment (see [PaymentIntentId]).
 */
data class CashMessageReturnStatus(
    val status: Status,
    val paymentIntentId: PaymentIntentId?,
) {
    enum class Status {
        /** The server did not say, or said something this build does not know. */
        UNKNOWN,

        /** The payment has not been returned yet. */
        AVAILABLE,

        /** Terminal: the payment was returned, so there is no reason to ask again. */
        RETURNED,
    }
}
