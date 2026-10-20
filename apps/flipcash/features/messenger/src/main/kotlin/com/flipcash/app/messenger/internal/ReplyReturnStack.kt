package com.flipcash.app.messenger.internal

/**
 * The messages a reader tapped a quote in, newest tap last, which the jump-to-bottom button walks
 * back through before it goes to the bottom. The same rules as iOS's: a tap in the message already
 * on top is not pushed twice.
 */
internal fun List<Long>.pushOrigin(origin: Long): List<Long> =
    if (lastOrNull() == origin) this else this + origin

/**
 * The origins the reader has scrolled down to or past, by hand: on screen, or above it.
 *
 * The list is reverseLayout, so index 0 is the newest row and above the viewport is a higher index
 * than [firstVisibleIndex]. An origin with no index is not loaded and is not counted as reached;
 * one that has gone from the chat is skipped when the button gets to it instead.
 */
internal fun List<Long>.originsReached(firstVisibleIndex: Int, indexOf: (Long) -> Int?): Set<Long> =
    filterTo(mutableSetOf()) { origin -> indexOf(origin)?.let { it >= firstVisibleIndex } == true }

/** Drops every origin on top that is in [reached], stopping at the first one that is not. */
internal fun List<Long>.dropReached(reached: Set<Long>): List<Long> = dropLastWhile { it in reached }

/**
 * Where a tap on the button returns to: the top origin still in the chat, how far it sits from the
 * newest message, and how many entries the tap takes off the stack — that one and every gone
 * origin above it. A null [origin] means none was left, and the button goes to the bottom.
 */
internal data class ReturnLanding(val origin: Long?, val budget: Int?, val popped: Int)

/** Walks down from the top, asking [distanceOf] for each origin until one is still in the chat. */
internal suspend fun List<Long>.returnLanding(distanceOf: suspend (Long) -> Int?): ReturnLanding {
    for ((popped, origin) in asReversed().withIndex()) {
        val distance = distanceOf(origin) ?: continue
        return ReturnLanding(origin = origin, budget = distance, popped = popped + 1)
    }
    return ReturnLanding(origin = null, budget = null, popped = size)
}
