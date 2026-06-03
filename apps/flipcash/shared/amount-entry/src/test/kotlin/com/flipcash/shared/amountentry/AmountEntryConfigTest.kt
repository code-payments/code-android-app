package com.flipcash.shared.amountentry

import com.flipcash.app.core.ui.ConfirmationStyle
import com.getcode.view.LoadingSuccessState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AmountEntryConfigTest {

    @Test
    fun `default config has no hint and cannot confirm`() {
        val config = AmountEntryConfig(action = AmountEntryAction(label = "Go"))
        assertIs<AmountEntryHint.None>(config.hint)
        assertFalse(config.canConfirm)
        assertTrue(config.canChangeCurrency)
    }

    @Test
    fun `config with info hint`() {
        val config = AmountEntryConfig(
            hint = AmountEntryHint.Info("Send up to \$100"),
            action = AmountEntryAction(label = "Next"),
        )
        assertIs<AmountEntryHint.Info>(config.hint)
        assertEquals("Send up to \$100", (config.hint as AmountEntryHint.Info).text)
    }

    @Test
    fun `config with error hint`() {
        val config = AmountEntryConfig(
            hint = AmountEntryHint.Error("Limit exceeded"),
            action = AmountEntryAction(label = "Next"),
        )
        assertIs<AmountEntryHint.Error>(config.hint)
        assertEquals("Limit exceeded", (config.hint as AmountEntryHint.Error).text)
    }

    @Test
    fun `action defaults to Button style`() {
        val action = AmountEntryAction(label = "Continue")
        assertEquals(ConfirmationStyle.Button, action.style)
        assertTrue(action.loadingState.isIdle)
    }

    @Test
    fun `action with Slide style`() {
        val action = AmountEntryAction(
            label = "Swipe to send",
            style = ConfirmationStyle.Slide,
        )
        assertEquals(ConfirmationStyle.Slide, action.style)
    }

    @Test
    fun `action loading state`() {
        val action = AmountEntryAction(
            label = "Send",
            loadingState = LoadingSuccessState(loading = true),
        )
        assertTrue(action.loadingState.loading)
        assertFalse(action.loadingState.success)
        assertFalse(action.loadingState.isIdle)
    }

    @Test
    fun `action success state`() {
        val action = AmountEntryAction(
            label = "Send",
            loadingState = LoadingSuccessState(success = true),
        )
        assertTrue(action.loadingState.success)
        assertFalse(action.loadingState.loading)
    }

    @Test
    fun `style default values`() {
        val style = AmountEntryStyle(actionLabel = "Buy")
        assertEquals("Buy", style.actionLabel)
        assertEquals(ConfirmationStyle.Button, style.actionStyle)
        assertTrue(style.canChangeCurrency)
        assertEquals("", style.infoHint("anything"))
        assertEquals("", style.overMaxHint("anything"))
        assertEquals(null, style.belowMinHint)
    }

    @Test
    fun `style with custom hints`() {
        val style = AmountEntryStyle(
            actionLabel = "Send",
            actionStyle = ConfirmationStyle.Slide,
            canChangeCurrency = false,
            infoHint = { "Up to $it available" },
            overMaxHint = { "Exceeds $it limit" },
            belowMinHint = { "Must be at least $it" },
        )
        assertEquals("Up to \$100 available", style.infoHint("\$100"))
        assertEquals("Exceeds \$50 limit", style.overMaxHint("\$50"))
        assertEquals("Must be at least \$5", style.belowMinHint!!("\$5"))
        assertFalse(style.canChangeCurrency)
    }
}
