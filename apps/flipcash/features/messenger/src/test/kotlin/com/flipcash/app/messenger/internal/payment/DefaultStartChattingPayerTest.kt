package com.flipcash.app.messenger.internal.payment

import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.messenger.R
import com.flipcash.services.models.TipAction
import com.flipcash.services.models.TipOrigin
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The checks that stand between a tap on "Start Chatting" and money leaving. */
class DefaultStartChattingPayerTest {

    private val token = mockk<Token>(relaxed = true)
    private val balance = mockk<Fiat>(relaxed = true)
    private val fee = mockk<Fiat>(relaxed = true)

    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true) {
        every { observeSelectedTokenMint() } returns flowOf(token.address)
        every { tokenBalances } returns flowOf(listOf(mockk<TokenWithBalance>(relaxed = true) {
            every { this@mockk.token } returns this@DefaultStartChattingPayerTest.token
        }))
        every { balanceForToken(any<Token>()) } returns balance
    }
    private val exchange = mockk<Exchange>(relaxed = true) { every { preferredRate } returns mockk<Rate>(relaxed = true) }
    private val userManager = mockk<UserManager>(relaxed = true)
    private val calculator = mockk<VerifiedFiatCalculator>(relaxed = true)
    private val tips = mockk<TipPaymentDelegate>(relaxed = true) {
        every { exceedsSendLimit(any()) } returns false
    }
    private val analytics = RecordingAnalytics()
    private val resources = mockk<ResourceHelper>(relaxed = true) {
        every { getString(any()) } answers { "res:${firstArg<Int>()}" }
    }

    private fun payer() = DefaultStartChattingPayer(
        tokenCoordinator = tokenCoordinator,
        exchange = exchange,
        userManager = userManager,
        verifiedFiatCalculator = calculator,
        tipPaymentDelegate = tips,
        purchaseMethodController = mockk<PurchaseMethodController>(relaxed = true),
        analytics = analytics,
        resources = resources,
    )

    @Before
    fun setUp() {
        BottomBarManager.clear()
        every { userManager.accountCluster } returns mockk(relaxed = true)
        every { fee.valueGreaterThan(any()) } returns false
    }

    @After
    fun tearDown() = BottomBarManager.clear()

    private fun assertBlockedWith(result: Result<ChatId?>, title: Int) {
        assertIs<StartChattingPayer.PaymentBlocked>(result.exceptionOrNull())
        assertEquals("res:$title", BottomBarManager.messages.value.single().title)
        coVerify(exactly = 0) { tips.send(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `over balance is blocked with the insufficient balance alert`() = runTest {
        every { fee.valueGreaterThan(any()) } returns true

        val result = payer().pay(listOf(1), fee, ProfileOrigin.UsernameLookup, onAddMoney = {})

        assertBlockedWith(result, R.string.title_insufficientBalance)
    }

    @Test
    fun `over the send limit is blocked with the limit alert`() = runTest {
        every { tips.exceedsSendLimit(any()) } returns true

        val result = payer().pay(listOf(1), fee, ProfileOrigin.UsernameLookup, onAddMoney = {})

        assertBlockedWith(result, R.string.error_title_sendLimitReached)
    }

    @Test
    fun `no account fails without an alert or a payment`() = runTest {
        every { userManager.accountCluster } returns null

        val result = payer().pay(listOf(1), fee, ProfileOrigin.UsernameLookup, onAddMoney = {})

        assertTrue(result.exceptionOrNull() !is StartChattingPayer.PaymentBlocked)
        assertTrue(result.isFailure)
        assertTrue(BottomBarManager.messages.value.isEmpty())
        coVerify(exactly = 0) { tips.send(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a payment that clears every check is sent and returns the chat`() = runTest {
        val chatId = ChatId(ByteArray(32) { 7 })
        coEvery { calculator.compute(any(), any(), any(), any()) } returns Result.success(mockk(relaxed = true))
        coEvery { tips.send(any(), any(), any(), any(), any(), any()) } returns Result.success(chatId)

        val result = payer().pay(listOf(1), fee, ProfileOrigin.UsernameLookup, onAddMoney = {})

        assertEquals(chatId, result.getOrNull())
        assertTrue(BottomBarManager.messages.value.isEmpty())
        coVerify(exactly = 1) { tips.send(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `under the start chatting fee floor is blocked with the amount too small alert`() = runTest {
        coEvery { calculator.compute(any(), any(), any(), any()) } returns
            Result.failure(ComputeVerifiedFiatError.AmountBelowMinimum())

        val result = payer().pay(listOf(1), fee, ProfileOrigin.UsernameLookup, onAddMoney = {})

        assertBlockedWith(result, R.string.error_title_amountTooSmall)
    }

    @Test
    fun `an opening payment from anywhere but a scan is a send`() = runTest {
        coEvery { calculator.compute(any(), any(), any(), any()) } returns Result.success(mockk(relaxed = true))
        coEvery { tips.send(any(), any(), any(), any(), any(), any()) } returns Result.success(null)
        val others = ProfileOrigin.entries - ProfileOrigin.Scan

        others.forEach { origin -> payer().pay(listOf(1), fee, origin, onAddMoney = {}) }

        coVerify(exactly = others.size) {
            tips.send(any(), any(), any(), any(), TipOrigin.TIPCARD, TipAction.SEND)
        }
        coVerify(exactly = 0) { tips.send(any(), any(), any(), any(), any(), TipAction.TIP) }
        assertEquals(List(others.size) { "Sent Cash" }, analytics.events.map { it.name })
    }

    @Test
    fun `an opening payment from a scanned flipcard is a tip`() = runTest {
        coEvery { calculator.compute(any(), any(), any(), any()) } returns Result.success(mockk(relaxed = true))
        coEvery { tips.send(any(), any(), any(), any(), any(), any()) } returns Result.success(null)

        payer().pay(listOf(1), fee, ProfileOrigin.Scan, onAddMoney = {})

        coVerify(exactly = 1) { tips.send(any(), any(), any(), any(), TipOrigin.TIPCARD, TipAction.TIP) }
        assertEquals(listOf("Sent Tip"), analytics.events.map { it.name })
    }
}
