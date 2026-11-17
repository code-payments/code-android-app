package com.flipcash.app.balance.internal

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.shared.transactionhistory.FeedSyncState
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The Money balance (node 13729:744) drops the flag and chevron so the amount sits centred, but
 * tapping it still opens the currency picker: the wallet balance is the only way into it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class MoneyBalanceHeaderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<WalletViewModel.Event>()

    private val mint = Mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")

    private fun usd(amount: Double) = Fiat(amount, CurrencyCode.USD).let {
        LocalFiat(underlyingTokenAmount = it, nativeAmount = it, rate = Rate.oneToOne, mint = mint)
    }

    @Test
    fun `tapping the Money balance opens the currency picker`() {
        composeRule.setContent {
            FlipcashPreview {
                WalletScreenContent(
                    balanceState = WalletViewModel.State(
                        receivedMoney = true,
                        holdsBalance = true,
                        feedSyncState = FeedSyncState.Synced,
                    ),
                    tokenState = SelectTokenViewModel.State(
                        purpose = TokenPurpose.Balance,
                        tokens = listOf(
                            TokenWithLocalizedBalance(
                                token = Token.usdf.copy(address = mint, name = "Currency"),
                                balance = usd(10.0),
                                appreciation = usd(1.0),
                            )
                        ),
                    ),
                    dispatchEvent = { events += it },
                    money = true,
                )
            }
        }
        composeRule.onNodeWithTag("money_balance").performClick()
        assertEquals(WalletViewModel.Event.OpenCurrencySelection, events.last())
    }
}
