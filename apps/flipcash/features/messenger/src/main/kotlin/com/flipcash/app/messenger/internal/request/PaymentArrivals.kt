package com.flipcash.app.messenger.internal.request

import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

/**
 * Picks out the requests an open chat's cash messages pay. A payment landing changes its request
 * and nothing else says so, so each such message sent after [openedAt] earns one refetch. Older
 * messages are history loading in, and a message seen again (the list re-emits on every change)
 * is not counted twice. Two messages for one request are two refetches; the in-flight dedupe in
 * `PaymentRequests.refetch` folds those that overlap.
 */
internal class PaymentArrivals(private val openedAt: Instant) {
    private val seen = mutableSetOf<Long>()

    /** The request ids to refetch for [messages], one per message not seen before. */
    fun newlyPaid(messages: List<ChatMessage>): List<ID> =
        messages.mapNotNull { message ->
            val id = message.content.firstNotNullOfOrNull { (it as? MessageContent.Cash)?.paymentRequestId }
            id?.takeIf { message.timestamp > openedAt && seen.add(message.messageId) }
        }
}
