package com.flipcash.app.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.theme.FlipcashPreview
import com.getcode.theme.CodeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    // The pill's side padding inside its glass, grid.x1 plus the indicator's overhang
    // (staticGrid.x2). grid.x1 follows the screen's width class, so it is read from the theme.
    private var pillPadding = 0.dp

    private fun setBar(shell: NavBarShell, selected: NavBarButton = NavBarButton.Chats) {
        composeRule.setContent {
            FlipcashPreview {
                pillPadding = CodeTheme.dimens.grid.x1 + CodeTheme.dimens.staticGrid.x2
                Box(Modifier.fillMaxWidth().testTag(HOST)) {
                    NavigationBar(
                        state = rememberNavigationBarState(selectedTab = selected),
                        onButtonClick = { clicks += it },
                        shell = shell,
                    )
                }
            }
        }
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    // Layout snaps to whole pixels, so a half-pixel split can land on either side.
    private fun assertDpEquals(expected: Dp, actual: Dp) {
        val onePixel = 1f / composeRule.density.density
        assertEquals(expected.value, actual.value, onePixel + 0.01f, "expected $expected, was $actual")
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
    fun `the group money bar is sized to its two tabs and centred, not full width`() {
        setBar(NavBarShell.GroupMoney)
        val host = bounds(HOST)
        val groups = bounds("nav_chats")
        val money = bounds("nav_wallet")

        val leftGap = groups.left - host.left
        val rightGap = host.right - money.right
        assertDpEquals(leftGap, rightGap)
        assertTrue(leftGap > pillPadding + 1.dp, "the pill reaches the edges: gap $leftGap")
        assertDpEquals(groups.right - groups.left, money.right - money.left)
        assertDpEquals(groups.right, money.left)
    }

    @Test
    fun `the classic bar still spans the width it is given`() {
        setBar(NavBarShell.Classic)
        val host = bounds(HOST)
        assertDpEquals(pillPadding, bounds("nav_scanner").left - host.left)
        assertDpEquals(pillPadding, host.right - bounds("nav_tipcard").right)
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

    private companion object {
        const val HOST = "host"
    }
}
