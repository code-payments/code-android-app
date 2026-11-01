package com.flipcash.desktop.shell

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click as mouseClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashTheme
import com.flipcash.desktop.input.DesktopCommand
import com.flipcash.desktop.input.HostOs
import com.flipcash.desktop.input.desktopKeyHandler
import com.flipcash.desktop.input.perform
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// A real pointer moves and clicks, so hover and hit testing are part of every test. The test window
// is 1024 x 768, and at the wide widths the trailing controls sit outside it; only those go through
// the node's click action instead.
private fun SemanticsNodeInteraction.click(): SemanticsNodeInteraction {
    // positionInRoot, not the bounds, which are clipped to the window.
    val node = fetchSemanticsNode()
    val (x, y) = node.positionInRoot
    val inWindow = x >= 0f && y >= 0f && x + node.size.width <= TestWindowWidth && y + node.size.height <= TestWindowHeight
    return if (inWindow) performMouseInput { mouseClick(center) } else performSemanticsAction(SemanticsActions.OnClick)
}

private const val TestWindowWidth = 1024f
private const val TestWindowHeight = 768f

class DesktopShellTest {
    @get:Rule
    val rule = createComposeRule()

    private val nav = DesktopNavState()
    private var width by mutableStateOf(1280.dp)
    private var os = HostOs.Linux

    // The window's key handling, as Main wires it, so key presses here take the real path.
    private fun run(command: DesktopCommand): Boolean =
        nav.perform(command, nav.contextPaneModeAt(widthClassFor(width)))

    private fun shell(at: Dp, block: ComposeContentTestRule.() -> Unit) {
        width = at
        rule.setContent {
            FlipcashTheme {
            Box(
                Modifier.requiredWidth(width).fillMaxHeight()
                    .onPreviewKeyEvent(desktopKeyHandler(os, ::run))
                    .testTag("window")
                    .focusable(),
            ) {
                DesktopShell(
                    nav = nav,
                    sidebar = { m -> Box(m.testTag("fake_list")) },
                    main = { m -> Box(m.testTag("fake_main")) },
                    context = { content, m -> Box(m.testTag("fake_${content::class.simpleName}")) { Text("$content") } },
                    footer = { m ->
                        SidebarFooter(
                            displayName = "Brandon",
                            balance = "$80.80",
                            onYou = nav::openYou,
                            onWallet = nav::openWallet,
                            onSettings = {},
                            modifier = m,
                        )
                    },
                )
            }
            }
        }
        rule.block()
    }

    @Test
    fun `balance opens wallet in a column at wide`() = shell(1280.dp) {
        onNodeWithTag("footer_balance").click()
        onNodeWithTag("context_pane").assertExists()
        onNodeWithTag("context_overlay").assertDoesNotExist()
        onNodeWithTag("fake_Wallet").assertExists()
    }

    @Test
    fun `wallet takes a column beside a rail at 1000 and the list returns when it closes`() = shell(1280.dp) {
        onNodeWithTag("footer_balance").click()
        width = 1000.dp
        waitForIdle()
        onNodeWithTag("context_pane").assertExists()
        onNodeWithTag("rail").assertExists()
        onNodeWithTag("sidebar").assertDoesNotExist()
        onNodeWithTag("fake_Wallet").assertExists()
        nav.closeContext()
        waitForIdle()
        onNodeWithTag("sidebar").assertExists()
        onNodeWithTag("rail").assertDoesNotExist()
    }

    @Test
    fun `wallet floats over the chat below 840 without dimming it, and esc closes it`() = shell(700.dp) {
        onNodeWithTag("footer_balance").click()
        onNodeWithTag("context_overlay").assertExists()
        onNodeWithTag("context_scrim").assertDoesNotExist()
        assertTrue(nav.onEscape(nav.contextPaneModeAt(WidthClass.Medium)))
        waitForIdle()
        onNodeWithTag("context_overlay").assertDoesNotExist()
    }

    @Test
    fun `the toggle hides the list, and the toolbar's toggle brings it back`() = shell(1280.dp) {
        onNodeWithTag("sidebar_toggle").click()
        onNodeWithTag("sidebar").assertDoesNotExist()
        onNodeWithTag("rail").assertDoesNotExist()
        width = 1000.dp
        waitForIdle()
        onNodeWithTag("sidebar").assertDoesNotExist()
        onNodeWithTag("sidebar_toggle").click()
        onNodeWithTag("sidebar").assertExists()
    }

