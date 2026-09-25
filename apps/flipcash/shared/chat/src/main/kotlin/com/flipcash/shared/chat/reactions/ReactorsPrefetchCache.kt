package com.flipcash.shared.chat.reactions

import com.flipcash.services.models.PagingToken
import com.flipcash.services.repository.ReactorsPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns one [ReactorsListModel] per messageId, keyed so the reactors sheet and whatever started the
 * fetch read the same rows/loading state rather than each building their own.
 *
 * Loading has to start on the pill long-press (decision: `OpenReactors` fires the fetch), not on
 * the reactors sheet's first composition — the sheet is a fresh Compose tree on every push, and a
 * fetch gated on its `LaunchedEffect` would race the same tap that opened it. [start] is called
 * from `ChatViewModel`'s `OpenReactors` handler, one step before the navigator pushes
 * `ChatStep.Reactors`; [rows]/[loading] are backed by one shared `StateFlow` each (keyed by
 * messageId), rather than a per-messageId flow instance created inside [start] — a composition that
 * calls [rows] *before* [start] has run still sees the update once it does, because it collects a
 * `.map` over the same always-live backing flow rather than a separate object [start] would
 * otherwise swap in.
 */
class ReactorsPrefetchCache(
    private val scope: CoroutineScope,
    private val fetchPage: suspend (messageId: Long, emoji: String, token: PagingToken?) -> Result<ReactorsPage>,
) {

    private val models = mutableMapOf<Long, ReactorsListModel>()
    private val allRows = MutableStateFlow<Map<Long, List<ReactorsListModel.Row>>>(emptyMap())
    private val allLoading = MutableStateFlow<Map<Long, Boolean>>(emptyMap())

    /** The merged rows for [messageId], empty before [start] has produced a first page. */
    fun rows(messageId: Long): Flow<List<ReactorsListModel.Row>> =
        allRows.map { it[messageId].orEmpty() }.distinctUntilChanged()

    /** Whether a page is in flight for [messageId]. */
    fun loading(messageId: Long): Flow<Boolean> =
        allLoading.map { it[messageId] == true }.distinctUntilChanged()

    /** True while at least one of [messageId]'s emojis still has an unfetched page. */
    fun hasMore(messageId: Long): Boolean = models[messageId]?.hasMore ?: false

    /**
     * Starts the first round of pages for [messageId]'s [emojis] if nothing has started yet.
     * No-ops for an empty [emojis] list (nothing reacted, so nothing to fetch) and for a messageId
     * already in the cache — a second `OpenReactors` for the same message re-opens the existing
     * fetch/rows rather than starting over.
     */
    fun start(messageId: Long, emojis: List<String>) {
        if (emojis.isEmpty() || models.containsKey(messageId)) return
        models[messageId] = ReactorsListModel(emojis) { emoji, token -> fetchPage(messageId, emoji, token) }
        loadMore(messageId)
    }

    /** Fetches the next page for every emoji of [messageId] that still has more, once no load is already in flight. */
    fun loadMoreIfNeeded(messageId: Long) {
        val model = models[messageId] ?: return
        if (allLoading.value[messageId] == true || !model.hasMore) return
        loadMore(messageId)
    }

    private fun loadMore(messageId: Long) {
        allLoading.update { it + (messageId to true) }
        scope.launch {
            val model = models.getValue(messageId)
            model.loadMoreIfNeeded()
            allRows.update { it + (messageId to model.rows) }
            allLoading.update { it + (messageId to false) }
        }
    }

    /** Drops [messageId]'s cached fetch/rows — for a message that leaves the transcript (deleted, etc). */
    fun clear(messageId: Long) {
        models.remove(messageId)
        allRows.update { it - messageId }
        allLoading.update { it - messageId }
    }
}
