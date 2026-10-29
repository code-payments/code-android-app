package com.getcode.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Moderation opens its rows in place, under itself, and keeps the rest of the menu. */
@RunWith(RobolectricTestRunner::class)
class ExpandableMenuRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setMenu() = composeTestRule.setContent {
        DesignSystem {
            androidx.compose.foundation.layout.Column {
                DropdownMenuItem(text = { Text("Mute Notifications") }, onClick = {})
                ExpandableMenuRow(label = "Moderation", icon = Icons.Outlined.Shield) {
                    DropdownMenuItem(text = { Text("Restricted") }, onClick = {})
                    DropdownMenuItem(text = { Text("Removed") }, onClick = {})
                }
            }
        }
    }

    @Test
    fun `the row starts collapsed`() {
        setMenu()

        composeTestRule.onNodeWithText("Moderation").assertExists()
        composeTestRule.onNodeWithText("Restricted").assertDoesNotExist()
    }

    @Test
    fun `tapping the row shows its rows and keeps the rest of the menu`() {
        setMenu()

        composeTestRule.onNodeWithText("Moderation").performClick()

        composeTestRule.onNodeWithText("Restricted").assertExists()
        composeTestRule.onNodeWithText("Removed").assertExists()
        composeTestRule.onNodeWithText("Mute Notifications").assertExists()
    }

    @Test
    fun `tapping the row again folds it`() {
        setMenu()
        composeTestRule.onNodeWithText("Moderation").performClick()

        composeTestRule.onNodeWithText("Moderation").performClick()

        composeTestRule.onNodeWithText("Restricted").assertDoesNotExist()
    }
}