    @Test
    fun `resting on the rail floats the list, and leaving it folds back`() = shell(1000.dp) {
        nav.openWallet()
        waitForIdle()
        onNodeWithTag("rail").performMouseInput { moveTo(center) }
        waitUntil(2_000) { nav.sidebarPeek }
        onNodeWithTag("sidebar_peek").assertExists()
        onNodeWithTag("sidebar_peek").performMouseInput {
            moveTo(center)
            moveTo(Offset(width + 200f, centerY))
        }
        waitUntil(2_000) { !nav.sidebarPeek }
        onNodeWithTag("sidebar_peek").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeContentTestRule.press(key: Key, ctrl: Boolean = false, meta: Boolean = false) {
        onNodeWithTag("window").performSemanticsAction(SemanticsActions.RequestFocus)
        onNodeWithTag("window").performKeyInput {
            when {
                ctrl -> withKeyDown(Key.CtrlLeft) { pressKey(key) }
                meta -> withKeyDown(Key.MetaLeft) { pressKey(key) }
                else -> pressKey(key)
            }
        }
        waitForIdle()
    }

    @Test
    fun `esc folds the floating list first, and leaves the pane in its column`() = shell(1000.dp) {
        nav.openWallet()
        waitForIdle()
        onNodeWithTag("rail").performMouseInput { moveTo(center) }
        waitUntil(2_000) { nav.sidebarPeek }
        press(Key.Escape)
        assertFalse(nav.sidebarPeek)
        onNodeWithTag("sidebar_peek").assertDoesNotExist()
        onNodeWithTag("context_pane").assertExists()
        press(Key.Escape)
        assertEquals(ContextContent.Wallet, nav.context)
        onNodeWithTag("context_pane").assertExists()
    }

    @Test
    fun `esc closes the pane floating over the chat`() = shell(700.dp) {
        nav.openWallet()
        waitForIdle()
        onNodeWithTag("context_overlay").assertExists()
        press(Key.Escape)
        assertNull(nav.context)
        onNodeWithTag("context_overlay").assertDoesNotExist()
    }

    @Test
    fun `control I twice opens info and closes it again on windows and linux`() = shell(1280.dp) {
        press(Key.I, ctrl = true)
        assertEquals(ContextContent.Info, nav.context)
        onNodeWithTag("fake_Info").assertExists()
        press(Key.I, ctrl = true)
        assertNull(nav.context)
        onNodeWithTag("context_pane").assertDoesNotExist()
    }

    @Test
    fun `command I is left to the menu bar on mac`() {
        os = HostOs.Mac
        shell(1280.dp) {
            press(Key.I, meta = true)
            assertNull(nav.context)
        }
    }

    @Test
    fun `the floating list brings the toggle, which hides the sidebar`() = shell(1000.dp) {
        nav.openWallet()
        waitForIdle()
        onNodeWithTag("rail").performMouseInput { moveTo(center) }
        waitUntil(2_000) { nav.sidebarPeek }
        val toggle = onNode(hasTestTag("sidebar_toggle") and hasAnyAncestor(hasTestTag("sidebar_peek")))
        // A real pointer: moving onto the toggle must not count as leaving the list.
        toggle.performMouseInput { moveTo(center) }
        waitForIdle()
        assertTrue(nav.sidebarPeek)
        toggle.click()
        waitForIdle()
        assertTrue(nav.sidebarHidden)
        onNodeWithTag("sidebar_peek").assertDoesNotExist()
        onNodeWithTag("rail").assertDoesNotExist()
    }

    @Test
    fun `info button toggles info`() = shell(1280.dp) {
        onNodeWithTag("toolbar_info").click()
        onNodeWithTag("fake_Info").assertExists()
        onNodeWithTag("toolbar_info").click()
        onNodeWithTag("context_pane").assertDoesNotExist()
    }

    @Test
    fun `footer avatar opens you`() = shell(1280.dp) {
        onNodeWithTag("footer_you").click()
        onNodeWithTag("fake_You").assertExists()
    }

    @Test
    fun `no rail without an open pane, and no tab bar at any width`() = shell(500.dp) {
        for (w in listOf(500.dp, 700.dp, 1000.dp, 1280.dp)) {
            width = w
            waitForIdle()
            onAllNodesWithTag("rail").fetchSemanticsNodes().let { assertEquals(0, it.size, "rail at $w") }
            onAllNodesWithTag("tab_bar").fetchSemanticsNodes().let { assertEquals(0, it.size, "tab bar at $w") }
        }
    }

    @Test
    fun `compact shows the list, then the chat with a back control`() = shell(500.dp) {
        onNodeWithTag("fake_list").assertExists()
        onNodeWithTag("fake_main").assertDoesNotExist()
        nav.compactShowsChat = true
        waitForIdle()
        onNodeWithTag("fake_main").assertExists()
        onNodeWithTag("fake_list").assertDoesNotExist()
        onNodeWithTag("compact_back").click()
        onNodeWithTag("fake_list").assertExists()
    }

    @Test
    fun `medium shows list and main side by side`() = shell(700.dp) {
        onNodeWithTag("fake_list").assertExists()
        onNodeWithTag("fake_main").assertExists()
    }
}
