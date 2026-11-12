package com.flipcash.app.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.theme.FlipcashPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Spec for the Group Money tab pill (frames node 13729:413 Groups, node 13729:744 Money): the
 * group money shell draws exactly Groups and Money; the classic shell is today's bar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroupMoneyShellSpecTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clicks = mutableListOf<NavBarButton>()

    private fun setBar(shell: NavBarShell, selected: NavBarButton = NavBarButton.Chats) {
        composeRule.setContent {
            FlipcashPreview {
                NavigationBar(
                    state = rememberNavigationBarState(selectedTab = selected),
                    onButtonClick = { clicks += it },
                    shell = shell,
                )
            }
        }
    }

    @Test
    fun `the group money bar shows exactly the Groups and Money tabs`() {
        setBar(NavBarShell.GroupMoney)
        composeRule.onNodeWithTag("nav_chats").assertExists()
        composeRule.onNodeWithTag("nav_wallet").assertExists()
        composeRule.onNodeWithTag("nav_scanner").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_tipcard").assertDoesNotExist()
    }

    @Test
    fun `the group money tabs are labelled Groups and Money`() {
        setBar(NavBarShell.GroupMoney)
        composeRule.onNodeWithText("Groups", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Money", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Chat", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Wallet", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `tapping Groups and Money opens the Chats and Wallet tabs`() {
        setBar(NavBarShell.GroupMoney)
        composeRule.onNodeWithTag("nav_chats").performClick()
        composeRule.onNodeWithTag("nav_wallet").performClick()
        assertEquals(listOf(NavBarButton.Chats, NavBarButton.Wallet), clicks)
    }

    @Test
    fun `the classic bar keeps today's four tabs and labels`() {
        setBar(NavBarShell.Classic)
        listOf("nav_scanner", "nav_chats", "nav_wallet", "nav_tipcard").forEach {
            composeRule.onNodeWithTag(it).assertExists()
        }
        listOf("Scan", "Chat", "Wallet", "You").forEach {
            composeRule.onNodeWithText(it, useUnmergedTree = true).assertExists()
        }
        composeRule.onNodeWithText("Groups", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Money", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the bar defaults to the classic shell`() {
        composeRule.setContent {
            FlipcashPreview {
                NavigationBar(state = rememberNavigationBarState(selectedTab = NavBarButton.Wallet))
            }
        }
        composeRule.onNodeWithTag("nav_scanner").assertExists()
        composeRule.onNodeWithTag("nav_tipcard").assertExists()
    }
}
