package com.flipcash.shared.chat.reactions

/**
 * Decides which message ids need a reaction refresh (a [com.flipcash.shared.chat.ChatCoordinator.refreshReactions]
 * call), and how to batch that call.
 *
 * Two entry points, matching the two places a hook fires:
 *  - [initialWindow]: chat opens, or the app/stream comes back to the foreground — refresh the
 *    newest messages currently loaded, since a live overlay may have been missed while backgrounded.
 *  - [forLoadedPage]: a new page of *older* messages lands in the paging list. Only messages that
 *    were read out of Room (already-cached rows the pager attached, not a fresh server page) need a
 *    refresh — a page the RemoteMediator just fetched from the network already carries current
 *    reaction state, so refreshing it again would be redundant.
 *
 * Both are pure: callers own the "already refreshed" bookkeeping and pass in what's still needed.
 */
object ReactionRefreshPlanner {

    const val DEFAULT_WINDOW_SIZE = 60
    const val DEFAULT_CHUNK_SIZE = 40

    /**
     * @param newestFirstIds all currently loaded message ids, newest first.
     * @param alreadyRefreshed ids this session has already refreshed and doesn't need to repeat.
     */
    fun initialWindow(
        newestFirstIds: List<Long>,
        alreadyRefreshed: Set<Long> = emptySet(),
        windowSize: Int = DEFAULT_WINDOW_SIZE,
    ): List<Long> =
        newestFirstIds
            .asSequence()
            .take(windowSize)
            .filterNot { it in alreadyRefreshed }
            .toList()

    /**
     * @param pageIds ids in the page that just loaded.
     * @param sourcedFromServer true when this page came from a RemoteMediator network fetch rather
     *   than an existing Room row; server-fetched pages are skipped.
     * @return chunks of ids to refresh, each no larger than [chunkSize].
     */
    fun forLoadedPage(
        pageIds: List<Long>,
        sourcedFromServer: Boolean,
        alreadyRefreshed: Set<Long> = emptySet(),
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
    ): List<List<Long>> {
        if (sourcedFromServer) return emptyList()
        val remaining = pageIds.filterNot { it in alreadyRefreshed }
        if (remaining.isEmpty()) return emptyList()
        return remaining.chunked(chunkSize)
    }
}
