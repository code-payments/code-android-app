package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.PaymentRequestDataSource
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.chat.GroupContentCrypto
import com.flipcash.services.controllers.PaymentRequestController
import com.flipcash.services.models.GetPaymentRequestError
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.NewSplitRequest
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestDetails
import com.flipcash.services.models.paymentrequest.PaymentRequestPage
import com.flipcash.services.models.paymentrequest.PaymentRequestSection
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.PaymentRequestUpdate
import com.flipcash.services.models.paymentrequest.newerOf
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The payment requests the viewer has seen, by id, each the newest copy fetched.
 *
 * Screens refetch (see the spec's refetch table) and every fetched request goes through [ingest]:
 * its text is opened once, then [merge] keeps the newer copy. The chat's event stream also announces
 * changes, best effort, which [onUpdates] applies by version (see [applyUpdate]); polling stays as
 * reconciliation for whatever it misses.
 * Every change to the held map is also written to [store] (see [persist]), which keeps the newer copy
 * by the contract's rule, and a chat's stored requests are loaded into the map before its first
 * refetch ([hydrate]), so cards draw offline and after a restart. Only requests are stored:
 * [missing] and the in-flight fetches are per-process and start empty.
 * A request's `payment_intent_id` never enters the store; [fetch] hands it to the caller.
 */
@Singleton
class PaymentRequests @Inject constructor(
    private val controller: PaymentRequestController,
    private val crypto: GroupContentCrypto,
    private val store: PaymentRequestDataSource,
) : PaymentRequestUpdateSink {
    private val requests = MutableStateFlow<Map<ID, PaymentRequest>>(emptyMap())

    val state: StateFlow<Map<ID, PaymentRequest>> = requests.asStateFlow()

    private val _missing = MutableStateFlow<Set<ID>>(emptySet())

    /** Requests a fetch found DENIED or NOT_FOUND. Their cards draw as unsupported and aren't refetched. */
    val missing: StateFlow<Set<ID>> = _missing.asStateFlow()

    private val inFlight = MutableStateFlow<Set<ID>>(emptySet())

    private val hydrated = MutableStateFlow<Set<ChatId>>(emptySet())

    /** The held requests made in [chatId], in no particular order; its stored ones are loaded first. */
    fun inChat(chatId: ChatId): Flow<List<PaymentRequest>> = flow {
        hydrate(chatId)
        emitAll(requests.map { all -> all.values.filter { it.chatId == chatId } }.distinctUntilChanged())
    }

    /** The held request, loaded from storage first when the map has none. */
    fun request(id: ID): Flow<PaymentRequest?> = flow {
        if (id !in requests.value) hydrate(id)
        emitAll(requests.map { it[id] }.distinctUntilChanged())
    }

    /** Refetches [chatId]'s open requests (first 100) and recently resolved ones (first 20). */
    suspend fun loadChat(chatId: ChatId): Result<Unit> = runCatching {
        hydrate(chatId)
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
        controller.getPaymentRequest(id)
            .onSuccess { ingest(listOf(it.paymentRequest)) }
            .onFailure { if (it is GetPaymentRequestError.Denied || it is GetPaymentRequestError.NotFound) _missing.update { set -> set.plusElement(id) } }

    /**
     * Fetches [id] for a card that has nothing to show, once: not while a fetch for it is running,
     * and not after one found it missing.
     */
    suspend fun fetchIfAbsent(id: ID) {
        if (id in requests.value || id in _missing.value) return
        var claimed = false
        inFlight.update { held -> claimed = id !in held; held.plusElement(id) }
        if (!claimed) return
        try {
            fetch(id)
        } finally {
            inFlight.update { it.minusElement(id) }
        }
    }

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
        persist(opened)
    }

    /**
     * Applies the stream's request updates in order. A `created` goes through [ingest]; an
     * `updates` is applied or ignored by [applyUpdate], which also says when the held copy cannot
     * be trusted. Those requests are returned for [refetch] rather than fetched here, so the
     * stream is not held up by the network.
     */
    override suspend fun onUpdates(updates: List<PaymentRequestUpdate>): List<ID> {
        val refetch = LinkedHashSet<ID>()
        for (update in updates) {
            when (val kind = update.kind) {
                is PaymentRequestUpdate.Kind.Created -> ingest(listOf(kind.paymentRequest))
                is PaymentRequestUpdate.Kind.Updates, PaymentRequestUpdate.Kind.Unknown -> {
                    var stale = false
                    var applied: PaymentRequest? = null
                    requests.update { held ->
                        val outcome = applyUpdate(held, update)
                        stale = outcome.refetch
                        applied = outcome.requests[update.paymentRequestId]
                            ?.takeIf { it !== held[update.paymentRequestId] }
                        outcome.requests
                    }
                    // The whole request at its new version, which the store keeps if it is newer.
                    applied?.let { persist(listOf(it)) }
                    if (stale) refetch += update.paymentRequestId
                }
            }
        }
        return refetch.toList()
    }

    /** Refetches [id] to reconcile a missed or unappliable update; one fetch at a time per request. */
    override suspend fun refetch(id: ID) {
        var claimed = false
        inFlight.update { held -> claimed = id !in held; held.plusElement(id) }
        if (!claimed) return
        try {
            fetch(id)
        } finally {
            inFlight.update { it.minusElement(id) }
        }
    }

    /** Drops what the session holds; the stored requests stay for the account's next session. */
    fun clear() {
        requests.value = emptyMap()
        _missing.value = emptySet()
        hydrated.value = emptySet()
    }

    /** Erases the stored requests, with the rest of the account's cache. */
    suspend fun clearStored() = store.clear()

    /**
     * Loads [chatId]'s stored requests into the map, once per session. They merge like fetched
     * copies, so a newer one already held stays, and they are not written back. A store that fails
     * leaves the map as it was.
     */
    private suspend fun hydrate(chatId: ChatId) {
        var first = false
        hydrated.update { held -> first = chatId !in held; held.plusElement(chatId) }
        if (!first) return
        val stored = storeCatching { store.inChat(chatId) } ?: return
        load(stored)
    }

    private suspend fun hydrate(id: ID) {
        val stored = storeCatching { store.find(id) } ?: return
        load(listOf(stored))
    }

    private suspend fun load(stored: List<PaymentRequest>) {
        val opened = stored.map { open(it) }
        requests.update { held -> opened.fold(held, ::merge) }
    }

    private suspend fun persist(written: List<PaymentRequest>) {
        storeCatching { store.merge(written) }
    }

    // The store is a cache of what the network says: a failure to read or write it must not fail
    // the fetch or the update it rode along with.
    private suspend fun <T> storeCatching(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun ingestOne(request: PaymentRequest): PaymentRequest {
        ingest(listOf(request))
        return requests.value[request.id] ?: request
    }

    private suspend fun open(request: PaymentRequest): PaymentRequest {
        val kind = request.kind as? PaymentRequest.Kind.GroupSplit ?: return request
        val sealed = kind.split.text as? PaymentRequestText.Encrypted ?: return request
        // A key that can't be fetched yet leaves the text sealed; a later fetch opens it.
        val text = crypto.openPaymentRequestText(kind.chatId, request.creator, sealed)
            .getOrElse { return request }
        // The sealed form rides along so storage keeps the text as received.
        return request.copy(kind = kind.copy(split = kind.split.copy(text = text, sealedText = sealed)))
    }

    /** [requests] after an update, and whether the request must be refetched to reconcile it. */
    data class UpdateOutcome(val requests: Map<ID, PaymentRequest>, val refetch: Boolean)

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

        /**
         * Applies an [PaymentRequestUpdate.Kind.Updates] to the held copy of its request, by version
         * (L is the held version, V the update's):
         * - no held copy: ignored; the request is fetched when it is shown;
         * - V <= L: already applied, ignored;
         * - V > L + 1: an update was missed, so refetch;
         * - V == L + 1: every change is applied in order and the version set to V, unless one
         *   cannot be (unknown kind, or a share the copy lacks), in which case none is applied and
         *   the request is refetched rather than taking V for a copy missing a change.
         *
         * A [PaymentRequestUpdate.Kind.Unknown] cannot be applied, so it refetches a request that is
         * held. A `created` is not handled here: it merges like a fetched copy ([merge]).
         */
        fun applyUpdate(held: Map<ID, PaymentRequest>, update: PaymentRequestUpdate): UpdateOutcome {
            val current = held[update.paymentRequestId] ?: return UpdateOutcome(held, refetch = false)
            val kind = update.kind
            if (kind !is PaymentRequestUpdate.Kind.Updates) {
                return UpdateOutcome(held, refetch = kind is PaymentRequestUpdate.Kind.Unknown)
            }
            if (kind.version <= current.version) return UpdateOutcome(held, refetch = false)
            if (kind.version > current.version + 1) return UpdateOutcome(held, refetch = true)
            val applied = kind.changes.fold<PaymentRequestUpdate.Change, PaymentRequest?>(current) { copy, change ->
                copy?.let { applyChange(it, change) }
            } ?: return UpdateOutcome(held, refetch = true)
            return UpdateOutcome(held + (current.id to applied.copy(version = kind.version)), refetch = false)
        }

        /** [request] with [change] applied, or null when it can't be. */
        private fun applyChange(request: PaymentRequest, change: PaymentRequestUpdate.Change): PaymentRequest? = when (change) {
            is PaymentRequestUpdate.Change.SharePaid -> {
                val kind = request.kind as? PaymentRequest.Kind.GroupSplit
                val share = kind?.split?.shareOf(change.userId)
                if (kind == null || share == null || change.paid == null) null
                else request.copy(
                    kind = kind.copy(
                        split = kind.split.copy(
                            // The update carries no amount: keep the held share's.
                            shares = kind.split.shares.map { if (it.userId == change.userId) it.copy(paid = change.paid) else it },
                        ),
                    ),
                )
            }
            is PaymentRequestUpdate.Change.StatusChanged -> when (change.status) {
                PaymentRequestStatus.Completed, PaymentRequestStatus.Closed ->
                    request.copy(status = change.status, closedAt = change.closedAt ?: request.closedAt)
                else -> null
            }
            PaymentRequestUpdate.Change.Unknown -> null
        }
    }
}
