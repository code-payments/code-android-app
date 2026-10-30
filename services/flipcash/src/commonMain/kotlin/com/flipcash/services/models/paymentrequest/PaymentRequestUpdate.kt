package com.flipcash.services.models.paymentrequest

import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

/**
 * A best-effort real-time change to a payment request, from `ChatUpdate.payment_request_updates`.
 * Unlike chat events these are not gap-detected, so the store applies them by version:
 *
 * - [Kind.Created]: treat like a fetched copy and keep the greater version (see [newerOf]).
 * - [Kind.Updates] at version V against a held version L: V <= L is ignored; V == L + 1 applies the
 *   [Kind.Updates.changes] in order and sets the version to V; V > L + 1 refetches the request.
 * - A [Change.Unknown] anywhere in a batch, or [Kind.Unknown], refetches rather than guessing.
 */
data class PaymentRequestUpdate(
    val paymentRequestId: ID,
    val kind: Kind,
) {
    sealed interface Kind {
        data class Created(val paymentRequest: PaymentRequest) : Kind
        data class Updates(val version: Long, val changes: List<Change>) : Kind
        data object Unknown : Kind
    }

    sealed interface Change {
        /** [userId]'s share is now [paid]; apply it onto the held share of that user. */
        data class SharePaid(val userId: ID, val paid: PaidShare?) : Change

        /** The request moved to [status] (COMPLETED or CLOSED on the wire), closed at [closedAt]. */
        data class StatusChanged(val status: PaymentRequestStatus, val closedAt: Instant?) : Change

        /** A change kind this build does not know, or a share update with no share. */
        data object Unknown : Change
    }
}
