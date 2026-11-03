package com.getcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.DesignSystem
import androidx.compose.ui.graphics.toPixelMap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals

/**
 * The leading side added to [SwipeActionRow]: a full swipe right fires the leading action once
 * and, for an action that resets, sends the row home; a row with only trailing actions does not
 * move on a swipe right.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SwipeActionRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var leadingFired = 0
    private var trailingFired = 0
    private var labelWidth = 0.dp
    private var circleRevealWidth = 0.dp
    private var pillRevealWidth = 0.dp

    private fun show(withLeading: Boolean, leadingLabel: String? = null) {
        composeTestRule.setContent {
            DesignSystem {
                // What the row should open to: the label's width (when wider than the circle)
                // plus the inset on each side, measured with the style the row draws it in.
                val density = LocalDensity.current
                val measurer = rememberTextMeasurer()
                val style = CodeTheme.typography.caption
                labelWidth = leadingLabel?.let {
                    with(density) { measurer.measure(it, style, maxLines = 1).size.width.toDp() }
                } ?: 0.dp
                circleRevealWidth = CodeTheme.dimens.staticGrid.x10 + CodeTheme.dimens.inset * 2
                // A labelled action is a pill 1.4 times the circle's width.
                pillRevealWidth = CodeTheme.dimens.staticGrid.x10 * 1.4f + CodeTheme.dimens.inset * 2
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
                                    label = leadingLabel,
                                ) {
                                    Box(
                                        Modifier.fillMaxSize().semantics {
                                            contentDescription = LEADING_DESCRIPTION
                                        }
                                    )
                                }
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

    // The device path again, on a labelled action: a slow drag opens the row wide enough for the
    // label, the circle keeps its content description and the label adds no node of its own, a
    // tap on the revealed action fires it, and a full swipe still fires and comes home.
    @Test
    fun `a labelled leading action opens to fit its label and still fires`() {
        show(withLeading = true, leadingLabel = LABEL)
        assert(labelWidth > 70.dp) { "the test label ($labelWidth) should be wider than the pill" }

        composeTestRule.onNodeWithTag(ROW).performTouchInput {
            // Past half the reveal, short of a full swipe.
            swipeRight(startX = left + 1f, endX = left + labelWidth.toPx(), durationMillis = 1_500)
        }
        composeTestRule.waitForIdle()
        val revealed = rowLeft()
        assert(revealed > circleRevealWidth) { "revealed $revealed should be wider than the circle-only $circleRevealWidth" }
        assert(revealed >= labelWidth) { "revealed $revealed should fit the label $labelWidth" }
        assertEquals(0, leadingFired)

        composeTestRule.onNodeWithContentDescription(LEADING_DESCRIPTION).assertExists()
        composeTestRule.onAllNodesWithText(LABEL).assertCountEquals(0)

        composeTestRule.onNodeWithContentDescription(LEADING_DESCRIPTION).performTouchInput { click() }
        composeTestRule.waitForIdle()
        assertEquals(1, leadingFired)
        assertEquals(0.dp, rowLeft())

        composeTestRule.onNodeWithTag(ROW).performTouchInput {
            swipeRight(startX = left + 1f, endX = left + 60.dp.toPx(), durationMillis = 1_500)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ROW).performTouchInput {
            swipeRight(startX = left + 1f, endX = right, durationMillis = 120)
        }
        composeTestRule.waitForIdle()

        assertEquals(2, leadingFired)
        assertEquals(0, trailingFired)
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

    // The chat list's leading title is short, so a labelled leading action opens to the pill's
    // width (1.4 times the circle, as iOS draws a titled action) and no wider, and its swipe still
    // fires on the real gesture sequence.
    @Test
    fun `a short label opens the reveal to the pill width`() {
        show(withLeading = true, leadingLabel = SHORT_LABEL)
        assert(labelWidth <= 70.dp) { "the short label ($labelWidth) should fit inside the pill" }

        composeTestRule.onNodeWithTag(ROW).performTouchInput {
            swipeRight(startX = left + 1f, endX = left + 80.dp.toPx(), durationMillis = 1_500)
        }
        composeTestRule.waitForIdle()
        val revealed = rowLeft()
        assert(revealed > circleRevealWidth) { "revealed $revealed should be wider than the circle-only $circleRevealWidth" }
        assertEquals(pillRevealWidth.value, revealed.value, 1f)
        assertEquals(0, leadingFired)

        composeTestRule.onNodeWithTag(ROW).performTouchInput {
            swipeRight(startX = left + 1f, endX = right, durationMillis = 120)
        }
        composeTestRule.waitForIdle()
        assertEquals(1, leadingFired)
        assertEquals(0.dp, rowLeft())
    }

    // A slow drag past the reveal toward a full swipe: the inner action fades with the offset, from
    // full at the reveal to nearly gone at the point a release would commit, and is back to full
    // once the row is released short of that point and settles revealed.
    @Test
    fun `the inner trailing action fades as a full swipe commits and returns on release`() {
        var revealWidth = 0.dp
        composeTestRule.setContent {
            DesignSystem {
                revealWidth = CodeTheme.dimens.staticGrid.x10 * 2 + CodeTheme.dimens.inset * 3
                Box(Modifier.fillMaxWidth().background(Color.Black)) {
                    SwipeActionRow(
                        actions = listOf(
                            SwipeAction(background = Color.Red, onTriggered = {}) {
                                Box(Modifier.fillMaxSize().semantics { contentDescription = INNER })
                            },
                            SwipeAction(background = Color.Gray, onTriggered = { trailingFired++ }) {},
                        ),
                    ) {
                        Box(Modifier.fillMaxWidth().height(72.dp).testTag(ROW))
                    }
                }
            }
        }
        // Releasing past halfway from the reveal to the row's width commits the full swipe.
        val rowWidth = composeTestRule.onNodeWithTag(ROW).getUnclippedBoundsInRoot().let { it.right - it.left }
        val threshold = revealWidth + (rowWidth - revealWidth) / 2
        fun innerRed(): Float {
            val pixels = composeTestRule.onNodeWithContentDescription(INNER).captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2].red
        }
        fun opened() = -rowLeft()
        // A slow drag in small steps, steered by where the row actually is: injected moves can
        // land a frame late, so each step is sized from the measured offset. The first step also
        // covers the touch slop. Moved by deltas because the node's bounds move with the row.
        var started = false
        fun openTo(open: Dp) {
            repeat(200) {
                val remaining = open - opened()
                if (abs(remaining.value) < 1f) return
                val step = remaining.coerceIn(-4.dp, 4.dp)
                composeTestRule.onNodeWithTag(ROW).performTouchInput {
                    val slop = if (started) 0f else viewConfiguration.touchSlop
                    moveBy(androidx.compose.ui.geometry.Offset(-(slop + step.toPx()), 0f))
                }
                started = true
                composeTestRule.waitForIdle()
            }
        }

        composeTestRule.onNodeWithTag(ROW).performTouchInput { down(androidx.compose.ui.geometry.Offset(right - 1f, centerY)) }
        openTo(revealWidth)
        composeTestRule.waitForIdle()
        assertEquals(revealWidth.value, opened().value, 2f)
        assert(innerRed() > 0.95f) { "at the reveal the inner action should be opaque, red ${innerRed()}" }

        val midway = revealWidth + (threshold - revealWidth) / 2
        openTo(midway)
        composeTestRule.waitForIdle()
        // Linear in how far past the reveal the row is, measured on the row itself.
        val expected = 1f - (opened() - revealWidth) / (threshold - revealWidth)
        assert(expected in 0.2f..0.8f) { "midway drag opened ${opened()}, expected alpha $expected" }
        assertEquals(expected, innerRed(), 0.05f)

        openTo(threshold - 2.dp)
        composeTestRule.waitForIdle()
        assertEquals((threshold - 2.dp).value, opened().value, 2f)
        assert(innerRed() < 0.1f) { "near the threshold the inner action should be nearly gone, red ${innerRed()}" }

        // Back short of the threshold and let go: the row settles revealed, the action opaque again.
        openTo(revealWidth + 10.dp)
        composeTestRule.onNodeWithTag(ROW).performTouchInput { up() }
        composeTestRule.waitForIdle()
        assertEquals(revealWidth.value, opened().value, 2f)
        assert(innerRed() > 0.95f) { "after release the inner action should be opaque, red ${innerRed()}" }
        assertEquals(0, trailingFired)
    }

    private companion object {
        const val ROW = "row"
        // Wider than the circle, as the Members row's titles are.
        const val LABEL = "Lift Restriction"
        // The chat list's leading title: no wider than the circle.
        const val SHORT_LABEL = "Unread"
        const val LEADING_DESCRIPTION = "Mark chat as unread"
        const val INNER = "Inner action"
    }
}
