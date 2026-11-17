package com.flipcash.app.menu.internal

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.flipcash.features.menu.R
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * You's top bar. As a tab it holds only the Settings gear; drawn without the tab bar (the Group Money
 * shell has no tab for You) it leads with a close, as iOS's You does.
 */
@RunWith(RobolectricTestRunner::class)
class ProfileBarButtonCloseTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // The bar composes its leading control a second time, unplaced, to measure it (TopAppBarBase's
    // "leftIcon_probe"), so only the placed copy is the one on screen.
    private val placed = SemanticsMatcher("is placed") { it.layoutInfo.isPlaced }

    private fun closeButtons() = composeTestRule.onAllNodesWithTag("action_close").filter(placed)

    private fun setBar(onClose: (() -> Unit)?) {
        composeTestRule.setContent {
            DesignSystem {
                ProfileBarButton(
                    icon = R.drawable.ic_settings_outline,
                    contentDescription = "Settings",
                    onClick = {},
                    testTag = "you-settings",
                    onClose = onClose,
                )
            }
        }
    }

    @Test
    fun `You as a tab draws no close`() {
        setBar(onClose = null)

        composeTestRule.onNodeWithTag("you-settings").assertIsDisplayed()
        closeButtons().assertCountEquals(0)
    }

    @Test
    fun `You off the tab bar leads with a close that leaves it`() {
        var closes = 0
        setBar(onClose = { closes++ })

        val close = composeTestRule.onAllNodesWithTag("action_close").filterToOne(placed).assertIsDisplayed()
        val gear = composeTestRule.onNodeWithTag("you-settings").assertIsDisplayed()
        assertTrue(close.getBoundsInRoot().left < gear.getBoundsInRoot().left)

        close.performClick()
        assertEquals(1, closes)
    }
}
