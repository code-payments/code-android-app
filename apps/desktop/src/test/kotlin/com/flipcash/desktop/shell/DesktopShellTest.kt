package com.flipcash.desktop.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashTheme
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

// The test window is 1024 x 768, so at the wide widths these tests use the trailing controls sit
// outside it; the click goes through the node's action rather than a pointer.
private fun SemanticsNodeInteraction.click() = performSemanticsAction(SemanticsActions.OnClick)

class DesktopShellTest {
    @get:Rule
    val rule = createComposeRule()

    private val nav = DesktopNavState()
    private var width by mutableStateOf(1280.dp)

    private fun shell(at: Dp, block: ComposeContentTestRule.() -> Unit) {
        width = at
        rule.setContent {
            FlipcashTheme {
            Box(Modifier.requiredWidth(width).fillMaxHeight()) {
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
    fun `wallet overlays main below 1100 and survives a resize`() = shell(1280.dp) {
        onNodeWithTag("footer_balance").click()
        onNodeWithTag("context_pane").assertExists()
        width = 1000.dp
        waitForIdle()
        onNodeWithTag("context_overlay").assertExists()
        onNodeWithTag("fake_Wallet").assertExists()
        assertEquals(ContextContent.Wallet, nav.context)
    }

    @Test
    fun `scrim closes the overlay`() = shell(1000.dp) {
        onNodeWithTag("footer_balance").click()
        onNodeWithTag("context_scrim").click()
        onNodeWithTag("context_overlay").assertDoesNotExist()
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
    fun `no rail or tab bar at any width`() = shell(500.dp) {
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
