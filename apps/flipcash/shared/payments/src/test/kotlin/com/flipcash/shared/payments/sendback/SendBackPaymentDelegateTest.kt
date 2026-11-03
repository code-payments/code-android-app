package com.flipcash.shared.payments.sendback

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.buildReturnPaymentMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentIntentId
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.LiveSecrets
import com.getcode.vendor.Base58
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SendBackPaymentDelegateTest {

    private val resolverController = mockk<ResolverController>()
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val calculator = mockk<VerifiedFiatCalculator>()
    private val exchange = mockk<Exchange>(relaxed = true)

    private val delegate = SendBackPaymentDelegate(
        resolverController, transactionController, tokenCoordinator, calculator, exchange,
    )

    private val chatId = ChatId(ByteArray(32) { 9 })
    private val messageId = 41L
    private val originalSender: ID = List(16) { 1 }
    private val senderOwner = mockk<PublicKey>()
    private val intentBytes = ByteArray(32) { 7 }
    private val token = mockk<Token>(relaxed = true)
    private val source = mockk<AccountCluster>(relaxed = true)
    private val state = mockk<VerifiedState>()
    private val eur = mockk<Rate>()
    private val original = Fiat("5.00", CurrencyCode.EUR)

    init {
        every { exchange.rateFor(CurrencyCode.EUR) } returns eur
        coEvery { resolverController.resolve(userId = originalSender) } returns Result.success(senderOwner)
        coEvery { calculator.compute(any(), any(), any(), any(), any()) } returns
            Result.success(VerifiedFiat(mockk<LocalFiat>(relaxed = true), state))
    }

    private suspend fun pay() = delegate.pay(
        chatId = chatId,
        messageId = messageId,
        payee = originalSender,
        amount = original,
        token = token,
        source = source,
        intentId = PaymentIntentId(intentBytes),
    )

    @Test
    fun `12 the return pays the original's amount to its sender under the server's intent id`() = runTest {
        val amount = slot<Fiat>()
        val rate = slot<Rate>()
        coEvery { calculator.compute(capture(amount), any(), any(), capture(rate), any()) } returns
            Result.success(VerifiedFiat(mockk<LocalFiat>(relaxed = true), state))
        val metadata = slot<ByteArray>()
        val intentId = slot<PublicKey>()
        coEvery {
            transactionController.directTransfer(any(), token, source, senderOwner, capture(metadata), any(), capture(intentId))
        } returns Result.success(mockk())

        assertTrue(pay().isSuccess)

        // Native amount and currency are the original's; the token count comes from today's rate.
        assertEquals(original, amount.captured)
        assertSame(eur, rate.captured)
        assertContentEquals(intentBytes, intentId.captured.byteArray)
        assertContentEquals(buildReturnPaymentMetadata(chatId, messageId), metadata.captured)
    }

    @Test
    fun `price is not capped by any balance, so its quarks are what the return needs`() = runTest {
        val balance = slot<Fiat?>()
        coEvery { calculator.compute(any(), any(), captureNullable(balance), any(), any()) } returns
            Result.success(VerifiedFiat(mockk<LocalFiat>(relaxed = true), state))

        assertTrue(delegate.price(original, token).isSuccess)
        assertEquals(null, balance.captured)
    }

    private val intentBase58 = Base58.encode(intentBytes)

    private suspend fun logLinesFor(outcome: Result<Unit>): List<String> {
        coEvery {
            transactionController.directTransfer(any(), token, source, senderOwner, any(), any(), any())
        } returns outcome.map { mockk() }
        val lines = mutableListOf<String>()
        delegate.log = { message, _ -> lines += message }
        pay()
        return lines
    }

    @Test
    fun `18 a successful return logs the intent id masked only`() = runTest {
        val line = logLinesFor(Result.success(Unit)).single()

        assertFalse(intentBase58 in line, line)
        assertTrue(line.endsWith("…${intentBase58.takeLast(6)}"), line)
        assertTrue(LiveSecrets.isLive(intentBase58))
        LiveSecrets.release(intentBase58)
    }

    @Test
    fun `18 a failed return logs the intent id masked only`() = runTest {
        val line = logLinesFor(Result.failure(IllegalStateException("boom"))).single()

        assertFalse(intentBase58 in line, line)
        assertTrue(LiveSecrets.isLive(intentBase58))
        LiveSecrets.release(intentBase58)
    }
}
