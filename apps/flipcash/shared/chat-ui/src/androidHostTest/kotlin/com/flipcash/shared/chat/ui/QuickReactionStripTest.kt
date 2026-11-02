package com.flipcash.shared.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.flipcash.shared.chat.reactions.ReactionStrip
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * [QuickReactionStrip]: the horizontally-scrolling strip a long-press on a reactable, selected
 * bubble shows (decision 5). Every entry a caller hands it renders, self-reacted ones read as
 * selected, and a tap is the one signal that both toggles and (from the caller's side) clears the
 * selection.
 */
@RunWith(RobolectricTestRunner::class)
class QuickReactionStripTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun entry(emoji: String, highlighted: Boolean = false) =
        ReactionStrip.Entry(emoji = emoji, highlighted = highlighted)

    private fun setStrip(
        entries: List<ReactionStrip.Entry>,
        width: androidx.compose.ui.unit.Dp = 200.dp,
        onToggle: (String) -> Unit = {},
        onOpenPicker: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            DesignSystem {
                Box(Modifier.width(width)) {
                    QuickReactionStrip(
                        entries = entries,
                        onToggle = onToggle,
                        onOpenPicker = onOpenPicker,
                    )
                }
            }
        }
    }

    @Test
    fun `every entry renders`() {
        val entries = listOf(entry("❤️"), entry("👍"), entry("😂"))
        setStrip(entries = entries, width = 2000.dp)

        entries.forEach { composeTestRule.onNodeWithTag("quick_reaction_${it.emoji}").assertExists() }
    }

    @Test
    fun `self-reacted entries are marked selected`() {
        setStrip(entries = listOf(entry("❤️", highlighted = true), entry("👍", highlighted = false)))

        composeTestRule.onNodeWithTag("quick_reaction_❤️").assertIsSelected()
        composeTestRule.onNodeWithTag("quick_reaction_👍").assertIsNotSelected()
    }

    @Test
    fun `tapping an entry toggles that emoji`() {
        val toggled = mutableListOf<String>()
        setStrip(entries = listOf(entry("❤️"), entry("👍")), onToggle = { toggled += it })

        composeTestRule.onNodeWithTag("quick_reaction_👍").performClick()

        composeTestRule.runOnIdle { assertEquals(listOf("👍"), toggled) }
    }

    @Test
    fun `plus opens the picker`() {
        var opened = 0
        setStrip(entries = listOf(entry("❤️")), onOpenPicker = { opened++ })

        composeTestRule.onNodeWithTag("quick_reaction_plus").performClick()

        composeTestRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun `plus is the size of an emoji circle`() {
        setStrip(entries = listOf(entry("❤️")))

        composeTestRule.onNodeWithTag("quick_reaction_plus")
            .assertWidthIsEqualTo(40.dp)
            .assertHeightIsEqualTo(40.dp)
    }

    @Test
    fun `scrolled to the end, the last entry sits one spacing short of the plus`() {
        val entries = List(11) { entry("😀") } + entry("🔥")
        setStrip(entries = entries, width = 313.dp)

        composeTestRule.onNodeWithTag("quick_reaction_scroll").performTouchInput { swipeLeft() }
        composeTestRule.onNodeWithTag("quick_reaction_scroll").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        val last = composeTestRule.onNodeWithTag("quick_reaction_🔥").getUnclippedBoundsInRoot()
        val plus = composeTestRule.onNodeWithTag("quick_reaction_plus").getUnclippedBoundsInRoot()
        assertEquals(4f, (plus.left - last.right).value, absoluteTolerance = 0.5f)
    }

    @Test
    fun `no fade at either end with nowhere to scroll`() {
        assertEquals(
            emptyList(),
            edgeFadeStops(width = 300f, leadingFade = 20f, trailingFade = 14f, trailingInset = 14f, leading = 0f, trailing = 0f, rtl = false),
        )
    }

    @Test
    fun `the trailing fade ends inside the plus and hides everything past it`() {
        val clear = 1f - 14f / 300
        assertEquals(
            listOf(0f to 1f, 20f / 300 to 1f, clear - 14f / 300 to 1f, clear to 0f, 1f to 0f),
            edgeFadeStops(width = 300f, leadingFade = 20f, trailingFade = 14f, trailingInset = 14f, leading = 0f, trailing = 1f, rtl = false),
        )
    }

    @Test
    fun `the leading fade grows in with the scroll`() {
        assertEquals(
            listOf(0f to 0.5f, 20f / 300 to 1f, (1f - 14f / 300) - 14f / 300 to 1f, 1f - 14f / 300 to 1f, 1f to 1f),
            edgeFadeStops(width = 300f, leadingFade = 20f, trailingFade = 14f, trailingInset = 14f, leading = 0.5f, trailing = 0f, rtl = false),
        )
    }

    @Test
    fun `both fades mirror in RTL`() {
        val ltr = edgeFadeStops(width = 300f, leadingFade = 20f, trailingFade = 14f, trailingInset = 14f, leading = 1f, trailing = 1f, rtl = false)
        val rtl = edgeFadeStops(width = 300f, leadingFade = 20f, trailingFade = 14f, trailingInset = 14f, leading = 1f, trailing = 1f, rtl = true)

        assertEquals(ltr.reversed().map { (at, alpha) -> (1f - at) to alpha }, rtl)
        // The leading edge is the right one, and it fades.
        assertEquals(1f to 0f, rtl.last())
    }

    @Test
    fun `overflowing entries are reachable by horizontal scroll`() {
        // The strip doesn't dedupe entries, so 29 identical filler emoji plus one distinguishable
        // target at the end is enough to force overflow at a narrow width and give the test
        // something unique to scroll to.
        val entries = List(29) { entry("😀") } + entry("🔥")
        setStrip(entries = entries, width = 120.dp)

        composeTestRule.onRoot().performScrollToNode(hasTestTag("quick_reaction_🔥"))

        composeTestRule.onNodeWithTag("quick_reaction_🔥").assertExists()
    }
}
