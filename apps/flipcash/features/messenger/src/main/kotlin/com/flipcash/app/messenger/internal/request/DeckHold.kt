package com.flipcash.app.messenger.internal.request

import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus

/**
 * The deck to show while a completed request sweeps its ring to full before leaving: [shown]'s
 * order, each card updated from [next], and each request that left [next] because it completed
 * kept in its slot as its [held] copy. Requests new to [next] go at the end. Closed and expired
 * requests leave at once. Null when nothing completed, so the deck can show [next] directly.
 */
internal fun holdForCompletion(
    shown: List<PaymentRequest>,
    next: List<PaymentRequest>,
    held: Collection<PaymentRequest>,
): List<PaymentRequest>? {
    val nextById = next.associateBy { it.id }
    val completed = held
        .filter { it.status == PaymentRequestStatus.Completed && it.id !in nextById }
        .associateBy { it.id }
        .filterKeys { id -> shown.any { it.id == id } }
    if (completed.isEmpty()) return null
    val shownIds = shown.map { it.id }.toSet()
    return shown.mapNotNull { nextById[it.id] ?: completed[it.id] } + next.filter { it.id !in shownIds }
}
