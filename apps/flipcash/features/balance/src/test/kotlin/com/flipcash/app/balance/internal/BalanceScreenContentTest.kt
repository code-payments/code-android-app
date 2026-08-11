package com.flipcash.app.balance.internal

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

/**
 * Regression coverage for the AddMoneyUX flag removal on the Balance screen. The flag was
 * `launched = true`, so the empty-wallet call-to-action is now unconditionally the
 * "Add Money" deposit path — it must never fall back to the old "Discover Currencies"
 * copy/route, and tapping it must open the deposit options.
 */
@RunWith(RobolectricTestRunner::class)
class BalanceScreenContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var lastEvent: WalletViewModel.Event? = null

    private fun setEmptyBalanceScreen() {
        lastEvent = null
        composeTestRule.setContent {
            DesignSystem {
                CompositionLocalProvider(
                    LocalExchange provides ExchangeStub(
                        providedRates = mapOf(CurrencyCode.USD to Rate.oneToOne),
                        context = LocalContext.current,
                    )
                ) {
                    BalanceScreenContent(
                        tokenState = SelectTokenViewModel.State(
                            purpose = TokenPurpose.Balance,
                            tokens = emptyList(),
                        ),
                        dispatchEvent = { lastEvent = it },
                    )
                }
            }
        }
    }

    @Test
    fun `empty wallet shows add money cta`() {
        setEmptyBalanceScreen()
        composeTestRule.onNodeWithText("Add Money").assertIsDisplayed()
    }

    @Test
    fun `empty wallet does not show discover currencies fallback`() {
        setEmptyBalanceScreen()
        composeTestRule.onNodeWithText("Discover Currencies").assertDoesNotExist()
    }

    @Test
    fun `tapping add money opens deposit options`() {
        setEmptyBalanceScreen()
        composeTestRule.onNodeWithText("Add Money").performClick()
        assertTrue(lastEvent is WalletViewModel.Event.PresentDepositOptions)
    }
}
