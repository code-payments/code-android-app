package com.flipcash.app.messenger.internal.request

import com.flipcash.services.models.paymentrequest.PaymentRequest
import kotlin.time.Instant

/**
 * [next]'s contents in [shown]'s order, when ending a request is all that moves the deck: the same
 * requests, a request that was open at [shownNow] and is ended at [now], and a different order.
 * The deck keeps [shown]'s order while the ended look fades, then settles to [next]. Null when
 * nothing needs holding.
 */
internal fun holdForEndedFade(
    shown: List<PaymentRequest>,
    shownNow: Instant,
    next: List<PaymentRequest>,
    now: Instant,
): List<PaymentRequest>? {
    if (shown.map { it.id } == next.map { it.id }) return null
    val slotted = inShownOrder(shown, next) ?: return null
    val ended = shown.indices.any { shown[it].isOpen(shownNow) && !slotted[it].isOpen(now) }
    return slotted.takeIf { ended }
}

/** [next]'s contents in [shown]'s order, or null unless both hold the same requests. */
internal fun inShownOrder(shown: List<PaymentRequest>, next: List<PaymentRequest>): List<PaymentRequest>? {
    if (shown.size != next.size) return null
    val byId = next.associateBy { it.id }
    if (byId.size != next.size || shown.any { it.id !in byId }) return null
    return shown.map { byId.getValue(it.id) }
}
