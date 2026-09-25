package com.flipcash.shared.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
