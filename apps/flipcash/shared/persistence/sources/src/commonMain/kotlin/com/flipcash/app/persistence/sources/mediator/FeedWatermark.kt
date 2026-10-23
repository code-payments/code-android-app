package com.flipcash.app.persistence.sources.mediator

import com.flipcash.services.models.PagingToken

/** One source's position in a merged feed. */
data class FeedSource(
    val key: String,
    /** What to pass to fetch this source's next page. */
    val token: PagingToken? = null,
    /** Oldest last-activity (epoch ms) this source has produced, or null before its first page. */
    val tail: Long? = null,
    /** The source reported its last page; there is nothing below [tail]. */
    val exhausted: Boolean = false,
    /** The source's last fetch failed. Skipped until it answers again. */
    val failed: Boolean = false,
)

/**
 * Tracks how far each source of a merged feed has been paged, and what that says about the
 * merged list as a whole.
 *
 * Every source orders by most recent activity first and pages with its own opaque token, so
 * their pages do not line up: one page of DMs may span a week and one page of groups a year.
 * Fetching a page from each and concatenating is only correct down to the point where all of
 * them have reported — below the shallowest tail, a source that has not been asked yet may
 * still hold an item that belongs higher than one already fetched.
 *
 * So loading more does not mean "one more page of everything". It means advancing whichever
 * source is currently holding the merged list back, which is the non-exhausted source that has
 * been paged the least far.
 */
class FeedWatermark(keys: List<String>) {

    private var sources: Map<String, FeedSource> =
        keys.associateWithTo(LinkedHashMap()) { FeedSource(key = it) }

    /** Every source's current position, in the order the sources were declared. */
    val all: List<FeedSource> get() = sources.values.toList()

    /** The sources whose last fetch failed. */
    val failedKeys: List<String> get() = sources.values.filter { it.failed }.map { it.key }

    /** True once every source has reported its last page. */
    val isComplete: Boolean get() = sources.values.all { it.exhausted }

    /**
     * The oldest activity timestamp the merged list is complete down to, or null while a source
     * that can still produce items has not produced any yet.
     *
     * Exhausted sources are excluded: one with nothing left below its tail constrains nothing.
     */
    val watermark: Long?
        get() {
            val live = sources.values.filterNot { it.exhausted }
            if (live.any { it.tail == null }) return null
            return live.mapNotNull { it.tail }.maxOrNull()
        }

    /**
     * The source to page next, or null when nothing can be paged — either because every source
     * has run out (see [isComplete]) or because the ones left have failed.
     */
    fun next(): FeedSource? {
        val candidates = sources.values.filterNot { it.exhausted || it.failed }
        // A source with no tail caps the watermark at "unknown", so it goes ahead of any source
        // that has at least reported where it got to.
        return candidates.firstOrNull { it.tail == null }
            ?: candidates.maxByOrNull { requireNotNull(it.tail) }
    }

    /**
     * Records a page from [key]. [tail] is the oldest last-activity in that page, or null when
     * the page was empty — in which case the source keeps the tail it already had, because
     * forgetting it would restart the source from the top.
     */
    fun advance(key: String, tail: Long?, token: PagingToken?, hasMore: Boolean) {
        val source = sources[key] ?: return
        sources = sources + (
            key to source.copy(
                token = token,
                tail = tail ?: source.tail,
                exhausted = !hasMore,
                failed = false,
            )
            )
    }

    /** Records that [key]'s fetch failed. It keeps its position and is skipped by [next]. */
    fun fail(key: String) {
        val source = sources[key] ?: return
        sources = sources + (key to source.copy(failed = true))
    }

    /** Puts every source back to never-paged, for a refresh. */
    fun reset() {
        sources = sources.mapValues { (key, _) -> FeedSource(key = key) }
    }
}
