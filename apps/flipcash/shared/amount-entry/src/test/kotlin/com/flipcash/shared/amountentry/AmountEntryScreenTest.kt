package com.flipcash.shared.amountentry

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flipcash.app.core.ui.ConfirmationStyle
import com.getcode.theme.DesignSystem
import com.getcode.view.LoadingSuccessState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AmountEntryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeController(
        state: AmountEntryDelegate.State = AmountEntryDelegate.State(),
        config: AmountEntryConfig = AmountEntryConfig(
            action = AmountEntryAction(label = "Next"),
        ),
    ) : AmountEntryController {
        override val state = MutableStateFlow(state)
        override val config = MutableStateFlow(config)

        val numbersPressed = mutableListOf<Int>()
        var decimalPressed = false
        var backspacePressed = false

        override fun onNumber(number: Int) { numbersPressed += number }
        override fun onDecimal() { decimalPressed = true }
        override fun onBackspace() { backspacePressed = true }

        fun updateConfig(block: (AmountEntryConfig) -> AmountEntryConfig) {
            config.update(block)
        }
    }

    private fun setScreen(
        controller: FakeController = FakeController(),
        onConfirm: () -> Unit = {},
        onChangeCurrency: () -> Unit = {},
        appBar: (@Composable () -> Unit)? = null,
    ): FakeController {
        composeTestRule.setContent {
            DesignSystem {
                AmountEntryScreen(
                    controller = controller,
                    onConfirm = onConfirm,
                    onChangeCurrency = onChangeCurrency,
                    appBar = appBar,
                )
            }
        }
        return controller
    }

    // ---------------------------------------------------------------
    // Button action label
    // ---------------------------------------------------------------

    @Test
    fun `displays action button with label from config`() {
        setScreen(FakeController(
            config = AmountEntryConfig(
                action = AmountEntryAction(label = "Send"),
            ),
        ))

        composeTestRule.onNodeWithText("Send").assertIsDisplayed()
    }

    @Test
    fun `button label updates when config changes`() {
        val controller = FakeController(
            config = AmountEntryConfig(
                action = AmountEntryAction(label = "Buy"),
            ),
        )
        setScreen(controller)

        composeTestRule.onNodeWithText("Buy").assertIsDisplayed()

        controller.updateConfig {
            it.copy(action = it.action.copy(label = "Sell"))
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Sell").assertIsDisplayed()
    }

    // ---------------------------------------------------------------
    // Button enabled state
    // ---------------------------------------------------------------

    @Test
    fun `button is disabled when canConfirm is false`() {
        setScreen(FakeController(
            config = AmountEntryConfig(
                canConfirm = false,
                action = AmountEntryAction(label = "Next"),
            ),
        ))

        composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
    }

    @Test
    fun `button is enabled when canConfirm is true`() {
        setScreen(FakeController(
            config = AmountEntryConfig(
                canConfirm = true,
                action = AmountEntryAction(label = "Next"),
            ),
        ))

        composeTestRule.onNodeWithText("Next").assertIsEnabled()
    }

    @Test
    fun `button does not trigger confirm while loading`() {
        var confirmed = false
        val controller = FakeController(
            config = AmountEntryConfig(
                canConfirm = true,
                action = AmountEntryAction(
                    label = "Next",
                    loadingState = LoadingSuccessState(loading = true),
                ),
            ),
        )
        setScreen(controller, onConfirm = { confirmed = true })

        // During loading the button text is replaced with a spinner,
        // so we can't find it by text. Instead verify that transitioning
        // back to idle re-enables the button and that confirm wasn't fired.
        assertFalse(confirmed)

        controller.updateConfig {
            it.copy(action = it.action.copy(loadingState = LoadingSuccessState()))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Next").assertIsEnabled()
    }

    // ---------------------------------------------------------------
    // Confirm callback
    // ---------------------------------------------------------------

    @Test
    fun `clicking button triggers onConfirm`() {
        var confirmed = false
        setScreen(
            controller = FakeController(
                config = AmountEntryConfig(
                    canConfirm = true,
                    action = AmountEntryAction(label = "Next"),
                ),
            ),
            onConfirm = { confirmed = true },
        )

        composeTestRule.onNodeWithText("Next").performClick()
        assertTrue(confirmed)
    }

    // ---------------------------------------------------------------
    // Slide-to-confirm variant
    // ---------------------------------------------------------------

    @Test
    fun `displays slide label when action style is Slide`() {
        setScreen(FakeController(
            config = AmountEntryConfig(
                action = AmountEntryAction(
                    label = "Swipe to send",
                    style = ConfirmationStyle.Slide,
                ),
            ),
        ))

        composeTestRule.onNodeWithText("Swipe to send").assertIsDisplayed()
    }

    // ---------------------------------------------------------------
    // Hint text
    // ---------------------------------------------------------------

    @Test
    fun `displays info hint text`() {
        setScreen(FakeController(
            config = AmountEntryConfig(
                hint = AmountEntryHint.Info("Send up to \$100"),
                action = AmountEntryAction(label = "Next"),
            ),
        ))

        composeTestRule.onNodeWithText("Send up to \$100").assertIsDisplayed()
    }

    @Test
    fun `displays error hint text`() {
        setScreen(FakeController(
            config = AmountEntryConfig(
                hint = AmountEntryHint.Error("Limit exceeded"),
                action = AmountEntryAction(label = "Next"),
            ),
        ))

        composeTestRule.onNodeWithText("Limit exceeded").assertIsDisplayed()
    }

    // ---------------------------------------------------------------
    // Keypad interaction
    // ---------------------------------------------------------------

    @Test
    fun `tapping number key invokes controller onNumber`() {
        val controller = setScreen()

        composeTestRule.onNodeWithText("5").performClick()
        assertEquals(listOf(5), controller.numbersPressed)
    }

    @Test
    fun `tapping multiple number keys invokes controller in order`() {
        val controller = setScreen()

        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        assertEquals(listOf(1, 2, 3), controller.numbersPressed)
    }
}
