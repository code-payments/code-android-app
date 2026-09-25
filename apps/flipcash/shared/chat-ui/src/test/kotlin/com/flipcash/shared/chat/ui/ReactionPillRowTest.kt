package com.flipcash.shared.chat.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.flipcash.shared.chat.reactions.ReactionPill
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ReactionPillRow]: the pill row a bubble draws its reaction summary in. Behavior, not pixels —
 * callback wiring, the previewer's read-only contract (decision 2, #17), and the "N more" collapse
 * an narrow bubble forces (the layout math itself is [ReactionPillRowLayoutTest]'s job).
 */
@RunWith(RobolectricTestRunner::class)
class ReactionPillRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun pill(
        emoji: String,
        count: Long = 1,
        selfReacted: Boolean = false,
        pending: Boolean = false,
    ) = ReactionPill(emoji = emoji, count = count, selfReacted = selfReacted, pending = pending)

    private fun setRow(
        pills: List<ReactionPill>,
        canReact: Boolean = true,
        width: androidx.compose.ui.unit.Dp = 400.dp,
        onToggle: (String) -> Unit = {},
        onPillLongClick: () -> Unit = {},
        onOpenPicker: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            DesignSystem {
                Box(Modifier.width(width)) {
                    ReactionPillRow(
                        pills = pills,
                        canReact = canReact,
                        onToggle = onToggle,
                        onPillLongClick = onPillLongClick,
                        onOpenPicker = onOpenPicker,
                    )
                }
            }
        }
    }

    @Test
    fun `a message with no reactions draws no row and no plus, matching iOS`() {
        setRow(pills = emptyList(), canReact = true)

        composeTestRule.onNodeWithTag("reaction_pill_plus").assertDoesNotExist()
    }

    @Test
    fun `plus is a 28dp circle`() {
        setRow(pills = listOf(pill("😀")))

        composeTestRule.onNodeWithTag("reaction_pill_plus")
            .assertWidthIsEqualTo(28.dp)
            .assertHeightIsEqualTo(28.dp)
    }

    @Test
    fun `tapping a pill toggles that emoji`() {
        val toggled = mutableListOf<String>()
        setRow(pills = listOf(pill("😀"), pill("😂")), onToggle = { toggled += it })

        composeTestRule.onNodeWithTag("reaction_pill_😂").performClick()

        composeTestRule.runOnIdle { assertEquals(listOf("😂"), toggled) }
    }

    @Test
    fun `long-pressing a pill only opens reactors, and does not reach a parent long-press`() {
        val toggled = mutableListOf<String>()
        var reactorsOpened = 0
        var parentLongPresses = 0

        composeTestRule.setContent {
            DesignSystem {
                Box(
                    Modifier
                        .width(400.dp)
                        .combinedClickable(onClick = {}, onLongClick = { parentLongPresses++ }),
                ) {
                    ReactionPillRow(
                        pills = listOf(pill("😀"), pill("😂")),
                        canReact = true,
                        onToggle = { toggled += it },
                        onPillLongClick = { reactorsOpened++ },
                        onOpenPicker = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("reaction_pill_😂").performTouchInput { longClick() }

        composeTestRule.runOnIdle {
            assertEquals(1, reactorsOpened)
            assertEquals(emptyList<String>(), toggled)
            assertEquals(0, parentLongPresses)
        }
    }

    @Test
    fun `plus opens the picker`() {
        var opened = 0
        setRow(pills = listOf(pill("😀")), onOpenPicker = { opened++ })

        composeTestRule.onNodeWithTag("reaction_pill_plus").performClick()

        composeTestRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun `a previewer sees pills and can open reactors, but has no plus and taps do nothing`() {
        val toggled = mutableListOf<String>()
        var reactorsOpened = 0
        setRow(
            pills = listOf(pill("😀")),
            canReact = false,
            onToggle = { toggled += it },
            onPillLongClick = { reactorsOpened++ },
        )

        composeTestRule.onNodeWithTag("reaction_pill_plus").assertDoesNotExist()

        composeTestRule.onNodeWithTag("reaction_pill_😀").performClick()
        composeTestRule.onNodeWithTag("reaction_pill_😀").performTouchInput { longClick() }

        composeTestRule.runOnIdle {
            assertTrue(toggled.isEmpty())
            assertEquals(1, reactorsOpened)
        }
    }

    @Test
    fun `a selected pill is exposed as selected in semantics`() {
        setRow(pills = listOf(pill("😀", selfReacted = true), pill("😂", selfReacted = false)))

        composeTestRule.onNodeWithTag("reaction_pill_😀").assertIsSelected()
        composeTestRule.onNodeWithTag("reaction_pill_😂").assertIsNotSelected()
    }

    @Test
    fun `wrapping at a narrow width collapses into an N more pill before plus, which expands on tap`() {
        val pills = listOf(
            pill("😀"), pill("😁"), pill("😂"), pill("😃"),
            pill("😄"), pill("😅"), pill("😆"), pill("😇"),
        )
        setRow(pills = pills, width = 90.dp)

        // Narrow enough that not everything fits on two lines: a collapse pill stands in for the
        // pills pushed off, placed ahead of "+". The row's SubcomposeLayout still subcomposes every
        // pill to measure it, so a collapsed one stays in the semantics tree unplaced — displayed,
        // not existence, is the check that actually distinguishes "shown" from "collapsed away".
        composeTestRule.onNodeWithTag("reaction_pill_more").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reaction_pill_😇").assertIsNotDisplayed()

        composeTestRule.onNodeWithTag("reaction_pill_more").performClick()

        composeTestRule.runOnIdle {
            composeTestRule.onNodeWithTag("reaction_pill_more").assertDoesNotExist()
            composeTestRule.onNodeWithTag("reaction_pill_😇").assertIsDisplayed()
        }
    }
}
