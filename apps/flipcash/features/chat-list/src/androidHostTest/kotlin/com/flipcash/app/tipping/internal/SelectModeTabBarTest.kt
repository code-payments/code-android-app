package com.flipcash.app.tipping.internal

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.navigation.LocalTabBarPadding
import com.flipcash.app.core.navigation.LocalTabBarVisibility
import com.flipcash.app.core.navigation.TabBarVisibilityController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Select mode takes the tab bar away and gives its space to the list: the bar hides for as long as
 * the mode lasts, and the list's bottom inset eases to and from zero rather than snapping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SelectModeTabBarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val barHeight = 80.dp

    private fun setUp(selecting: MutableState<Boolean>): Pair<TabBarVisibilityController, () -> Dp> {
        val controller = TabBarVisibilityController()
        var inset = Dp.Unspecified
        composeRule.setContent {
            CompositionLocalProvider(
                LocalTabBarVisibility provides controller,
                LocalTabBarPadding provides PaddingValues(bottom = barHeight),
            ) {
                inset = selectModeTabBarInset(selecting.value).value
            }
        }
        return controller to { inset }
    }

    @Test
    fun selectModeHidesTheBarUntilItEnds() {
        val selecting = mutableStateOf(false)
        val (controller, _) = setUp(selecting)
        assertFalse(controller.isHidden)

        selecting.value = true
        composeRule.waitForIdle()
        assertTrue(controller.isHidden)

        selecting.value = false
        composeRule.waitForIdle()
        assertFalse(controller.isHidden)
    }

    @Test
    fun insetEasesToZeroAndBack() {
        val selecting = mutableStateOf(false)
        val (_, inset) = setUp(selecting)
        assertEquals(barHeight, inset())

        composeRule.mainClock.autoAdvance = false
        selecting.value = true
        // The paused clock does not flush the write for us.
        Snapshot.sendApplyNotifications()
        // A few frames into the spring: past the frame the new target lands on, well short of rest.
        composeRule.mainClock.advanceTimeBy(64)
        val mid = inset()
        assertTrue(mid > 0.dp && mid < barHeight, "inset should be mid-animation, was $mid")

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(0.dp, inset())

        selecting.value = false
        composeRule.waitForIdle()
        assertEquals(barHeight, inset())
    }
}
