package com.flipcash.shared.chat.reactions

import com.flipcash.services.models.PagingToken
import com.flipcash.services.repository.ReactorsPage
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Pure, fake-friendly merge logic for the reactors sheet (decision 4) — mirrors iOS's
 * `ReactorsListModel`/`ReactorsSource`. One row per person who reacted, holding every emoji they
 * used, in the order [emojis] lists them (pill order); rows sorted by their most recent reaction
 * first, then by userId as a tiebreak.
 *
 * Not itself a ViewModel — a VM or step-scoped holder drives it (loadMoreIfNeeded from a start
 * trigger and from "last row visible") and republishes [rows] to its own StateFlow. Kept free of
 * Compose/Android types so it is unit-testable with a fake [fetchPage].
 */
class ReactorsListModel(
    private val emojis: List<String>,
    private val fetchPage: suspend (emoji: String, token: PagingToken?) -> Result<ReactorsPage>,
) {

    private data class EmojiState(
        val reactors: List<com.flipcash.services.models.chat.Reactor> = emptyList(),
        val nextToken: PagingToken? = null,
        val hasMore: Boolean = true,
        val loading: Boolean = false,
    )

    private val state = emojis.associateWith { EmojiState() }.toMutableMap()

    /** One row per reactor: their userId, every emoji they used (pill order), and their newest reaction time. */
    data class Row(
        val userId: ID,
        val emojis: List<String>,
        val newestReactedAt: kotlin.time.Instant,
    )

    var rows: List<Row> = emptyList()
        private set

    /** True while at least one emoji still has an unfetched page. */
    val hasMore: Boolean
        get() = state.values.any { it.hasMore }

    /**
     * Fetches the next page for every emoji that still has more and isn't already loading, waits
     * for the whole round to land, then republishes [rows]. A failed page for one emoji only turns
     * off that emoji's `hasMore` — the others still complete and are reflected.
     */
    suspend fun loadMoreIfNeeded() {
        val toFetch = state.filterValues { it.hasMore && !it.loading }.keys
        if (toFetch.isEmpty()) return

        toFetch.forEach { emoji -> state[emoji] = state.getValue(emoji).copy(loading = true) }

        coroutineScope {
            val results = toFetch.map { emoji ->
                emoji to async { fetchPage(emoji, state.getValue(emoji).nextToken) }
            }
            for ((emoji, deferred) in results) {
                val current = state.getValue(emoji)
                val result = deferred.await()
                state[emoji] = result.fold(
                    onSuccess = { page ->
                        current.copy(
                            reactors = current.reactors + page.reactors,
                            nextToken = page.nextToken,
                            hasMore = page.hasMore,
                            loading = false,
                        )
                    },
                    onFailure = {
                        current.copy(hasMore = false, loading = false)
                    },
                )
            }
        }

        publish()
    }

    private fun publish() {
        val byUser = LinkedHashMap<String, MutableList<Pair<String, kotlin.time.Instant>>>()
        for (emoji in emojis) {
            val emojiState = state.getValue(emoji)
            for (reactor in emojiState.reactors) {
                val key = reactor.userId.joinToString(",")
                val entries = byUser.getOrPut(key) { mutableListOf() }
                entries += emoji to reactor.reactedAt
            }
        }

        val userIdByKey = mutableMapOf<String, ID>()
        for (emoji in emojis) {
            for (reactor in state.getValue(emoji).reactors) {
                userIdByKey[reactor.userId.joinToString(",")] = reactor.userId
            }
        }

        rows = byUser.entries
            .map { (key, entries) ->
                val emojiOrder = entries.map { it.first }.distinct()
                val newest = entries.maxOf { it.second }
                Row(userId = userIdByKey.getValue(key), emojis = emojiOrder, newestReactedAt = newest)
            }
            .sortedWith(
                compareByDescending<Row> { it.newestReactedAt }
                    .thenByDescending { it.userId.hexEncodedString() },
            )
    }
}

private fun ID.hexEncodedString(): String = joinToString("") { "%02x".format(it) }
