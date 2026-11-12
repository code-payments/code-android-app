package com.flipcash.app.balance.internal

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.shared.transactionhistory.FeedSyncState
import com.flipcash.shared.transactionhistory.TransactionAvatar
import com.flipcash.shared.transactionhistory.TransactionListItem
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
import kotlin.time.Instant

/**
 * Spec for the Money tab (frame node 13729:744): today's Wallet laid out as Money when the
 * group money shell is on, and unchanged when it is off. The screen is tall so every list item
 * composes and an absent element is really absent, not just scrolled away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class GroupMoneyShellSpecTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<WalletViewModel.Event>()

    private val mints = listOf(
        "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
        "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB",
        "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263",
    )

    private fun usd(amount: Double, mint: Mint) = Fiat(amount, CurrencyCode.USD).let {
        LocalFiat(underlyingTokenAmount = it, nativeAmount = it, rate = Rate.oneToOne, mint = mint)
    }

    private fun tokens(count: Int): List<TokenWithLocalizedBalance> = List(count) { index ->
        val mint = Mint(mints[index])
        TokenWithLocalizedBalance(
            token = Token.usdf.copy(address = mint, name = "Currency $index"),
            balance = usd(10.0, mint),
            appreciation = usd(1.0, mint),
        )
    }

    private val activity = TransactionListItem(
        id = "010203",
        messageId = listOf<Byte>(1, 2, 3),
        title = "Tipped Sally The Streamer",
        timestamp = Instant.fromEpochSeconds(1700000000L),
        avatar = TransactionAvatar.Generic(),
        signedAmountPrefix = "+",
        amount = usd(20.0, Mint.usdf),
        fee = null,
        canCancel = false,
    )

    private fun setWallet(money: Boolean, tokenCount: Int = 3) {
        val balanceState = WalletViewModel.State(
            receivedMoney = true,
            holdsBalance = true,
            feedSyncState = FeedSyncState.Synced,
            transactions = listOf(activity),
        )
        val tokenState = SelectTokenViewModel.State(
            purpose = TokenPurpose.Balance,
            tokens = tokens(tokenCount),
        )
        composeRule.setContent {
            FlipcashPreview {
                WalletScreenContent(
                    balanceState = balanceState,
                    tokenState = tokenState,
                    dispatchEvent = { events += it },
                    money = money,
                )
            }
        }
    }

    // region Money layout

    @Test
    fun `money shows the Money title and the balance`() {
        setWallet(money = true)
        composeRule.onNodeWithText("Money").assertExists()
        composeRule.onNodeWithTag("money_balance").assertExists()
    }

    @Test
    fun `money counts the currencies held`() {
        setWallet(money = true, tokenCount = 3)
        composeRule.onNodeWithText("3 currencies", substring = true).assertExists()
    }

    @Test
    fun `money says 1 currency for a single currency`() {
        setWallet(money = true, tokenCount = 1)
        composeRule.onNodeWithText("1 currency", substring = true).assertExists()
        composeRule.onNodeWithText("1 currencies", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the currency count opens the token list`() {
        setWallet(money = true)
        composeRule.onNodeWithText("3 currencies", substring = true).performClick()
        assertEquals(
            WalletViewModel.Event.OpenScreen(AppRoute.Sheets.TokenSelection(TokenPurpose.Balance)),
            events.last(),
        )
    }

    @Test
    fun `Add Money opens today's deposit options`() {
        setWallet(money = true)
        composeRule.onNodeWithText("Add Money").performClick()
        assertEquals(WalletViewModel.Event.PresentDepositOptions, events.last())
    }

    @Test
    fun `Withdraw opens today's withdrawal flow`() {
        setWallet(money = true)
        composeRule.onNodeWithText("Withdraw").performClick()
        assertEquals(
            WalletViewModel.Event.OpenScreen(AppRoute.Transfers.Withdrawal(preselectedMint = null)),
            events.last(),
        )
    }

    @Test
    fun `the profile avatar opens You`() {
        setWallet(money = true)
        composeRule.onNodeWithTag("money_profile").performClick()
        assertEquals(WalletViewModel.Event.OpenScreen(AppRoute.Tabs.Menu), events.last())
    }

    @Test
    fun `money lists Recent Activity`() {
        setWallet(money = true)
        composeRule.onNodeWithText("Recent Activity").assertExists()
        composeRule.onNodeWithText("Tipped Sally The Streamer").assertExists()
    }

    @Test
    fun `money drops the card stack, the appreciation line and the tiles`() {
        setWallet(money = true)
        composeRule.onNodeWithTag("wallet_token_card_stack").assertDoesNotExist()
        composeRule.onNodeWithTag("wallet_tiles").assertDoesNotExist()
        composeRule.onNodeWithText("from appreciation", substring = true).assertDoesNotExist()
    }

    // endregion

    // region flag off

    @Test
    fun `the classic wallet keeps the card stack, the appreciation line and the tiles`() {
        setWallet(money = false)
        composeRule.onNodeWithTag("wallet_token_card_stack").assertExists()
        composeRule.onNodeWithTag("wallet_tiles").assertExists()
        composeRule.onNodeWithText("from appreciation", substring = true).assertExists()
    }

    @Test
    fun `the classic wallet has none of the Money layout`() {
        setWallet(money = false)
        composeRule.onNodeWithText("Money").assertDoesNotExist()
        composeRule.onNodeWithText("3 currencies", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("money_profile").assertDoesNotExist()
        composeRule.onNodeWithText("Recent Activity").assertDoesNotExist()
    }

    // endregion
}
