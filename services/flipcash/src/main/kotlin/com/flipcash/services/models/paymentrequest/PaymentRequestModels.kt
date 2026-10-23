package com.flipcash.services.models.paymentrequest

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.paymentrequest.v1.Model
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
 * set only for a caller who holds an unpaid share. The request is the generated
 * `paymentrequest.v1` type until a UI consumes it.
 */
data class PaymentRequestDetails(
    val paymentRequest: Model.PaymentRequestMetadata,
    val paymentIntentId: Common.IntentId?,
)

/** One page of a chat's payment requests. Page with [pagingToken] while [hasMore]. */
data class PaymentRequestPage(
    val paymentRequests: List<Model.PaymentRequestMetadata>,
    val pagingToken: PagingToken?,
    val hasMore: Boolean,
)
