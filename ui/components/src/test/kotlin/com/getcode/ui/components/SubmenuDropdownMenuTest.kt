package com.getcode.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
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

/** The Moderation menu swaps its items in place: Material 3 has no submenu. */
@RunWith(RobolectricTestRunner::class)
class SubmenuDropdownMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setMenu(onDismiss: () -> Unit = {}) = composeTestRule.setContent {
        DesignSystem {
            SubmenuDropdownMenu(
                expanded = true,
                onDismissRequest = onDismiss,
                submenuTitle = "Moderation",
                root = { open ->
                    DropdownMenuItem(text = { Text("Mute Notifications") }, onClick = {})
                    SubmenuRow(label = "Moderation", icon = Icons.Outlined.Block, onClick = open)
                },
                submenu = {
                    DropdownMenuItem(text = { Text("Silenced") }, onClick = {})
                    DropdownMenuItem(text = { Text("Removed") }, onClick = {})
                },
            )
        }
    }

    @Test
    fun `the first level shows its rows and not the submenu`() {
        setMenu()

        composeTestRule.onNodeWithText("Mute Notifications").assertExists()
        composeTestRule.onNodeWithText("Moderation").assertExists()
        composeTestRule.onNodeWithText("Silenced").assertDoesNotExist()
    }

    @Test
    fun `tapping the row replaces the items in place with the submenu`() {
        setMenu()

        composeTestRule.onNodeWithText("Moderation").performClick()

        composeTestRule.onNodeWithText("Silenced").assertExists()
        composeTestRule.onNodeWithText("Removed").assertExists()
        composeTestRule.onNodeWithText("Mute Notifications").assertDoesNotExist()
    }

    @Test
    fun `the header row returns to the first level`() {
        setMenu()
        composeTestRule.onNodeWithText("Moderation").performClick()

        // In the submenu the header is the only "Moderation" row.
        composeTestRule.onNodeWithText("Moderation").performClick()

        composeTestRule.onNodeWithText("Mute Notifications").assertExists()
        composeTestRule.onNodeWithText("Silenced").assertDoesNotExist()
    }
}
