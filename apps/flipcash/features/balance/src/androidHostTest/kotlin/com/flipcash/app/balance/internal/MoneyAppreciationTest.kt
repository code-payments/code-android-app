package com.flipcash.app.balance.internal

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import kotlin.test.assertTrue

/**
 * The Money tab draws the classic wallet's aggregate appreciation pill directly under the balance,
 * above the currency count and Add Money, and it reads exactly as the wallet's does for the same
 * holdings: same sign, same amount, same label.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class MoneyAppreciationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val mint = Mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")

    private fun usd(amount: Double) = Fiat(amount, CurrencyCode.USD).let {
        LocalFiat(underlyingTokenAmount = it, nativeAmount = it, rate = Rate.oneToOne, mint = mint)
    }

    private var money by mutableStateOf(false)

    /**
     * One currency holding $27 that has moved by [appreciation]. The 7 appears nowhere else on the
     * Money tab, so it finds the balance.
     */
    private fun setWallet(appreciation: Double, money: Boolean) {
        this.money = money
        val balanceState = WalletViewModel.State(
            receivedMoney = true,
            holdsBalance = true,
            feedSyncState = FeedSyncState.Synced,
        )
        val tokenState = SelectTokenViewModel.State(
            purpose = TokenPurpose.Balance,
            tokens = listOf(
                TokenWithLocalizedBalance(
                    token = Token.usdf.copy(address = mint, name = "Currency"),
                    balance = usd(27.0),
                    appreciation = usd(appreciation),
                )
            ),
        )
        composeRule.setContent {
            FlipcashPreview {
                WalletScreenContent(
                    balanceState = balanceState,
                    tokenState = tokenState,
                    dispatchEvent = {},
                    money = this.money,
                    topBar = { MoneyTitleBar(profileAvatar = {}, dispatchEvent = {}) },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private val pillLabel = hasText("from appreciation") or hasText("from depreciation")

    /**
     * The pill as it reads: its signed amount, a space, then its label. The amount is drawn a Text
     * per character, so it is read off the text nodes on the label's line, left of the label.
     */
    private fun pillText(): String {
        val label = composeRule.onNode(pillLabel, useUnmergedTree = true).fetchSemanticsNode()
        val line = label.boundsInRoot
        val amount = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            .descendants()
            .filter { it.text().isNotEmpty() }
            .filter { it.boundsInRoot.right <= line.left && it.boundsInRoot.center.y in line.top..line.bottom }
            .sortedBy { it.boundsInRoot.left }
            .joinToString("") { it.text() }
        return "$amount ${label.text()}"
    }

    private fun SemanticsNode.descendants(): List<SemanticsNode> =
        children.flatMap { listOf(it) + it.descendants() }

    private fun SemanticsNode.text(): String =
        config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }

    @Test
    fun `money draws the pill under the balance, above the currency count and Add Money`() {
        setWallet(appreciation = 3.0, money = true)

        val balance = composeRule.onNodeWithText("7", useUnmergedTree = true).getBoundsInRoot()
        val pill = composeRule.onNode(pillLabel, useUnmergedTree = true).getBoundsInRoot()
        val currencies = composeRule.onNodeWithText("1 currency", substring = true).getBoundsInRoot()
        val addMoney = composeRule.onNodeWithText("Add Money").getBoundsInRoot()

        assertTrue(pill.top >= balance.bottom, "pill under the balance, $balance vs $pill")
        assertTrue(currencies.top >= pill.bottom, "currency count under the pill, $pill vs $currencies")
        assertTrue(addMoney.top >= currencies.bottom, "Add Money under the count, $currencies vs $addMoney")
    }

    @Test
    fun `money's pill reads as the classic wallet's for a gain`() =
        assertMoneyReadsAsClassic(appreciation = 3.0, expected = "+$3.00 from appreciation")

    @Test
    fun `money's pill reads as the classic wallet's for a loss`() =
        assertMoneyReadsAsClassic(appreciation = -2.0, expected = "-$2.00 from depreciation")

    @Test
    fun `money's pill reads as the classic wallet's for no change`() =
        assertMoneyReadsAsClassic(appreciation = 0.0, expected = "+$0.00 from appreciation")

    @Test
    fun `money shows a depreciation as a depreciation`() {
        setWallet(appreciation = -2.0, money = true)

        assertEquals("-$2.00 from depreciation", pillText())
        composeRule.onNodeWithText("from appreciation").assertDoesNotExist()
    }

    /** Reads the classic wallet's pill, turns the Money layout on over the same state, reads Money's. */
    private fun assertMoneyReadsAsClassic(appreciation: Double, expected: String) {
        setWallet(appreciation = appreciation, money = false)
        val classic = pillText()

        money = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("money_balance").assertExists()

        assertEquals(expected, classic, "classic wallet")
        assertEquals(classic, pillText(), "money")
    }
}
