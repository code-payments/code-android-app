package com.flipcash.shared.chat

import com.flipcash.services.models.paymentrequest.PaymentRequestUpdate
import com.getcode.opencode.model.core.ID

/** Where the chat event stream sends payment request updates. [PaymentRequests] is the real one. */
interface PaymentRequestUpdateSink {
    /** Applies [updates] in order and returns the requests whose held copy needs a [refetch]. */
    suspend fun onUpdates(updates: List<PaymentRequestUpdate>): List<ID>

    suspend fun refetch(id: ID)

    object None : PaymentRequestUpdateSink {
        override suspend fun onUpdates(updates: List<PaymentRequestUpdate>): List<ID> = emptyList()
        override suspend fun refetch(id: ID) = Unit
    }
}
