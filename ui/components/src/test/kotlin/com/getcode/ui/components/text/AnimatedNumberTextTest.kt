package com.getcode.ui.components.text

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The roller inside [AnimatedNumberText] is only mounted once a digit has actually changed, because
 * `AnimatedContent` costs several times the `Text` it wraps to compose and a wallet full of numbers
 * pays that before its first frame. These cover what that defers: the digit still has to roll when
 * the number moves, including the very first time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class AnimatedNumberTextTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `renders the value`() {
        composeRule.setContent { AnimatedNumberText(value = "1,234") }
        composeRule.waitForIdle()

        "1,234".toSet().forEach { char ->
            assertTrue(
                composeRule.onAllNodesWithText(char.toString()).fetchSemanticsNodes().isNotEmpty(),
                "expected '$char' on screen",
            )
        }
    }

    /**
     * The one the deferral could plausibly break: on the first change there is no roller yet, so it
     * has to mount showing the outgoing digit and animate from there rather than snapping to the new
     * one. Mid-roll both digits are on screen — the outgoing one is still fading out.
     */
    @Test
    fun `the first change rolls`() = assertRolls(from = "0", to = "1", changesBefore = 0)

    /** And the mounted roller keeps working for later changes. */
    @Test
    fun `a later change rolls`() = assertRolls(from = "0", to = "2", changesBefore = 1)

    private fun assertRolls(from: String, to: String, changesBefore: Int) {
        var value by mutableStateOf(from)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { AnimatedNumberText(value = value) }
        composeRule.mainClock.advanceTimeByFrame()

        repeat(changesBefore) { index ->
            composeRule.runOnUiThread { value = "${index + 5}" }
            // Well past the roll, so the next change starts from a settled roller.
            repeat(FramesPerRoll) { composeRule.mainClock.advanceTimeByFrame() }
            composeRule.runOnUiThread { value = from }
            repeat(FramesPerRoll) { composeRule.mainClock.advanceTimeByFrame() }
        }

        composeRule.runOnUiThread { value = to }

        // The roller needs a couple of compositions to mount and start; then the outgoing digit is on
        // screen alongside the incoming one for as long as it is fading. Pump until both are up rather
        // than guessing which frame that is.
        var framesToOverlap = -1
        repeat(FramesPerRoll) { frame ->
            composeRule.mainClock.advanceTimeByFrame()
            val showsBoth = composeRule.onAllNodesWithText(from).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(to).fetchSemanticsNodes().isNotEmpty()
            if (showsBoth && framesToOverlap < 0) framesToOverlap = frame
        }
        assertTrue(
            framesToOverlap >= 0,
            "'$from' never overlapped '$to' — the digit snapped instead of rolling",
        )

        // And it settles on the new digit alone.
        repeat(FramesPerRoll) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals(
            0,
            composeRule.onAllNodesWithText(from).fetchSemanticsNodes().size,
            "'$from' is still on screen after the roll finished",
        )
        assertEquals(
            1,
            composeRule.onAllNodesWithText(to).fetchSemanticsNodes().size,
            "expected exactly one '$to' after the roll finished",
        )
    }

    private companion object {
        /** Frames at 16ms covering the whole roll, with room to spare. */
        const val FramesPerRoll = 60
    }
}
