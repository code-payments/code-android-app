package com.flipcash.shared.chat.reactions

/** The emoji the long-press strip offers, before the trailing "+" the UI adds. */
object ReactionStrip {

    /** One emoji in the strip. */
    data class Entry(
        val emoji: String,
        /** True when the user has reacted to the message with this emoji. */
        val highlighted: Boolean,
    )

    /**
     * Returns the recents in rank order, highlighting the ones the user reacted with, followed by
     * the user's other reactions, newest first.
     */
    fun entries(recents: List<String>, selfReactions: List<SelfReaction>): List<Entry> {
        val mine = selfReactions.map { it.emoji }.toSet()
        val recentSet = recents.toSet()
        // Indexed so equal times keep their input order, as the reference's stable sort does.
        val extra = selfReactions.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<SelfReaction>> { it.value.reactedAt }
                    .thenBy { it.index },
            )
            .map { it.value.emoji }
            .filter { it !in recentSet }
        return recents.map { Entry(emoji = it, highlighted = it in mine) } +
            extra.map { Entry(emoji = it, highlighted = true) }
    }
}
