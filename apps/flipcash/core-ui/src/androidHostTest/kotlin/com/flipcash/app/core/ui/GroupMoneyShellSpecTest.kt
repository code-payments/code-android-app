package com.flipcash.app.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.getcode.ui.components.glass.FloatingChrome
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

    // region where the bar sits in the app's row

    // The row AppNavigationBar floats the bar in insets each edge FloatingChrome.horizontalInset.
    // The Left-Aligned Tab Bar flag moves the Group Money bar's leading edge to the screen's content
    // margin, dimens.inset, which follows the width class; both are read from the theme.
    private var rowInset = 0.dp
    private var contentMargin = 0.dp

    private var leftAligned by mutableStateOf(false)
    private var selectedTab by mutableStateOf(NavBarButton.Chats)

    /** The bar as AppNavigationBar floats it: a screen-wide host, the row's padding, the pill. */
    private fun setRow(shell: NavBarShell) {
        composeRule.setContent {
            FlipcashPreview {
                pillPadding = CodeTheme.dimens.grid.x1 + CodeTheme.dimens.staticGrid.x2
                rowInset = FloatingChrome.horizontalInset
                contentMargin = CodeTheme.dimens.inset
                Box(Modifier.fillMaxWidth().testTag(HOST)) {
                    Box(Modifier.fillMaxWidth().padding(navigationBarRowPadding(shell, leftAligned))) {
                        NavigationBar(
                            state = rememberNavigationBarState(selectedTab = selectedTab),
                            shell = shell,
                            leftAligned = leftAligned,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** The pill's glass, from its first tab's leading edge to its last tab's trailing edge. */
    private data class Glass(val left: Dp, val right: Dp, val top: Dp, val bottom: Dp)

    private fun glass(shell: NavBarShell): Glass {
        val first = bounds(shell.tabs.first().testTag)
        val last = bounds(shell.tabs.last().testTag)
        return Glass(first.left - pillPadding, last.right + pillPadding, first.top, first.bottom)
    }

    private fun assertSameGlass(expected: Glass, actual: Glass, what: String) {
        listOf(
            "left" to (expected.left to actual.left),
            "right" to (expected.right to actual.right),
            "top" to (expected.top to actual.top),
            "bottom" to (expected.bottom to actual.bottom),
        ).forEach { (edge, pair) ->
            val onePixel = 1f / composeRule.density.density
            assertEquals(pair.first.value, pair.second.value, onePixel + 0.01f, "$what: $edge")
        }
    }

    @Test
    fun `with the shell off the left-aligned flag leaves the classic bar spanning the row`() {
        setRow(NavBarShell.Classic)
        val host = bounds(HOST)
        val off = glass(NavBarShell.Classic)
        assertDpEquals(rowInset, off.left - host.left)
        assertDpEquals(rowInset, host.right - off.right)

        leftAligned = true
        composeRule.waitForIdle()

        assertSameGlass(off, glass(NavBarShell.Classic), "classic with the flag on")
    }

    @Test
    fun `under group money with the flag off the bar is centred in the row`() {
        setRow(NavBarShell.GroupMoney)
        val host = bounds(HOST)
        val bar = glass(NavBarShell.GroupMoney)

        assertDpEquals(bar.left - host.left, host.right - bar.right)
        assertTrue(bar.left - host.left > rowInset + 1.dp, "the centred bar reaches the row's edge")
    }

    @Test
    fun `under group money with the flag on the bar's leading edge sits at the content margin`() {
        setRow(NavBarShell.GroupMoney)
        val host = bounds(HOST)
        val centred = glass(NavBarShell.GroupMoney)

        leftAligned = true
        composeRule.waitForIdle()
        val left = glass(NavBarShell.GroupMoney)

        assertDpEquals(contentMargin, left.left - host.left)
        // Only the horizontal position moves: the same width, top and bottom as the centred bar.
        assertDpEquals(centred.right - centred.left, left.right - left.left)
        assertDpEquals(centred.top, left.top)
        assertDpEquals(centred.bottom, left.bottom)
        assertTrue(host.right - left.right > rowInset + 1.dp, "the left bar reaches the row's end")
    }

    @Test
    fun `the group money bar keeps its bounds between the Groups and Money tabs, centred and left`() {
        setRow(NavBarShell.GroupMoney)
        val host = bounds(HOST)
        listOf(false, true).forEach { aligned ->
            leftAligned = aligned
            selectedTab = NavBarButton.Chats
            composeRule.waitForIdle()
            val onGroups = glass(NavBarShell.GroupMoney)
            // Pin where the bar sits in this alignment, so the comparison is between left bars.
            if (aligned) {
                assertDpEquals(contentMargin, onGroups.left - host.left)
            } else {
                assertDpEquals(onGroups.left - host.left, host.right - onGroups.right)
            }
            val groupsTab = bounds("nav_chats")
            val moneyTab = bounds("nav_wallet")

            selectedTab = NavBarButton.Wallet
            composeRule.waitForIdle()

            val what = if (aligned) "left" else "centred"
            assertSameGlass(onGroups, glass(NavBarShell.GroupMoney), "$what bar on Money")
            assertEquals(groupsTab, bounds("nav_chats"), "$what Groups tab on Money")
            assertEquals(moneyTab, bounds("nav_wallet"), "$what Money tab on Money")
        }
    }

    // endregion

    private companion object {
        const val HOST = "host"
    }
}
