package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.PaymentRequestDataSource
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.chat.GroupContentCrypto
import com.flipcash.services.controllers.PaymentRequestController
import com.flipcash.services.models.GetPaymentRequestError
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.NewPaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentIntentId
import com.flipcash.services.models.paymentrequest.Payer
import com.flipcash.services.models.paymentrequest.PayerSection
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    /**
     * Requests a fetch found DENIED or NOT_FOUND. Their cards draw as unsupported. A refusal can
     * be the server not yet counting a member who just joined, so a request leaves this set once
     * it arrives by any path ([ingest]), and one marked for a chat is retried each time that chat
     * refetches ([loadChat]).
     */
    val missing: StateFlow<Set<ID>> = _missing.asStateFlow()

    // The chat each request in [missing] was fetched for, when the fetch named one.
    private val missingIn = MutableStateFlow<Map<ID, ChatId>>(emptyMap())

    private val _payers = MutableStateFlow<Map<ID, PaidPayers>>(emptyMap())

    /**
     * The paid payers of each request whose details have asked for them ([loadPayers]), kept current
     * by the stream's payer updates. Per-process like [missing]: payers are never stored.
     */
    val payers: StateFlow<Map<ID, PaidPayers>> = _payers.asStateFlow()

    private val inFlight = MutableStateFlow<Set<ID>>(emptySet())

    private val hydrated = MutableStateFlow<Set<ChatId>>(emptySet())

    /**
     * The held requests made in [chatId], in no particular order; its stored ones are loaded first.
     *
     * Text this device holds the key for is open in the first emission. Text that needs the key
     * fetched opens alongside, not before it: offline that fetch waits for a connection instead of
     * failing, and the chat's transcript waits on this flow, so waiting here kept a private group's
     * messages from drawing offline.
     */
    fun inChat(chatId: ChatId): Flow<List<PaymentRequest>> = channelFlow {
        hydrate(chatId)
        launch { openHeld { it.chatId == chatId } }
        requests.map { all -> all.values.filter { it.chatId == chatId } }.distinctUntilChanged().collect { send(it) }
    }

    /** The held request, loaded from storage first when the map has none. Opens as [inChat] does. */
    fun request(id: ID): Flow<PaymentRequest?> = channelFlow {
        if (id !in requests.value) hydrate(id)
        launch { openHeld { it.id == id } }
        requests.map { it[id] }.distinctUntilChanged().collect { send(it) }
    }

    /**
     * Refetches [chatId]'s open requests (first 100) and recently resolved ones (first 20), then
     * retries each request marked [missing] for this chat. A retry that fails leaves its mark and
     * doesn't fail the load.
     */
    suspend fun loadChat(chatId: ChatId): Result<Unit> = runCatching {
        // Stored copies first, so none of them lands after, and over, a fresher fetched one.
        hydrate(chatId)
        // The two sections are separate first pages; either failing cancels the other.
        val (open, resolved) = coroutineScope {
            val open = async {
                controller.getChatPaymentRequests(chatId, PaymentRequestSection.OPEN, QueryOptions(limit = OPEN_PAGE))
                    .getOrThrow()
            }
            val resolved = async {
                controller.getChatPaymentRequests(chatId, PaymentRequestSection.RESOLVED, QueryOptions(limit = RESOLVED_PAGE))
                    .getOrThrow()
            }
            open.await() to resolved.await()
        }
        ingest(open.paymentRequests + resolved.paymentRequests)
        // The mark stays while the retry runs, so the card doesn't flip to loading and back.
        val marked = missingIn.value.filterValues { it == chatId }.keys
        for (id in marked) {
            try {
                refetch(id, chatId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Left marked; the chat's next refetch tries again.
            }
        }
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
     *
     * [chatId] is the chat the request is shown in, when the caller knows it: a DENIED or
     * NOT_FOUND then marks it [missing] for that chat, whose [loadChat] retries it. Without one
     * the mark stays until the request arrives by another path.
     */
    suspend fun fetch(id: ID, chatId: ChatId? = null): Result<PaymentRequestDetails> =
        controller.getPaymentRequest(id)
            .onSuccess { ingest(listOf(it.paymentRequest)) }
            .onFailure {
                if (it is GetPaymentRequestError.Denied || it is GetPaymentRequestError.NotFound) {
                    // The chat goes in first, so a mark is never seen without the chat that retries it.
                    if (chatId != null) missingIn.update { held -> held + (id to chatId) }
                    _missing.update { set -> set.plusElement(id) }
                }
            }

    /**
     * Fetches [id] for a card in [chatId] that has nothing to show, once: not while a fetch for
     * it is running, and not after one found it missing. A missing one is retried by [loadChat].
     */
    suspend fun fetchIfAbsent(id: ID, chatId: ChatId) {
        if (id in requests.value || id in _missing.value) return
        var claimed = false
        inFlight.update { held -> claimed = id !in held; held.plusElement(id) }
        if (!claimed) return
        try {
            fetch(id, chatId)
        } finally {
            inFlight.update { it.minusElement(id) }
        }
    }

    /**
     * Loads [id]'s paid payers (`GetPayers` section PAID, newest first), up to [MAX_PAYER_PAGES]
     * pages, and holds them in [payers]. A list already held at a newer version is kept.
     *
     * Payer updates are merged only into a list already held, so one that lands while the first
     * list loads is lost. If the request moved past the version the list was read at, it loads once
     * more.
     */
    suspend fun loadPayers(id: ID): Result<PaidPayers> {
        val loaded = readPayers(id)
        val requestVersion = requests.value[id]?.version ?: return loaded
        val listVersion = loaded.getOrNull()?.version ?: return loaded
        return if (requestVersion > listVersion) readPayers(id) else loaded
    }

    private suspend fun readPayers(id: ID): Result<PaidPayers> = runCatching {
        var page = controller.getPayers(id, PayerSection.PAID, QueryOptions(limit = PAYER_PAGE)).getOrThrow()
        // Updates past the first page's version go on top, so it is the list's version.
        val version = page.version
        val paid = page.payers.toMutableList()
        var pages = 1
        while (page.hasMore && page.pagingToken != null && pages < MAX_PAYER_PAGES) {
            page = controller.getPayers(id, PayerSection.PAID, QueryOptions(limit = PAYER_PAGE, token = page.pagingToken))
                .getOrThrow()
            paid += page.payers
            pages++
        }
        PaidPayers(version, paid.filter { it.isPaid }.distinctBy { it.userId })
    }.onSuccess { loaded ->
        _payers.update { held ->
            val current = held[id]
            if (current != null && current.version > loaded.version) held else held + (id to loaded)
        }
    }.onFailure { if (it is CancellationException) throw it }

    /**
     * The intent ID to pay [id] under. It is for the one payment: keep it in memory, never store or
     * log it.
     */
    suspend fun paymentIntentId(id: ID): Result<PaymentIntentId> = controller.getPaymentIntentId(id)

    suspend fun create(request: NewPaymentRequest, idempotencyKey: IdempotencyKey): Result<PaymentRequest> =
        controller.createPaymentRequest(request, idempotencyKey).map { ingestOne(it) }

    suspend fun close(id: ID): Result<PaymentRequest> =
        controller.closePaymentRequest(id).map { ingestOne(it) }

    suspend fun markPaid(id: ID, userId: ID): Result<PaymentRequest> =
        controller.markPaid(id, userId).map { ingestOne(it) }

    /** Opens each request's text, then merges it into what is held. A request held is no longer [missing]. */
    suspend fun ingest(incoming: List<PaymentRequest>) {
        val opened = incoming.map { open(it) }
        requests.update { held -> opened.fold(held, ::merge) }
        // After the merge, so a card goes from unsupported straight to drawn, never through loading.
        val arrived = opened.mapTo(HashSet()) { it.id }
        _missing.update { it - arrived }
        missingIn.update { it - arrived }
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
                    if (kind is PaymentRequestUpdate.Kind.Updates) {
                        var gap = false
                        _payers.update { held ->
                            val current = held[update.paymentRequestId] ?: return@update held
                            val merged = mergePayers(current, kind)
                            gap = merged.gap
                            held + (update.paymentRequestId to merged.payers)
                        }
                        if (gap) refetch += update.paymentRequestId
                    }
                }
            }
        }
        return refetch.toList()
    }

    /** Refetches [id] to reconcile a missed or unappliable update; one fetch at a time per request. */
    override suspend fun refetch(id: ID) = refetch(id, chatId = null)

    // [chatId] rides along so a refusal re-marks the request for the chat that retries it, even
    // when the request arrived, and its mark went, while this fetch was out.
    private suspend fun refetch(id: ID, chatId: ChatId?) {
        var claimed = false
        inFlight.update { held -> claimed = id !in held; held.plusElement(id) }
        if (!claimed) return
        try {
            fetch(id, chatId)
            // A payer list in use is reconciled with the request.
            if (id in _payers.value) loadPayers(id)
        } finally {
            inFlight.update { it.minusElement(id) }
        }
    }

    /** Drops what the session holds; the stored requests stay for the account's next session. */
    fun clear() {
        requests.value = emptyMap()
        _missing.value = emptySet()
        missingIn.value = emptyMap()
        _payers.value = emptyMap()
        hydrated.value = emptySet()
    }

    /**
     * Drops [chatId]'s requests from what the session holds, for a chat the viewer has left. The
     * stored rows go with the membership change (see `ChatMetadataDataSource.removeFromChat`); this
     * is the in-memory half, and un-marks the chat as hydrated so a rejoin loads afresh. Requests
     * marked [missing] for the chat are un-marked too.
     */
    fun forget(chatId: ChatId) {
        val gone = requests.value.filterValues { it.groupChatId == chatId }.keys +
            missingIn.value.filterValues { it == chatId }.keys
        requests.update { held -> held - gone }
        _missing.update { it - gone }
        missingIn.update { it - gone }
        _payers.update { it - gone }
        hydrated.update { it - chatId }
    }

    /** Erases the stored requests, with the rest of the account's cache. */
    suspend fun clearStored() = store.clear()

    /**
     * Loads [chatId]'s stored requests into the map, once per session. Their text is opened only
     * with a key this device holds; what that leaves sealed, [openHeld] opens. They merge like
     * fetched copies, so a newer one already held stays, and they are not written back. A store
     * that fails leaves the map as it was.
     */
    private suspend fun hydrate(chatId: ChatId) {
        var first = false
        hydrated.update { held -> first = chatId !in held; held.plusElement(chatId) }
        if (!first) return
        val stored = storeCatching { store.inChat(chatId) } ?: return
        val opened = stored.map { open(it, localOnly = true) }
        requests.update { held -> opened.fold(held, ::merge) }
    }

    private suspend fun hydrate(id: ID) {
        val stored = storeCatching { store.find(id) } ?: return
        val opened = open(stored, localOnly = true)
        requests.update { held -> merge(held, opened) }
    }

    /**
     * Opens the held requests matching [which] whose text is still sealed. One whose key can't be
     * fetched stays sealed; a copy that arrived meanwhile wins by [merge]'s rules.
     */
    private suspend fun openHeld(which: (PaymentRequest) -> Boolean) {
        val sealed = requests.value.values.filter { which(it) && it.split?.text is PaymentRequestText.Encrypted }
        if (sealed.isEmpty()) return
        val opened = sealed.map { open(it) }.filter { it.split?.text !is PaymentRequestText.Encrypted }
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

    private suspend fun open(request: PaymentRequest, localOnly: Boolean = false): PaymentRequest {
        val kind = request.kind as? PaymentRequest.Kind.GroupSplit ?: return request
        val sealed = kind.split.text as? PaymentRequestText.Encrypted ?: return request
        // A key that can't be fetched yet leaves the text sealed; a later fetch opens it.
        val text = crypto.openPaymentRequestText(kind.chatId, request.creator, sealed, localOnly)
            .getOrElse { return request }
        // The sealed form rides along so storage keeps the text as received.
        return request.copy(kind = kind.copy(split = kind.split.copy(text = text, sealedText = sealed)))
    }

    /** [requests] after an update, and whether the request must be refetched to reconcile it. */
    data class UpdateOutcome(val requests: Map<ID, PaymentRequest>, val refetch: Boolean)

    /**
     * A request's paid payers as of [version], newest paid first (ties by user id), as `GetPayers`
     * section PAID lists them.
     */
    data class PaidPayers(val version: Long, val payers: List<Payer>)

    /** [payers] after an update, and whether one was skipped so the list must be reloaded. */
    data class PayersOutcome(val payers: PaidPayers, val gap: Boolean)

    companion object {
        const val OPEN_PAGE = 100
        const val RESOLVED_PAGE = 20
        const val PAYER_PAGE = 100
        const val MAX_PAYER_PAGES = 5

        /**
         * [held] with [update]'s payer changes applied, by version (L is the list's, V the update's):
         * V <= L is already in the list; otherwise each payer replaces the one with its user id (or is
         * added), a payer not paid leaves the list, and the version becomes V. V > L + 1 still applies,
         * since a payer change carries the payer as it now is, but reports a gap: a skipped version
         * may have changed a payer this one does not mention.
         */
        fun mergePayers(held: PaidPayers, update: PaymentRequestUpdate.Kind.Updates): PayersOutcome {
            if (update.version <= held.version) return PayersOutcome(held, gap = false)
            val changed = update.changes.filterIsInstance<PaymentRequestUpdate.Change.PayerChanged>().map { it.payer }
            val byUser = held.payers.associateBy { it.userId }.toMutableMap()
            for (payer in changed) {
                if (payer.isPaid) byUser[payer.userId] = payer else byUser.remove(payer.userId)
            }
            return PayersOutcome(
                payers = PaidPayers(update.version, byUser.values.sortedWith(newestPaidFirst)),
                gap = update.version > held.version + 1,
            )
        }

        private val newestPaidFirst: Comparator<Payer> =
            compareByDescending<Payer> { (it.status as? Payer.Status.Paid)?.paid?.paidAt }
                .thenComparator { a, b -> compareIds(a.userId, b.userId) }

        private fun compareIds(a: ID, b: ID): Int {
            for (i in 0 until minOf(a.size, b.size)) {
                val diff = a[i].toUByte().compareTo(b[i].toUByte())
                if (diff != 0) return diff
            }
            return a.size.compareTo(b.size)
        }

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
         *   the request is refetched rather than taking V for a copy missing a change. A payer
         *   change applies as a no-op, since the copy holds no payers.
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
            // The request holds no payers, so a payer's change leaves it as is. It still counts
            // toward the version, so it must not refetch: every payment's batch carries one.
            is PaymentRequestUpdate.Change.PayerChanged -> request
            is PaymentRequestUpdate.Change.PayerSummaryChanged -> request.copy(payerSummary = change.summary)
            PaymentRequestUpdate.Change.Unknown -> null
        }
    }
}
