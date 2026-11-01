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
import androidx.compose.ui.test.onNodeWithText

private fun SemanticsNodeInteraction.click() = performSemanticsAction(SemanticsActions.OnClick)

class ContextPaneTest {
    @get:Rule val rule = createComposeRule()
    private val nav = DesktopNavState()

    private fun pane(content: ContextContent) {
        nav.context = content
        rule.setContent {
            FlipcashTheme {
                nav.context?.let { ContextPane(it, nav, wallet = { m -> Box(m.testTag("fake_wallet")) }) }
            }
        }
    }

    @Test
    fun `close dismisses any pane`() {
        pane(ContextContent.Wallet)
        rule.onNodeWithTag("context_close").click()
        rule.waitForIdle()
        assertEquals(null, nav.context)
    }

    @Test
    fun `an unavailable screen's back closes it`() {
        pane(ContextContent.Unavailable("Bill"))
        rule.onNodeWithText("‹ Back").click()
        rule.waitForIdle()
        assertEquals(null, nav.context)
    }
}
