package com.flipcash.app.bills

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * [OnBillPresented] is the guard that keeps [BillOverlay]'s keyboard dismissal from firing on a
 * copy of the overlay that mounts with a bill already up — which is what a screen opened *while*
 * a bill is presented does, the overlay being hosted per navigation entry.
 */
@RunWith(RobolectricTestRunner::class)
class OnBillPresentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var presented by mutableStateOf(false)
    private var recomposeTrigger by mutableIntStateOf(0)
    private var presentations = 0

    private fun watch(initiallyPresented: Boolean) {
        presented = initiallyPresented
        composeTestRule.setContent {
            // Read something unrelated so a test can force a recomposition without touching
            // `presented` — assigning the same value to snapshot state wouldn't invalidate.
            @Suppress("UNUSED_EXPRESSION")
            recomposeTrigger
            OnBillPresented(presented) { presentations++ }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `a bill already up at first composition is not a presentation`() {
        watch(initiallyPresented = true)

        // The post-tip hand-off case: LaunchChat opens the chat with the keyboard up on purpose,
        // and the chat's fresh overlay must not take it straight back down.
        assertEquals(0, presentations)
    }

    @Test
    fun `a bill appearing while watching is a presentation`() {
        watch(initiallyPresented = false)

        composeTestRule.runOnIdle { presented = true }
        composeTestRule.waitForIdle()

        assertEquals(1, presentations)
    }

    @Test
    fun `recomposing while the bill stays up does not present again`() {
        watch(initiallyPresented = false)
        composeTestRule.runOnIdle { presented = true }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle { recomposeTrigger++ }
        composeTestRule.waitForIdle()

        assertEquals(1, presentations)
    }

    @Test
    fun `a bill dismissed and presented again is a second presentation`() {
        watch(initiallyPresented = false)

        composeTestRule.runOnIdle { presented = true }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { presented = false }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { presented = true }
        composeTestRule.waitForIdle()

        assertEquals(2, presentations)
    }
}
