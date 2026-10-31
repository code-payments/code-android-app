package com.getcode.libs.emojis.reactions

import kotlin.time.Instant

/** Ranks the emoji the strip and picker offer first, from how often and how recently each was used. */
object RecentReactions {

    /** The emoji offered before any have been used, in their order. */
    val DEFAULTS: List<String> = listOf("❤️", "👍", "😂", "😮", "😢", "🔥")

    /** How often and how recently one emoji was reacted with. */
    data class Usage(val count: Int, val lastUsed: Instant)

    /** Records one use of [emoji] at [date] in [stats], returning the updated map. */
    fun record(emoji: String, at: Instant, stats: Map<String, Usage>): Map<String, Usage> {
        val existing = stats[emoji]
        val updated = if (existing != null) {
            existing.copy(count = existing.count + 1, lastUsed = maxOf(existing.lastUsed, at))
        } else {
            Usage(count = 1, lastUsed = at)
        }
        return stats + (emoji to updated)
    }

    /**
     * Returns up to [limit] emoji: the used ones by count descending, then last use descending,
     * padded with [DEFAULTS], leaving out any in [undrawable].
     *
     * Padding stops at the defaults, so an unused picker asking for more than six gets six.
     */
    fun rank(stats: Map<String, Usage>, undrawable: Set<String>, limit: Int): List<String> {
        val ranked = stats.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Usage>> { it.value.count }
                    .thenByDescending { it.value.lastUsed }
                    // Only for a map's lack of order; two uses at the same instant do not happen.
                    .thenComparator { a, b -> compareUtf8Bytes(a.key, b.key) },
            )
            .map { it.key }
            .filter { it !in undrawable }
            .toMutableList()
        for (emoji in DEFAULTS) {
            if (emoji !in ranked && emoji !in undrawable) {
                ranked.add(emoji)
            }
        }
        return ranked.take(maxOf(limit, 0))
    }

    private fun compareUtf8Bytes(lhs: String, rhs: String): Int {
        val a = lhs.encodeToByteArray()
        val b = rhs.encodeToByteArray()
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            val cmp = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (cmp != 0) return cmp
        }
        return a.size - b.size
    }
}
