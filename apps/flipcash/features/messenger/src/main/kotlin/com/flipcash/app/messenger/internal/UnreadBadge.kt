package com.flipcash.app.messenger.internal

import com.flipcash.shared.chat.UnreadBoundary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * [ChatViewModel.State.newestInboundIdAtOpen] for a chat with no inbound message stored at open:
 * below any real message id, so every inbound message that comes later counts as an arrival.
 */
internal const val NO_INBOUND_AT_OPEN = -1L

/**
 * What the jump-to-bottom badge shows.
 *
 * While [frozen] it opens at the unread divider's own count and holds there, whatever the read
 * pointer does underneath, rising by one for each inbound message that arrives after the open.
 * Once released it follows [live], the inbound messages past the viewer's read pointer. A viewer
 * outside the group has no read pointer to follow and sees nothing.
 */
internal fun unreadBadgeCount(
    frozen: Boolean,
    boundary: UnreadBoundary,
    arrivalsSinceOpen: Int,
    live: Int,
    outsideGroup: Boolean,
): Int = when {
    outsideGroup -> 0
    frozen -> ((boundary as? UnreadBoundary.At)?.count ?: 0) + arrivalsSinceOpen
    else -> live
}

/**
 * The badge over a chat's [state]. [live] is the inbound count past the read pointer;
 * [arrivalsAfter] counts inbound messages past an id, and is asked for the id that was newest at
 * open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun unreadBadgeCounts(
    state: Flow<ChatViewModel.State>,
    live: Flow<Int>,
    arrivalsAfter: (afterId: Long) -> Flow<Int>,
): Flow<Int> {
    val arrivals = state.map { it.newestInboundIdAtOpen }
        .distinctUntilChanged()
        .flatMapLatest { openedAt -> if (openedAt == null) flowOf(0) else arrivalsAfter(openedAt) }
    val inputs = state
        .map { Triple(it.unreadBadgeFrozen, it.unreadBoundary, it.isOutsideGroup) }
        .distinctUntilChanged()
    return combine(inputs, arrivals, live) { (frozen, boundary, outside), arrived, liveCount ->
        unreadBadgeCount(frozen, boundary, arrived, liveCount, outside)
    }.distinctUntilChanged()
}
