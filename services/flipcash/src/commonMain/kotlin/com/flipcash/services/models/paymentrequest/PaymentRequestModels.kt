package com.flipcash.services.models.paymentrequest

import com.flipcash.services.models.PagingToken

/** Which half of a chat's payment requests to list. Mirrors `GetChatPaymentRequestsRequest.Section`. */
enum class PaymentRequestSection {
    /** Requests still awaiting payment. */
    OPEN,

    /** Requests that are completed, closed or expired. */
    RESOLVED,
}

/**
 * A payment request, with the intent id the caller's share payment must use. [paymentIntentId] is
 * set only for a caller who holds an unpaid share, and is never stored with the request.
 */
data class PaymentRequestDetails(
    val paymentRequest: PaymentRequest,
    val paymentIntentId: PaymentIntentId?,
)

/** One page of a chat's payment requests. Page with [pagingToken] while [hasMore]. */
data class PaymentRequestPage(
    val paymentRequests: List<PaymentRequest>,
    val pagingToken: PagingToken?,
    val hasMore: Boolean,
)
