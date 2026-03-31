package com.getcode.opencode.internal.transactors

import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Key32
import com.getcode.solana.keys.Mint
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SendGiftCardTransactorTest {

    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val payloadFactory = PayloadFactory { _, _, _ ->
        PayloadResult(rendezvous = mockk(relaxed = true), codeData = emptyList())
    }

    private val transactor = SendGiftCardTransactor(
        transactionController = transactionController,
        payloadFactory = payloadFactory,
    )

    @Before
    fun setUp() {
        mockkStatic("com.getcode.utils.LoggingKt")
        every { com.getcode.utils.trace(any(), any(), any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkStatic("com.getcode.utils.LoggingKt")
    }

    // region preconditions

    @Test
    fun `start fails when with() not called (no state)`() = runTest {
        val result = transactor.start()
        assertTrue(result.isFailure)
    }

    // endregion

    // region dispose

    @Test
    fun `dispose clears state so subsequent start fails`() = runTest {
        setupWithDefaults()

        transactor.dispose()

        val result = transactor.start()
        assertTrue(result.isFailure)
    }

    // endregion

    // region helpers

    private fun setupWithDefaults() {
        val token = mockk<Token>(relaxed = true) {
            every { address } returns Mint.usdf
        }
        val amount = LocalFiat(
            underlyingTokenAmount = Fiat(quarks = 500L),
            nativeAmount = Fiat(fiat = 5.0, currencyCode = CurrencyCode.USD),
            rate = Rate.oneToOne,
            mint = Mint.usdf,
        )
        val owner = mockk<AccountCluster>(relaxed = true) {
            every { withTimelockForToken(any<Token>()) } returns this
            every { vaultPublicKey } returns Key32.mock
        }
        val giftCard = mockk<GiftCardAccount>(relaxed = true)

        transactor.with(giftCard, amount, token, owner)
    }

    // endregion
}
