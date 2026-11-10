package com.flipcash.app.messenger.internal.request

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaidShare
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.Split
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.SeparatorConfig
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

/**
 * The split's paid fraction as of [at], for a row that shows the request as it stood then: only
 * shares paid at or before [at] count, and the creator's own share counts from creation.
 *
 * [payer] counts whatever the stored request says. A share payment's message can arrive before the
 * refetch that marks its share paid, and its own row must not show the ring without it.
 */
internal fun Split.paidFractionAsOf(at: Instant, payer: ID? = null): Float {
    val total = requested.quarks
    if (total <= 0L) return 0f
    val paid = shares.filter { share ->
        val paid = share.paid
        share.userId == payer || (paid != null && (paid.how == PaidShare.How.Creator || paid.paidAt <= at))
    }.sumOf { it.amount.quarks }
    return (paid.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * The ring for a share payment row: the request as of [payer]'s payment. The stored share's
 * `paid_at` is the anchor; until the request shows that share paid, [sentAt], the message's time.
 */
internal fun Split.paidFractionAfterPayment(payer: ID?, sentAt: Instant): Float {
    val paidAt = payer?.let { shareOf(it) }?.paid?.paidAt ?: sentAt
    return paidFractionAsOf(paidAt, payer)
}

/** A change to a request that posts no message, at the time it happened. */
internal sealed interface RequestEvent {
    val request: PaymentRequest
    val at: Instant

    data class MarkedPaid(override val request: PaymentRequest, val userId: ID, override val at: Instant) : RequestEvent
    data class Closed(override val request: PaymentRequest, override val at: Instant) : RequestEvent
}

/** The title of a request this chat can show rows for, or null: another chat's, unsupported or unopened. */
internal fun PaymentRequest.rowTitle(chatId: ChatId): String? {
    val kind = kind as? PaymentRequest.Kind.GroupSplit ?: return null
    if (kind.chatId != chatId) return null
    return (kind.split.text as? PaymentRequestText.Plain)?.title
}

/**
 * Every marked-paid and closed event of [requests] in [chatId], newest first.
 *
 * Reads the status as the server sent it: a request that expired by the clock, or completed, has no
 * closed row. A share the creator marked and the member then paid reads `Payment`, so its mark is
 * gone and the payment's own message draws the row.
 */
internal fun requestEvents(requests: Collection<PaymentRequest>, chatId: ChatId): List<RequestEvent> =
    requests.filter { it.rowTitle(chatId) != null }.flatMap { request ->
        val marks = request.split?.shares.orEmpty().mapNotNull { share ->
            share.paid?.takeIf { it.how == PaidShare.How.Manual }
                ?.let { RequestEvent.MarkedPaid(request, share.userId, it.paidAt) }
        }
        val closed = request.closedAt?.takeIf { request.status == PaymentRequestStatus.Closed }
            ?.let { RequestEvent.Closed(request, it) }
        marks + listOfNotNull(closed)
    }.sortedByDescending { it.at }

/**
 * The [events] that belong in the gap between [newer] and [older] of the newest-first transcript,
 * newest first: those at or after [older]'s time and before [newer]'s, so an event sharing a
 * message's timestamp goes after that message.
 *
 * [newer] is null above the newest loaded message once nothing newer is left to load, which takes
 * every event from then on. [older] null is the far end of the history, which takes none: an event
 * shows only between loaded messages, and never asks for a page.
 */
internal fun eventsBetween(
    newer: ChatListItem.TranscriptMessage?,
    older: ChatListItem.TranscriptMessage?,
    events: List<RequestEvent>,
): List<RequestEvent> {
    older ?: return emptyList()
    return events.filter { it.at >= older.timestamp && (newer == null || it.at < newer.timestamp) }
}

/**
 * [events], newest first, as rows: consecutive marks for one request fold into one row unless a day
 * changes between them, where a date header will be drawn.
 */
internal fun List<RequestEvent>.toNotices(
    chatId: ChatId,
    viewerId: ID?,
    config: SeparatorConfig,
    above: ChatListItem.TranscriptMessage?,
    nameOf: (ID) -> String,
    keyOf: (ID) -> String,
): List<ChatListItem.RequestNotice> {
    val runs = mutableListOf<MutableList<RequestEvent>>()
    for (event in this) {
        val run = runs.lastOrNull()
        val last = run?.last()
        val joins = event is RequestEvent.MarkedPaid && last is RequestEvent.MarkedPaid &&
            last.request.id == event.request.id && !config.shouldSeparate(last.at, event.at)
        if (run != null && joins) run += event else runs += mutableListOf(event)
    }
    return runs.mapNotNull { run ->
        val head = run.first()
        val request = head.request
        val title = request.rowTitle(chatId) ?: return@mapNotNull null
        val creatorName = if (request.creator == viewerId) null else nameOf(request.creator)
        when (head) {
            is RequestEvent.Closed -> ChatListItem.RequestNotice(
                key = "request-closed-${keyOf(request.id)}",
                requestId = request.id,
                timestamp = head.at,
                kind = ChatListItem.RequestNotice.Kind.Closed,
                creatorName = creatorName,
                title = title,
                above = above,
            )
            is RequestEvent.MarkedPaid -> ChatListItem.RequestNotice(
                key = "request-marked-${keyOf(request.id)}-${keyOf(head.userId)}",
                requestId = request.id,
                timestamp = head.at,
                kind = ChatListItem.RequestNotice.Kind.MarkedPaid(
                    marked = run.filterIsInstance<RequestEvent.MarkedPaid>().map {
                        ChatListItem.RequestNotice.Person(it.userId, nameOf(it.userId), it.userId == viewerId)
                    },
                    progress = request.split?.paidFractionAsOf(head.at) ?: 0f,
                ),
                creatorName = creatorName,
                title = title,
                above = above,
            )
        }
    }
}

/**
 * The share payments [item] at [index] stands for, newest first, or null when a newer row of the
 * same run draws them. A run is share payments for one request with nothing drawn between them:
 * the list already holds date headers, notices and request rows as items, so it is adjacency.
 *
 * [peek] reads the loaded item at an index, null for one not loaded; indices run newest first.
 */
internal fun sharePaymentRun(
    index: Int,
    item: ChatListItem.SharePayment,
    count: Int,
    peek: (Int) -> ChatListItem?,
): List<ChatListItem.SharePayment>? {
    fun at(i: Int) = (if (i in 0 until count) peek(i) else null) as? ChatListItem.SharePayment
    if (at(index - 1)?.requestId == item.requestId) return null
    val run = mutableListOf(item)
    var next = index + 1
    while (true) {
        val older = at(next)?.takeIf { it.requestId == item.requestId } ?: break
        run += older
        next++
    }
    return run
}

/** The index of the row that draws the share payment at [index]: the newest of its run. */
internal fun sharePaymentRunHead(index: Int, count: Int, peek: (Int) -> ChatListItem?): Int {
    val item = (if (index in 0 until count) peek(index) else null) as? ChatListItem.SharePayment ?: return index
    var head = index
    while (head > 0 && (peek(head - 1) as? ChatListItem.SharePayment)?.requestId == item.requestId) head--
    return head
}
