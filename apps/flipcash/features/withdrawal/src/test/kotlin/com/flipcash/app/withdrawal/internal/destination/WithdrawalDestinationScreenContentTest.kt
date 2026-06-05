package com.flipcash.app.withdrawal.internal.destination

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flipcash.app.withdrawal.DestinationState
import com.flipcash.app.withdrawal.WithdrawalViewModel
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.WithdrawalAvailability
import com.getcode.solana.keys.PublicKey
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class WithdrawalDestinationScreenContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var lastEvent: WithdrawalViewModel.Event? = null

    private val testPublicKey = PublicKey(ByteArray(32) { 1 }.toList())

    private fun testAvailability(isValid: Boolean) = WithdrawalAvailability(
        destination = testPublicKey,
        isValid = isValid,
        kind = WithdrawalAvailability.Kind.TokenAccount,
        hasResolvedDestination = false,
        resolvedDestination = testPublicKey,
        requiresInitialization = false,
        feeAmount = null,
    )

    private fun testState(
        tokenDisplayName: String = "USDF",
        availability: WithdrawalAvailability? = null,
    ) = WithdrawalViewModel.State(
        token = TokenWithBalance(
            token = Token.usdf,
            balance = Fiat(10.0, CurrencyCode.USD),
            displayName = tokenDisplayName,
        ),
        selectedAmount = VerifiedFiat(LocalFiat.Zero, null),
        destinationState = DestinationState(availability = availability),
    )

    private fun setScreen(state: WithdrawalViewModel.State) {
        lastEvent = null
        composeTestRule.setContent {
            DesignSystem {
                WithdrawalDestinationScreenContent(state = state, dispatchEvent = { lastEvent = it })
            }
        }
    }

    // ---------------------------------------------------------------
    // Next button enabled state
    // ---------------------------------------------------------------

    @Test
    fun `next button disabled when availability is null`() {
        setScreen(testState(availability = null))
        composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
    }

    @Test
    fun `next button disabled when availability is invalid`() {
        setScreen(testState(availability = testAvailability(isValid = false)))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Next").assertIsNotEnabled()
    }

    @Test
    fun `next button enabled when availability is valid`() {
        setScreen(testState(availability = testAvailability(isValid = true)))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Next").assertIsEnabled()
    }

    // ---------------------------------------------------------------
    // Address validity indicators
    // ---------------------------------------------------------------

    @Test
    fun `valid address text shown when isValid is true`() {
        setScreen(testState(availability = testAvailability(isValid = true)))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Valid address").assertIsDisplayed()
    }

    @Test
    fun `invalid address text shown when isValid is false`() {
        setScreen(testState(availability = testAvailability(isValid = false)))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Invalid address").assertIsDisplayed()
    }

    // ---------------------------------------------------------------
    // Token name in subtitle
    // ---------------------------------------------------------------

    @Test
    fun `token name appears in subtitle text`() {
        setScreen(testState(tokenDisplayName = "USDF"))
        composeTestRule.onNodeWithText("Where would you like to withdraw your USDF to?")
            .assertIsDisplayed()
    }

    // ---------------------------------------------------------------
    // Event dispatch
    // ---------------------------------------------------------------

    @Test
    fun `next click dispatches OnDestinationConfirmed`() {
        setScreen(testState(availability = testAvailability(isValid = true)))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Next").performClick()
        assertTrue(lastEvent is WithdrawalViewModel.Event.OnDestinationConfirmed)
    }
}
