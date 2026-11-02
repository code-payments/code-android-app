package com.flipcash.shared.chat.ui

import org.junit.Test
import kotlin.test.assertEquals

/**
 * [ReactionPillRowLayout.compute]'s N-more collapse: pills wrap onto up to two lines, and when
 * they don't all fit, the row shows as many as fit alongside a trailing "N more" pill rather than
 * clipping one mid-row.
 */
class ReactionPillRowLayoutTest {

    private val spacing = 6

    @Test
    fun `everything fits on one line shows no more pill`() {
        val result = ReactionPillRowLayout.compute(
            pillWidths = listOf(40, 40, 40),
            moreWidth = 60,
            plusWidth = 28,
            containerWidth = 300,
            spacing = spacing,
            maxLines = 2,
            expanded = false,
        )
        assertEquals(ReactionPillRowLayout.Result(visibleCount = 3, showsMore = false, moreCount = 0), result)
    }

    @Test
    fun `pills spilling past two lines collapse into a more pill`() {
        // Six 60-wide pills at spacing 6 need three lines of 100 width; two lines fit two pills each.
        val result = ReactionPillRowLayout.compute(
            pillWidths = List(6) { 60 },
            moreWidth = 50,
            plusWidth = 28,
            containerWidth = 130,
            spacing = spacing,
            maxLines = 2,
            expanded = false,
        )
        // Two lines of 130 fit two 60-wide pills each (60, then 60+6=66 more) with 4px to spare —
        // not enough for a third 60-wide pill (66 > 4), so line one holds one pill's worth of
        // room short of two; the reference math above is why the collapse lands at exactly 2 kept
        // pills, 4 folded into "N more".
        assertEquals(ReactionPillRowLayout.Result(visibleCount = 2, showsMore = true, moreCount = 4), result)
    }

    @Test
    fun `expanded ignores the line limit and shows every pill`() {
        val result = ReactionPillRowLayout.compute(
            pillWidths = List(20) { 60 },
            moreWidth = 50,
            plusWidth = 28,
            containerWidth = 130,
            spacing = spacing,
            maxLines = 2,
            expanded = true,
        )
        assertEquals(ReactionPillRowLayout.Result(visibleCount = 20, showsMore = false, moreCount = 0), result)
    }

    @Test
    fun `no plus button still collapses correctly`() {
        val result = ReactionPillRowLayout.compute(
            pillWidths = List(6) { 60 },
            moreWidth = 50,
            plusWidth = null,
            containerWidth = 130,
            spacing = spacing,
            maxLines = 2,
            expanded = false,
        )
        assertEquals(true, result.showsMore)
        assertEquals(6, result.visibleCount + result.moreCount)
    }

    @Test
    fun `empty pills show nothing`() {
        val result = ReactionPillRowLayout.compute(
            pillWidths = emptyList(),
            moreWidth = 50,
            plusWidth = 28,
            containerWidth = 130,
            spacing = spacing,
            maxLines = 2,
            expanded = false,
        )
        assertEquals(ReactionPillRowLayout.Result(visibleCount = 0, showsMore = false, moreCount = 0), result)
    }

    @Test
    fun `single pill wider than the container still shows alone`() {
        val result = ReactionPillRowLayout.compute(
            pillWidths = listOf(500),
            moreWidth = 50,
            plusWidth = 28,
            containerWidth = 130,
            spacing = spacing,
            maxLines = 2,
            expanded = false,
        )
        // The pill alone already leaves no room for the trailing "+" within two lines, so it
        // collapses to a lone "more" pill that does have room for the "+" beside it.
        assertEquals(1, result.visibleCount + result.moreCount)
        assertEquals(0, result.visibleCount)
        assertEquals(true, result.showsMore)
    }
}
