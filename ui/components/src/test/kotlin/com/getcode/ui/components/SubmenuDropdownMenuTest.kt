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

/** Opening Moderation turns the menu into the submenu; its header turns it back. */
@RunWith(RobolectricTestRunner::class)
class SubmenuDropdownMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setMenu() = composeTestRule.setContent {
        DesignSystem {
            SubmenuDropdownMenu(expanded = true, onDismissRequest = {}) {
                Item { DropdownMenuItem(text = { Text("Mute Notifications") }, onClick = {}) }
                Submenu(label = "Moderation", icon = Icons.Outlined.Shield) {
                    DropdownMenuItem(text = { Text("Restricted") }, onClick = {})
                    DropdownMenuItem(text = { Text("Removed") }, onClick = {})
                }
                Item { DropdownMenuItem(text = { Text("Report") }, onClick = {}) }
            }
        }
    }

    @Test
    fun `the first level shows its rows and not the submenu`() {
        setMenu()

        composeTestRule.onNodeWithText("Mute Notifications").assertExists()
        composeTestRule.onNodeWithText("Report").assertExists()
        composeTestRule.onNodeWithText("Restricted").assertDoesNotExist()
    }

    @Test
    fun `opening the submenu leaves only its header and rows`() {
        setMenu()

        composeTestRule.onNodeWithText("Moderation").performClick()

        composeTestRule.onNodeWithText("Moderation").assertExists()
        composeTestRule.onNodeWithText("Restricted").assertExists()
        composeTestRule.onNodeWithText("Removed").assertExists()
        composeTestRule.onNodeWithText("Mute Notifications").assertDoesNotExist()
        composeTestRule.onNodeWithText("Report").assertDoesNotExist()
    }

    @Test
    fun `the header turns the menu back`() {
        setMenu()
        composeTestRule.onNodeWithText("Moderation").performClick()

        composeTestRule.onNodeWithText("Moderation").performClick()

        composeTestRule.onNodeWithText("Mute Notifications").assertExists()
        composeTestRule.onNodeWithText("Restricted").assertDoesNotExist()
    }
}
