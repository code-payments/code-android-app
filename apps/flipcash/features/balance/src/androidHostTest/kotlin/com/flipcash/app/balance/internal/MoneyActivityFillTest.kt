package com.flipcash.app.balance.internal

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.getcode.theme.CodeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The Money tab's Recent Activity takes the height left under the balance and its buttons, down to
 * the bottom of the list (node 13729:744), whether it has a few rows, none, or more than fit. A phone
 * height, so the space left over is what a real screen has.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h800dp")
class MoneyActivityFillTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<WalletViewModel.Event>()

    private val mint = Mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")

    private fun usd(amount: Double, mint: Mint) = Fiat(amount, CurrencyCode.USD).let {
        LocalFiat(underlyingTokenAmount = it, nativeAmount = it, rate = Rate.oneToOne, mint = mint)
    }

    private fun activity(count: Int) = List(count) { index ->
        TransactionListItem(
            id = "row$index",
            messageId = listOf(index.toByte()),
            title = "Activity $index",
            timestamp = Instant.fromEpochSeconds(1700000000L - index * 60L),
            avatar = TransactionAvatar.Generic(),
            signedAmountPrefix = "+",
            amount = usd(1.0 + index, Mint.usdf),
            fee = null,
            canCancel = false,
        )
    }

    /** The list's bottom content padding, read from the theme the tab is drawn in. */
    private var bottomPadding: Dp = 0.dp

    private fun setMoney(
        rows: Int,
        syncState: FeedSyncState = FeedSyncState.Synced,
        money: Boolean = true,
    ) {
        val balanceState = WalletViewModel.State(
            receivedMoney = true,
            holdsBalance = true,
            feedSyncState = syncState,
            transactions = activity(rows),
        )
        val tokenState = SelectTokenViewModel.State(
            purpose = TokenPurpose.Balance,
            tokens = listOf(
                TokenWithLocalizedBalance(
                    token = Token.usdf.copy(address = mint),
                    balance = usd(10.0, mint),
                    appreciation = usd(1.0, mint),
                )
            ),
        )
        composeRule.setContent {
            FlipcashPreview {
                // No tab bar in this host, so the list's bottom padding is the 40 dp gap alone.
                bottomPadding = CodeTheme.dimens.grid.x8
                WalletScreenContent(
                    balanceState = balanceState,
                    tokenState = tokenState,
                    dispatchEvent = { events += it },
                    money = money,
                    topBar = { MoneyTitleBar(profileAvatar = {}, dispatchEvent = {}) },
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** Where the list's content ends: its bottom edge less its bottom content padding. */
    private fun contentBottom(): Dp =
        composeRule.onNodeWithTag("wallet_content").getBoundsInRoot().bottom - bottomPadding

    private fun sectionBottom(): Dp =
        composeRule.onNodeWithTag("money_activity").getBoundsInRoot().bottom

    @Test
    fun `a few rows of activity stretch the section to the bottom of the list`() {
        setMoney(rows = 1)

        assertEquals(contentBottom().value, sectionBottom().value, 0.5f, "section bottom")
        composeRule.onNodeWithText("Activity 0").assertIsDisplayed()
    }

    @Test
    fun `with no activity the section still reaches the bottom and says so`() {
        setMoney(rows = 0, syncState = FeedSyncState.Synced)

        assertEquals(contentBottom().value, sectionBottom().value, 0.5f, "section bottom")
        composeRule.onNodeWithText("Recent Activity").assertIsDisplayed()
        composeRule.onNodeWithText("No activity yet").assertIsDisplayed()
        val header = composeRule.onNodeWithText("Recent Activity").getBoundsInRoot()
        val empty = composeRule.onNodeWithText("No activity yet").getBoundsInRoot()
        assertTrue(empty.top > header.bottom, "empty state under the header, $header vs $empty")
    }

    @Test
    fun `with no activity money says it in one line, not the full history's two`() {
        setMoney(rows = 0, syncState = FeedSyncState.Synced)

        composeRule.onNodeWithText("No activity yet").assertIsDisplayed()
        composeRule.onNodeWithText("No Activity").assertDoesNotExist()
        composeRule.onNodeWithText("Your recent activity will appear here", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a feed that gave up still says it has no activity`() {
        setMoney(rows = 0, syncState = FeedSyncState.Unavailable)

        composeRule.onNodeWithText("No activity yet").assertIsDisplayed()
    }

    @Test
    fun `an empty feed that has not synced yet makes no claim`() {
        setMoney(rows = 0, syncState = FeedSyncState.Unknown)

        assertEquals(contentBottom().value, sectionBottom().value, 0.5f, "section bottom")
        composeRule.onNodeWithText("Recent Activity").assertIsDisplayed()
        composeRule.onNodeWithText("No activity yet").assertDoesNotExist()
        composeRule.onNodeWithText("No Activity").assertDoesNotExist()
    }

    @Test
    fun `more rows than fit run past the bottom and scroll into view`() {
        setMoney(rows = 15)

        assertTrue(sectionBottom() > contentBottom(), "section grows with its rows")
        repeat(4) { composeRule.onNodeWithTag("wallet_content").performTouchInput { swipeUp() } }
        composeRule.onNodeWithText("Activity 14").assertIsDisplayed()
    }

    @Test
    fun `money asks for enough rows to fill the tab`() {
        setMoney(rows = 1)

        assertEquals(
            WalletViewModel.Event.OnActivityPreviewSizeChanged(WalletViewModel.MONEY_PREVIEW_COUNT),
            events.filterIsInstance<WalletViewModel.Event.OnActivityPreviewSizeChanged>().last(),
        )
    }

    @Test
    fun `the classic wallet keeps its three row preview`() {
        setMoney(rows = 1, money = false)

        assertEquals(
            WalletViewModel.Event.OnActivityPreviewSizeChanged(WalletViewModel.RECENT_PREVIEW_COUNT),
            events.filterIsInstance<WalletViewModel.Event.OnActivityPreviewSizeChanged>().last(),
        )
        composeRule.onNodeWithTag("money_activity").assertDoesNotExist()
    }
}
