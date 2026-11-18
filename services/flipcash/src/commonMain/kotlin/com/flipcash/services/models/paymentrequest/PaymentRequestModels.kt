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
 * A payment request, with the intent id the caller's share payment must use and the caller's part
 * in it.
 *
 * @param paymentIntentId set only on a split, for a caller who holds an unpaid share, and never
 *   stored with the request. The server keeps it for older clients; a collection never sets it, and
 *   new code asks `GetPaymentIntentId` when the user starts to pay.
 * @param callerPayer the caller's [Payer], exactly as `GetPayers` lists it: for a split, when the
 *   caller holds a share (the creator's own included); for a collection, when the caller has
 *   contributed. Null otherwise, so on a collection null means the caller has not paid.
 */
data class PaymentRequestDetails(
    val paymentRequest: PaymentRequest,
    val paymentIntentId: PaymentIntentId?,
    val callerPayer: Payer? = null,
)

/** Which half of a request's payers to list. Mirrors `GetPayersRequest.Section`. */
enum class PayerSection {
    /** Payers who still owe something. Always empty for a collection. */
    UNPAID,

    /** Payers who have paid. */
    PAID,
}

/**
 * One page of a request's payers. Page with [pagingToken] while [hasMore].
 *
 * @param version the request's version as of this page's read. Every change at or below it is in
 *   the page; to keep the list current, apply each [PaymentRequestUpdate] past it on top, replacing
 *   a payer by user id.
 */
data class PayerPage(
    val payers: List<Payer>,
    val pagingToken: PagingToken?,
    val hasMore: Boolean,
    val version: Long,
)

/** One page of a chat's payment requests. Page with [pagingToken] while [hasMore]. */
data class PaymentRequestPage(
    val paymentRequests: List<PaymentRequest>,
    val pagingToken: PagingToken?,
    val hasMore: Boolean,
)
