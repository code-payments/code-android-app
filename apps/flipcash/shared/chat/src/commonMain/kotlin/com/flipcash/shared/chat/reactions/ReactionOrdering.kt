package com.flipcash.shared.chat.reactions

/**
 * The order pills are shown in: boost total descending, then count descending, then the emoji's
 * UTF-8 bytes ascending.
 *
 * The last tiebreak matches the server, which sorts summaries with Go's bytewise string compare
 * (`flipcash2-server` `messaging/dynamodb/store.go:2277`). Kotlin's `String.compareTo` compares by
 * UTF-16 code unit instead, which disagrees with byte order for some multi-scalar emoji, so it
 * must not be used here.
 */
object ReactionOrdering {

    /** Returns pills-display order: negative when [lhs] is shown before [rhs]. */
    val comparator: Comparator<ReactionPill> = Comparator { lhs, rhs ->
        val lhsBoost = lhs.boost?.total ?: 0L
        val rhsBoost = rhs.boost?.total ?: 0L
        if (lhsBoost != rhsBoost) return@Comparator rhsBoost.compareTo(lhsBoost)
        if (lhs.count != rhs.count) return@Comparator rhs.count.compareTo(lhs.count)
        compareUtf8Bytes(lhs.emoji, rhs.emoji)
    }

    /** Returns [pills] in display order. */
    fun sorted(pills: Iterable<ReactionPill>): List<ReactionPill> = pills.sortedWith(comparator)

    /** Compares two emoji by their raw UTF-8 bytes, ascending, matching the server's order. */
    fun compareUtf8Bytes(lhs: String, rhs: String): Int {
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
