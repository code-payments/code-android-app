package com.flipcash.shared.chat.reactions

import com.flipcash.services.models.PagingToken
import com.flipcash.services.repository.ReactorsPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns one [ReactorsListModel] per messageId, keyed so the reactors sheet and whatever started the
 * fetch read the same instance rather than each building their own.
 *
 * Loading has to start on the pill long-press (decision: `OpenReactors` fires the fetch), not on
 * the sheet's first composition — the sheet is a fresh Compose tree on every push, and a fetch
 * gated on its `LaunchedEffect` would show a load on the same tap it could otherwise be racing.
 * [start] is called from `ChatViewModel`'s `OpenReactors` handler, one step before the navigator
 * pushes `ChatStep.Reactors`, and the sheet then only *reads* [rows]/[loading] for the id it opened
 * on rather than kicking off its own load. [start] is idempotent per messageId so re-opening an
 * already-fetched sheet doesn't refetch from scratch.
 */
class ReactorsPrefetchCache(
    private val scope: CoroutineScope,
    private val fetchPage: suspend (messageId: Long, emoji: String, token: PagingToken?) -> Result<ReactorsPage>,
) {

    private class Holder(val model: ReactorsListModel) {
        val rows = MutableStateFlow<List<ReactorsListModel.Row>>(emptyList())
        val loading = MutableStateFlow(true)
    }

    private val cache = mutableMapOf<Long, Holder>()

    /** The merged rows for [messageId], or an always-empty flow before [start] has been called. */
    fun rows(messageId: Long): StateFlow<List<ReactorsListModel.Row>> =
        cache[messageId]?.rows?.asStateFlow() ?: EMPTY_ROWS

    /** Whether a page is in flight for [messageId]. */
    fun loading(messageId: Long): StateFlow<Boolean> =
        cache[messageId]?.loading?.asStateFlow() ?: NOT_LOADING

    /** True while at least one of [messageId]'s emojis still has an unfetched page. */
    fun hasMore(messageId: Long): Boolean = cache[messageId]?.model?.hasMore ?: false

    /**
     * Starts the first round of pages for [messageId]'s [emojis] if nothing has started yet.
     * No-ops for an empty [emojis] list (nothing reacted, so nothing to fetch) and for a messageId
     * already in the cache — a second `OpenReactors` for the same message re-opens the existing
     * fetch/rows rather than starting over.
     */
    fun start(messageId: Long, emojis: List<String>) {
        if (emojis.isEmpty() || cache.containsKey(messageId)) return
        val holder = Holder(ReactorsListModel(emojis) { emoji, token -> fetchPage(messageId, emoji, token) })
        cache[messageId] = holder
        loadMore(messageId, holder)
    }

    /** Fetches the next page for every emoji of [messageId] that still has more, once no load is already in flight. */
    fun loadMoreIfNeeded(messageId: Long) {
        val holder = cache[messageId] ?: return
        if (holder.loading.value || !holder.model.hasMore) return
        loadMore(messageId, holder)
    }

    private fun loadMore(messageId: Long, holder: Holder) {
        holder.loading.value = true
        scope.launch {
            holder.model.loadMoreIfNeeded()
            holder.rows.value = holder.model.rows
            holder.loading.value = false
        }
    }

    /** Drops [messageId]'s cached fetch/rows — for a message that leaves the transcript (deleted, etc). */
    fun clear(messageId: Long) {
        cache.remove(messageId)
    }

    private companion object {
        val EMPTY_ROWS = MutableStateFlow<List<ReactorsListModel.Row>>(emptyList()).asStateFlow()
        val NOT_LOADING = MutableStateFlow(false).asStateFlow()
    }
}
