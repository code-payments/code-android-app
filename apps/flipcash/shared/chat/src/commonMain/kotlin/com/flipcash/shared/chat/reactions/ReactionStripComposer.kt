package com.flipcash.shared.chat.reactions

import com.getcode.libs.emojis.reactions.RecentReactions

/**
 * Composes the long-press strip's 12 emoji (the "+" that opens the picker is the UI's, not this
 * function's, thirteenth slot).
 *
 * Mirrors iOS's app-layer composition over the vector-exact `ReactionStrip.entries`: fixed
 * defaults (minus undrawable), then up to six most-used beyond the defaults, then every emoji the
 * user reacted with on this message (always shown, even past the cap — `ReactionStrip.entries`
 * itself guarantees that), then filler from the catalog's first category up to 12 total.
 */
object ReactionStripComposer {
    private const val TARGET_SIZE = 12
    private const val MOST_USED_SLOTS = 6

    /**
     * @param recentStats this device's recorded per-emoji use, as [RecentReactions.rank] takes it.
     * @param selfReactions the emoji the user reacted to this message with.
     * @param catalogFillSource the fill source's emoji, in the catalog's own order (the first
     *   category, per decision 1 — the caller resolves that; this function only fills from it).
     * @param undrawable emoji this device cannot draw as one glyph, left out everywhere.
     */
    fun compose(
        recentStats: Map<String, RecentReactions.Usage>,
        selfReactions: List<SelfReaction>,
        catalogFillSource: List<String>,
        undrawable: Set<String> = emptySet(),
    ): List<ReactionStrip.Entry> {
        val fixed = RecentReactions.DEFAULTS.filter { it !in undrawable }

        val mostUsed = RecentReactions.rank(recentStats, undrawable, fixed.size + MOST_USED_SLOTS)
            .filter { it !in fixed }
            .take(MOST_USED_SLOTS)

        val entries = ReactionStrip.entries(fixed + mostUsed, selfReactions).toMutableList()
        if (entries.size >= TARGET_SIZE) return entries.take(TARGET_SIZE)

        val present = entries.mapTo(mutableSetOf()) { it.emoji }
        val selfEmoji = selfReactions.map { it.emoji }.toSet()

        for (emoji in catalogFillSource) {
            if (entries.size >= TARGET_SIZE) break
            if (emoji in undrawable || emoji in present) continue
            entries.add(ReactionStrip.Entry(emoji = emoji, highlighted = emoji in selfEmoji))
            present.add(emoji)
        }

        return entries.take(TARGET_SIZE)
    }
}
