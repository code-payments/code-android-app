package com.flipcash.shared.chat.reactions

import com.getcode.libs.emojis.reactions.RecentReactions
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReactionStripComposerTest {

    private val catalogFillSource = listOf("😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "😉", "😊", "😇", "😍")

    @Test
    fun `with no history the strip is the six defaults padded from the catalog`() {
        val entries = ReactionStripComposer.compose(
            recentStats = emptyMap(),
            selfReactions = emptyList(),
            catalogFillSource = catalogFillSource,
        )

        assertEquals(12, entries.size)
        assertEquals(RecentReactions.DEFAULTS, entries.take(6).map { it.emoji })
        assertTrue(entries.none { it.highlighted })
        // The fill is catalog emoji not already offered, in the catalog's own order.
        val fill = entries.drop(6).map { it.emoji }
        assertEquals(catalogFillSource.filter { it !in RecentReactions.DEFAULTS }.take(6), fill)
    }

    @Test
    fun `undrawable defaults are dropped, not replaced by a most-used or filler emoji in their slot`() {
        val undrawable = setOf(RecentReactions.DEFAULTS[1])

        val entries = ReactionStripComposer.compose(
            recentStats = emptyMap(),
            selfReactions = emptyList(),
            catalogFillSource = catalogFillSource,
            undrawable = undrawable,
        )

        assertTrue(entries.none { it.emoji in undrawable })
        assertEquals(RecentReactions.DEFAULTS.filter { it !in undrawable }, entries.take(5).map { it.emoji })
    }

    @Test
    fun `most-used emoji beyond the defaults fill the next six slots, ranked`() {
        val stats = mapOf(
            "🥳" to RecentReactions.Usage(count = 10, lastUsed = Instant.fromEpochSeconds(100)),
            "🤔" to RecentReactions.Usage(count = 5, lastUsed = Instant.fromEpochSeconds(200)),
        )

        val entries = ReactionStripComposer.compose(
            recentStats = stats,
            selfReactions = emptyList(),
            catalogFillSource = catalogFillSource,
        )

        val afterDefaults = entries.drop(6).map { it.emoji }
        assertEquals(listOf("🥳", "🤔"), afterDefaults.take(2))
    }

    @Test
    fun `a self reaction outside the defaults and most-used is always included, highlighted`() {
        val entries = ReactionStripComposer.compose(
            recentStats = emptyMap(),
            selfReactions = listOf(SelfReaction(emoji = "🦄", reactedAt = Instant.fromEpochSeconds(1))),
            catalogFillSource = catalogFillSource,
        )

        val unicorn = entries.firstOrNull { it.emoji == "🦄" }
        assertTrue(unicorn != null, "self reaction was dropped from the strip")
        assertTrue(unicorn.highlighted)
    }

    @Test
    fun `never returns more than 12 entries`() {
        val manyStats = (1..20).associate { "emoji$it" to RecentReactions.Usage(count = it, lastUsed = Instant.fromEpochSeconds(it.toLong())) }
        val manySelf = (1..20).map { SelfReaction(emoji = "self$it", reactedAt = Instant.fromEpochSeconds(it.toLong())) }

        val entries = ReactionStripComposer.compose(
            recentStats = manyStats,
            selfReactions = manySelf,
            catalogFillSource = catalogFillSource,
        )

        assertEquals(12, entries.size)
    }

    @Test
    fun `a default already ranked as most-used is not duplicated`() {
        val stats = mapOf(RecentReactions.DEFAULTS[0] to RecentReactions.Usage(count = 100, lastUsed = Instant.fromEpochSeconds(1)))

        val entries = ReactionStripComposer.compose(
            recentStats = stats,
            selfReactions = emptyList(),
            catalogFillSource = catalogFillSource,
        )

        assertEquals(1, entries.count { it.emoji == RecentReactions.DEFAULTS[0] })
    }
}
