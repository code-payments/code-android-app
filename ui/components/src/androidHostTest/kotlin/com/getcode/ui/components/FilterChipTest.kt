package com.getcode.ui.components

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class FilterChipTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `selected state is exposed to accessibility`() {
        composeTestRule.setContent {
            DesignSystem {
                FilterChip(label = "Unread", selected = true, onClick = {})
            }
        }
        composeTestRule.onNodeWithText("Unread", useUnmergedTree = false).assertIsSelected()
    }

    @Test
    fun `an unselected chip is not selected`() {
        composeTestRule.setContent {
            DesignSystem {
                FilterChip(label = "Groups", selected = false, onClick = {})
            }
        }
        composeTestRule.onNodeWithText("Groups").assertIsNotSelected()
    }

    @Test
    fun `clicking invokes onClick`() {
        var clicks = 0
        composeTestRule.setContent {
            DesignSystem {
                FilterChip(label = "All", selected = false, onClick = { clicks++ })
            }
        }
        composeTestRule.onNodeWithText("All").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `a count is drawn after the label and a zero count is not drawn`() {
        composeTestRule.setContent {
            DesignSystem {
                FilterChip(label = "Unread", selected = false, onClick = {}, count = 5)
                FilterChip(label = "Groups", selected = false, onClick = {}, count = 0)
            }
        }
        composeTestRule.onNodeWithText("5").assertExists()
        composeTestRule.onNodeWithText("0").assertDoesNotExist()
    }
}
