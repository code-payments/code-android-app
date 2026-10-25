package com.flipcash.shared.chat

import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.chat.GroupContentCrypto
import com.flipcash.services.controllers.PaymentRequestController
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.NewSplitRequest
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestDetails
import com.flipcash.services.models.paymentrequest.PaymentRequestPage
import com.flipcash.services.models.paymentrequest.PaymentRequestSection
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.newerOf
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The payment requests the viewer has seen, by id, each the newest copy fetched.
 *
 * No event carries a request change, so screens refetch (see the spec's refetch table) and every
 * fetched request goes through [ingest]: its text is opened once, then [merge] keeps the newer copy.
 * A request's `payment_intent_id` never enters the store; [fetch] hands it to the caller.
 */
@Singleton
class PaymentRequests @Inject constructor(
    private val controller: PaymentRequestController,
    private val crypto: GroupContentCrypto,
) {
    private val requests = MutableStateFlow<Map<ID, PaymentRequest>>(emptyMap())

    val state: StateFlow<Map<ID, PaymentRequest>> = requests.asStateFlow()

    /** The held requests made in [chatId], in no particular order. */
    fun inChat(chatId: ChatId): Flow<List<PaymentRequest>> =
        requests.map { all -> all.values.filter { it.chatId == chatId } }.distinctUntilChanged()

    fun request(id: ID): Flow<PaymentRequest?> = requests.map { it[id] }.distinctUntilChanged()

    /** Refetches [chatId]'s open requests (first 100) and recently resolved ones (first 20). */
    suspend fun loadChat(chatId: ChatId): Result<Unit> = runCatching {
        val open = controller.getChatPaymentRequests(chatId, PaymentRequestSection.OPEN, QueryOptions(limit = OPEN_PAGE))
            .getOrThrow()
        val resolved = controller.getChatPaymentRequests(chatId, PaymentRequestSection.RESOLVED, QueryOptions(limit = RESOLVED_PAGE))
            .getOrThrow()
        ingest(open.paymentRequests + resolved.paymentRequests)
    }

    /**
     * One page of [chatId]'s ended requests, most recently ended first, for Request Money history.
     * [token] is the previous page's; null starts over.
     */
    suspend fun resolvedPage(chatId: ChatId, token: PagingToken?): Result<PaymentRequestPage> =
        controller.getChatPaymentRequests(
            chatId,
            PaymentRequestSection.RESOLVED,
            QueryOptions(limit = RESOLVED_PAGE, token = token),
        ).onSuccess { ingest(it.paymentRequests) }

    /**
     * Refetches one request. The returned details carry `payment_intent_id` for a viewer with an
     * unpaid share; keep it for the one payment and do not store or log it.
     */
    suspend fun fetch(id: ID): Result<PaymentRequestDetails> =
        controller.getPaymentRequest(id).onSuccess { ingest(listOf(it.paymentRequest)) }

    suspend fun create(request: NewSplitRequest, idempotencyKey: IdempotencyKey): Result<PaymentRequest> =
        controller.createPaymentRequest(request, idempotencyKey).map { ingestOne(it) }

    suspend fun close(id: ID): Result<PaymentRequest> =
        controller.closePaymentRequest(id).map { ingestOne(it) }

    suspend fun markPaid(id: ID, userId: ID): Result<PaymentRequest> =
        controller.markPaid(id, userId).map { ingestOne(it) }

    /** Opens each request's text, then merges it into what is held. */
    suspend fun ingest(incoming: List<PaymentRequest>) {
        val opened = incoming.map { open(it) }
        requests.update { held -> opened.fold(held, ::merge) }
    }

    fun clear() {
        requests.value = emptyMap()
    }

    private suspend fun ingestOne(request: PaymentRequest): PaymentRequest {
        ingest(listOf(request))
        return requests.value[request.id] ?: request
    }

    private suspend fun open(request: PaymentRequest): PaymentRequest {
        val kind = request.kind as? PaymentRequest.Kind.GroupSplit ?: return request
        if (kind.split.text !is PaymentRequestText.Encrypted) return request
        // A key that can't be fetched yet leaves the text sealed; a later fetch opens it.
        val text = crypto.openPaymentRequestText(kind.chatId, request.creator, kind.split.text)
            .getOrElse { return request }
        return request.copy(kind = kind.copy(split = kind.split.copy(text = text)))
    }

    companion object {
        const val OPEN_PAGE = 100
        const val RESOLVED_PAGE = 20

        /**
         * [held] with [incoming] merged in by [newerOf]. A copy still sealed never replaces an opened
         * one of the same version.
         */
        fun merge(held: Map<ID, PaymentRequest>, incoming: PaymentRequest): Map<ID, PaymentRequest> {
            val current = held[incoming.id]
            val sealedOverOpened = current != null &&
                current.version == incoming.version &&
                incoming.split?.text is PaymentRequestText.Encrypted &&
                current.split?.text !is PaymentRequestText.Encrypted
            if (sealedOverOpened) return held
            return held + (incoming.id to newerOf(current, incoming))
        }
    }
}
