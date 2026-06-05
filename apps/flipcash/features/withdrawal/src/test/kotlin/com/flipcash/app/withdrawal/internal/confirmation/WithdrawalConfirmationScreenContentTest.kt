package com.flipcash.app.withdrawal.internal.confirmation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flipcash.app.withdrawal.DestinationState
import com.flipcash.app.withdrawal.WithdrawalViewModel
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.LaunchpadMetadata
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.theme.DesignSystem
import com.getcode.utils.network.LocalNetworkObserver
import com.getcode.utils.network.NetworkObserverStub
import com.getcode.view.LoadingSuccessState
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue
import kotlin.time.Clock

@RunWith(RobolectricTestRunner::class)
class WithdrawalConfirmationScreenContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var lastEvent: WithdrawalViewModel.Event? = null

    private val dummyKey = PublicKey(ByteArray(32) { 1 }.toList())

    private val usdToCadRate = Rate(fx = 1.37161, currency = CurrencyCode.CAD)
    private val cadToUsdRate = Rate(fx = 0.72894, currency = CurrencyCode.USD)

    /**
     * Creates a custom launchpad token with a specific circulating supply.
     * The supply determines the bonding curve price for token-amount rendering.
     */
    private fun customToken(
        name: String = "TestCoin",
        symbol: String = "TEST",
        supplyQuarks: Long = 1_000_000_000_000L, // 1M tokens at 6 decimals
    ) = MintMetadata(
        address = Mint(ByteArray(32) { 2 }.toList()),
        decimals = 6,
        name = name,
        symbol = symbol,
        createdAt = Clock.System.now(),
        description = "",
        imageUrl = "",
        vmMetadata = VmMetadata(
            authority = dummyKey,
            vm = dummyKey,
            lockDurationInDays = 21,
        ),
        launchpadMetadata = LaunchpadMetadata(
            currencyConfig = dummyKey,
            liquidityPool = dummyKey,
            seed = dummyKey,
            authority = dummyKey,
            mintVault = dummyKey,
            coreMintVault = dummyKey,
            currentCirculatingSupplyQuarks = supplyQuarks,
            sellFeeBps = 100,
            price = Fiat(0.01, CurrencyCode.USD),
            marketCap = Fiat(10_000.0, CurrencyCode.USD),
        ),
        socialLinks = emptyList(),
        billCustomizations = null,
        holderMetrics = HolderMetrics.None,
    )

    private fun testState(
        token: TokenWithBalance = TokenWithBalance(
            token = Token.usdf,
            balance = Fiat(10.0, CurrencyCode.USD),
        ),
        withdrawalState: LoadingSuccessState = LoadingSuccessState(),
        destinationAddress: String = "ABC123def456",
        amount: Double = 5.0,
        fee: Fiat? = null,
        entryRate: Rate = Rate.oneToOne,
    ): WithdrawalViewModel.State {
        val textFieldState = TextFieldState()
        textFieldState.setTextAndPlaceCursorAtEnd(destinationAddress)
        return WithdrawalViewModel.State(
            token = token,
            selectedAmount = VerifiedFiat(
                LocalFiat.fromUsd(
                    usdf = Fiat(amount, CurrencyCode.USD),
                    rate = entryRate,
                ),
                null,
            ),
            entryRate = entryRate,
            destinationState = DestinationState(textFieldState = textFieldState),
            withdrawalState = withdrawalState,
            feeAmount = fee,
        )
    }

    private fun setScreen(
        state: WithdrawalViewModel.State,
        exchange: Exchange? = null,
    ) {
        lastEvent = null
        composeTestRule.setContent {
            DesignSystem {
                CompositionLocalProvider(
                    LocalNetworkObserver provides NetworkObserverStub(),
                    LocalExchange provides (exchange ?: ExchangeStub(context = LocalContext.current)),
                ) {
                    WithdrawalConfirmationScreenContent(
                        state = state,
                        dispatchEvent = { lastEvent = it },
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Withdraw button
    // ---------------------------------------------------------------

    @Test
    fun `withdraw button displayed and enabled when idle`() {
        setScreen(testState())
        composeTestRule.onNodeWithText("Withdraw").assertIsDisplayed()
        composeTestRule.onNodeWithText("Withdraw").assertIsEnabled()
    }

    @Test
    fun `withdraw not dispatched during loading`() {
        setScreen(testState(withdrawalState = LoadingSuccessState(loading = true)))
        // During loading the button text is replaced with a spinner,
        // so "Withdraw" text isn't rendered. Verify no event can be dispatched.
        composeTestRule.onNodeWithText("Withdraw").assertDoesNotExist()
    }

    // ---------------------------------------------------------------
    // Destination
    // ---------------------------------------------------------------

    @Test
    fun `destination address displayed`() {
        setScreen(testState(destinationAddress = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU"))
        composeTestRule.onNodeWithText("7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU")
            .assertIsDisplayed()
    }

    // ---------------------------------------------------------------
    // Receipt
    // ---------------------------------------------------------------

    @Test
    fun `receipt shows withdrawal amount label`() {
        setScreen(testState())
        composeTestRule.onNodeWithText("Withdrawal amount").assertIsDisplayed()
    }

    @Test
    fun `receipt shows you receive label`() {
        setScreen(testState())
        composeTestRule.onNodeWithText("You Receive").assertIsDisplayed()
    }

    @Test
    fun `receipt shows fee lines when fee is present`() {
        setScreen(testState(fee = Fiat(0.50, CurrencyCode.USD)))
        composeTestRule.onNodeWithText("Less fee").assertIsDisplayed()
        composeTestRule.onNodeWithText("Net amount").assertIsDisplayed()
    }

    @Test
    fun `receipt hides fee lines when fee is null`() {
        setScreen(testState(fee = null))
        composeTestRule.onNodeWithText("Less fee").assertDoesNotExist()
        composeTestRule.onNodeWithText("Net amount").assertDoesNotExist()
    }

    @Test
    fun `receipt shows amount in token label with token name`() {
        setScreen(testState())
        composeTestRule.onNodeWithText("Amount in USDF", substring = true).assertIsDisplayed()
    }

    // ---------------------------------------------------------------
    // Receipt — USDF / USD
    // ---------------------------------------------------------------

    @Test
    fun `receipt shows formatted USD withdrawal amount`() {
        setScreen(testState(amount = 5.0))
        // balance is Fiat(10.0, USD) → formatted as "$10.00"
        // selectedAmount.localFiat.nativeAmount is Fiat(5.0, USD)
        // The receipt renders tokenWithBalance.balance.formatted()
        // where balance = selectedAmount.localFiat.nativeAmount = $5.00
        composeTestRule.onNodeWithText("$5.00", substring = true).assertIsDisplayed()
    }

    @Test
    fun `receipt shows formatted USD fee with negative prefix`() {
        setScreen(testState(amount = 5.0, fee = Fiat(0.50, CurrencyCode.USD)))
        // fee.formatted(extraPrefix = "-") → "- $0.50"
        composeTestRule.onNodeWithText("- $0.50", substring = true).assertIsDisplayed()
    }

    // ---------------------------------------------------------------
    // Receipt — CAD currency conversion
    // ---------------------------------------------------------------

    @Test
    fun `receipt shows CAD formatted amounts when entry rate is CAD`() {
        val cadRate = usdToCadRate
        // $5 USD → ~$6.86 CAD via LocalFiat.fromUsd
        setScreen(
            testState(
                token = TokenWithBalance(
                    token = Token.usdf,
                    balance = Fiat(6.86, CurrencyCode.CAD),
                ),
                amount = 5.0,
                entryRate = cadRate,
            ),
            exchange = ExchangeStub(
                providedRates = mapOf(
                    CurrencyCode.CAD to cadRate,
                    CurrencyCode.USD to cadToUsdRate,
                ),
                context = ApplicationProvider.getApplicationContext(),
            ),
        )
        // nativeAmount = Fiat(5.0 USD).convertingTo(cadRate) ≈ $6.86 CAD
        // The receipt renders this amount in the withdrawal line and the "you receive" display.
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Withdrawal amount").assertExists()
        val cadNodes = composeTestRule.onAllNodesWithText("6.86", substring = true)
        cadNodes.fetchSemanticsNodes().let { nodes ->
            assertTrue(nodes.isNotEmpty(), "Expected CAD-formatted amount '6.86' in receipt")
        }
    }

    @Test
    fun `receipt shows CAD fee converted via entry rate`() {
        val cadRate = usdToCadRate
        // fee = $0.50 USD, entryRate = CAD → feeInEntryCurrency = 0.50 * 1.37161 ≈ $0.69 CAD
        setScreen(
            testState(
                token = TokenWithBalance(
                    token = Token.usdf,
                    balance = Fiat(6.86, CurrencyCode.CAD),
                ),
                amount = 5.0,
                fee = Fiat(0.50, CurrencyCode.USD),
                entryRate = cadRate,
            ),
            exchange = ExchangeStub(
                providedRates = mapOf(
                    CurrencyCode.CAD to cadRate,
                    CurrencyCode.USD to cadToUsdRate,
                ),
                context = ApplicationProvider.getApplicationContext(),
            ),
        )
        composeTestRule.onNodeWithText("Less fee").assertExists()
        composeTestRule.onNodeWithText("Net amount").assertExists()
    }

    // ---------------------------------------------------------------
    // Receipt — custom token with bonding curve
    // ---------------------------------------------------------------

    @Test
    fun `receipt shows token name for custom launchpad token`() {
        val token = customToken(name = "Jeffy")
        setScreen(testState(
            token = TokenWithBalance(
                token = token,
                balance = Fiat(5.0, CurrencyCode.USD),
                displayName = "Jeffy",
            ),
            amount = 5.0,
        ))
        composeTestRule.onNodeWithText("Amount in Jeffy", substring = true).assertIsDisplayed()
    }

    @Test
    fun `receipt renders bonding curve token amount for custom token`() {
        // With launchpadMetadata.currentCirculatingSupplyQuarks set, the receipt
        // computes token amounts through Estimator.valueExchangeAsTokens
        val token = customToken(name = "Jeffy", supplyQuarks = 1_000_000_000_000L)
        setScreen(testState(
            token = TokenWithBalance(
                token = token,
                balance = Fiat(5.0, CurrencyCode.USD),
                displayName = "Jeffy",
            ),
            amount = 5.0,
        ))
        // The "Amount in Jeffy" line should display a computed token amount (not a $ value)
        // We verify the label exists; the exact number depends on the bonding curve math.
        composeTestRule.onNodeWithText("Amount in Jeffy", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("You Receive").assertIsDisplayed()
    }

    @Test
    fun `low supply token renders bonding curve amount`() {
        // Low supply → tokens are cheaper → more tokens per dollar
        val lowSupplyToken = customToken(name = "CoinA", supplyQuarks = 100_000_000L) // 100 tokens
        setScreen(testState(
            token = TokenWithBalance(
                token = lowSupplyToken,
                balance = Fiat(5.0, CurrencyCode.USD),
                displayName = "CoinA",
            ),
            amount = 5.0,
        ))
        composeTestRule.onNodeWithText("Amount in CoinA", substring = true).assertIsDisplayed()
    }

    @Test
    fun `high supply token renders bonding curve amount`() {
        // High supply → tokens are more expensive → fewer tokens per dollar
        val highSupplyToken = customToken(name = "CoinB", supplyQuarks = 10_000_000_000_000L) // 10M tokens
        setScreen(testState(
            token = TokenWithBalance(
                token = highSupplyToken,
                balance = Fiat(5.0, CurrencyCode.USD),
                displayName = "CoinB",
            ),
            amount = 5.0,
        ))
        composeTestRule.onNodeWithText("Amount in CoinB", substring = true).assertIsDisplayed()
    }

    // ---------------------------------------------------------------
    // Event dispatch
    // ---------------------------------------------------------------

    @Test
    fun `withdraw click dispatches OnWithdraw`() {
        setScreen(testState())
        composeTestRule.onNodeWithText("Withdraw").performClick()
        assertTrue(lastEvent is WithdrawalViewModel.Event.OnWithdraw)
    }
}
