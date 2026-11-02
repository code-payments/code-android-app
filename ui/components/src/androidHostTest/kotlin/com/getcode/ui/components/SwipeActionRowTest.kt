package com.getcode.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * The leading side added to [SwipeActionRow]: a full swipe right fires the leading action once
 * and, for an action that resets, sends the row home; a row with only trailing actions does not
 * move on a swipe right.
 */
@RunWith(RobolectricTestRunner::class)
class SwipeActionRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var leadingFired = 0
    private var trailingFired = 0

    private fun show(withLeading: Boolean) {
        composeTestRule.setContent {
            DesignSystem {
                Box(Modifier.width(360.dp)) {
                    SwipeActionRow(
                        actions = listOf(
                            SwipeAction(background = Color.Gray, onTriggered = { trailingFired++ }) {},
                        ),
                        leadingActions = if (withLeading) {
                            listOf(
                                SwipeAction(
                                    background = Color.Blue,
                                    onTriggered = { leadingFired++ },
                                    resetOnDismiss = true,
                                ) {}
                            )
                        } else {
                            emptyList()
                        },
                    ) {
                        Box(Modifier.fillMaxWidth().height(72.dp).testTag(ROW))
                    }
                }
            }
        }
    }

    private fun rowLeft() = composeTestRule.onNodeWithTag(ROW).getUnclippedBoundsInRoot().left

    @Test
    fun `a full leading swipe fires once and the row comes home`() {
        show(withLeading = true)

        composeTestRule.onNodeWithTag(ROW).performTouchInput { swipeRight(startX = left + 1f, endX = right) }
        composeTestRule.waitForIdle()

        assertEquals(1, leadingFired)
        assertEquals(0, trailingFired)
        assertEquals(0.dp, rowLeft())
    }

    // The device path: a short drag leaves the row revealed, then a fast full swipe fires it.
    @Test
    fun `a full leading swipe from the revealed row fires once and the row comes home`() {
        show(withLeading = true)

        composeTestRule.onNodeWithTag(ROW).performTouchInput {
            swipeRight(startX = left + 1f, endX = left + 60.dp.toPx(), durationMillis = 1_500)
        }
        composeTestRule.waitForIdle()
        assert(rowLeft() > 0.dp) { "the short drag should leave the row revealed" }
        assertEquals(0, leadingFired)

        composeTestRule.onNodeWithTag(ROW).performTouchInput {
            swipeRight(startX = left + 1f, endX = right, durationMillis = 120)
        }
        composeTestRule.waitForIdle()

        assertEquals(1, leadingFired)
        assertEquals(0.dp, rowLeft())
    }

    @Test
    fun `a row with only trailing actions does not move on a swipe right`() {
        show(withLeading = false)

        composeTestRule.onNodeWithTag(ROW).performTouchInput { swipeRight(startX = left + 1f, endX = right) }
        composeTestRule.waitForIdle()

        assertEquals(0, leadingFired)
        assertEquals(0, trailingFired)
        assertEquals(0.dp, rowLeft())
    }

    private companion object {
        const val ROW = "row"
    }
}
