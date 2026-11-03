package com.flipcash.shared.payments.group

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.buildGroupPaymentMetadata
import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.PublicKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GroupSendPaymentDelegateTest {

    private val resolverController = mockk<ResolverController>()
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val calculator = mockk<VerifiedFiatCalculator>()
    private val exchange = mockk<Exchange>(relaxed = true)

    private val chatId = ChatId(ByteArray(32) { 9 })
    private val token = mockk<Token>(relaxed = true)
    private val source = mockk<AccountCluster>(relaxed = true)
    private val state = mockk<VerifiedState>()
    private val cad = mockk<Rate>()

    private val members: List<ID> = List(6) { i -> List(32) { (i + 1).toByte() } }
    private val keys: Map<ID, PublicKey> = members.associateWith { mockk<PublicKey>() }

    private val delegate = GroupSendPaymentDelegate(
        resolverController, transactionController, tokenCoordinator, calculator, exchange,
    )

    private val requested = mutableListOf<Fiat>()
    private val rates = mutableListOf<Rate>()

    private fun pricesAt(vararg states: VerifiedState?) {
        val queue = ArrayDeque(states.toList())
        val amount = slot<Fiat>()
        val rate = slot<Rate>()
        coEvery { calculator.compute(capture(amount), any(), any(), capture(rate), any()) } answers {
            requested += amount.captured
            rates += rate.captured
            Result.success(VerifiedFiat(mockk<LocalFiat>(relaxed = true), queue.removeFirstOrNull() ?: state))
        }
    }

    private suspend fun send(recipients: List<ID>, amount: String) = delegate.send(
        chatId = chatId,
        recipients = recipients,
        amount = Fiat(amount, CurrencyCode.CAD),
        token = token,
        source = source,
    )

    init {
        every { exchange.rateFor(CurrencyCode.CAD) } returns cad
        members.forEach { coEvery { resolverController.resolve(userId = it) } returns Result.success(keys.getValue(it)) }
        coEvery { transactionController.directMultiTransfer(any(), any(), any(), any(), any(), any()) } returns
            Result.success(mockk())
        coEvery { transactionController.directTransfer(any(), any(), any(), any(), any(), any()) } returns
            Result.success(mockk())
    }

    @Test
    fun `several recipients go in one multi-transfer, in selection order, each priced at the entered amount`() = runTest {
        pricesAt()
        val payments = slot<List<Pair<PublicKey, LocalFiat>>>()
        val metadata = slot<ByteArray>()
        coEvery {
            transactionController.directMultiTransfer(capture(payments), state, token, source, capture(metadata), any())
        } returns Result.success(mockk())

        assertTrue(send(members, "1.00").isSuccess)

        assertEquals(List(6) { Fiat("1.00", CurrencyCode.CAD) }, requested)
        assertTrue(rates.all { it === cad })
        assertEquals(members.map { keys.getValue(it) }, payments.captured.map { it.first })
        assertContentEquals(buildGroupPaymentMetadata(chatId), metadata.captured)
        coVerify(exactly = 6) { tokenCoordinator.subtract(token, any<LocalFiat>()) }
    }

    /** Resolving is a round trip per recipient; a send to the group must not pay for them in turn. */
    @Test
    fun `recipients resolve concurrently and keep selection order when they finish out of order`() = runTest {
        pricesAt()
        members.forEachIndexed { i, member ->
            coEvery { resolverController.resolve(userId = member) } coAnswers {
                delay((members.size - i) * 100L)
                Result.success(keys.getValue(member))
            }
        }
        val payments = slot<List<Pair<PublicKey, LocalFiat>>>()
        coEvery {
            transactionController.directMultiTransfer(capture(payments), state, token, source, any(), any())
        } returns Result.success(mockk())

        assertTrue(send(members, "1.00").isSuccess)

        assertEquals(members.map { keys.getValue(it) }, payments.captured.map { it.first })
        // Sequential would take 600 + 500 + ... + 100 = 2100ms; concurrent takes the slowest.
        assertEquals(members.size * 100L, testScheduler.currentTime)
    }

    @Test
    fun `a recipient that does not resolve sends nothing`() = runTest {
        pricesAt()
        coEvery { resolverController.resolve(userId = members[3]) } returns
            Result.failure(IllegalStateException("unresolved"))

        // Coroutine stack-trace recovery can rethrow a copy, so compare type and message.
        val error = assertIs<IllegalStateException>(send(members, "1.00").exceptionOrNull())
        assertEquals("unresolved", error.message)
        coVerify(exactly = 0) { transactionController.directMultiTransfer(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { tokenCoordinator.subtract(any(), any<LocalFiat>()) }
    }

    @Test
    fun `one recipient is a single transfer with group metadata`() = runTest {
        pricesAt()
        val metadata = slot<ByteArray>()
        coEvery {
            transactionController.directTransfer(any(), token, source, keys.getValue(members[0]), capture(metadata), any())
        } returns Result.success(mockk())

        assertTrue(send(members.take(1), "5.00").isSuccess)

        assertEquals(listOf(Fiat("5.00", CurrencyCode.CAD)), requested)
        assertContentEquals(buildGroupPaymentMetadata(chatId), metadata.captured)
        coVerify(exactly = 0) { transactionController.directMultiTransfer(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `parts priced at different states twice fail as stale and send nothing`() = runTest {
        val other = mockk<VerifiedState>()
        pricesAt(state, other, state, other)

        assertIs<ComputeVerifiedFiatError.StaleRate>(send(members.take(2), "1.00").exceptionOrNull())
        coVerify(exactly = 0) { transactionController.directMultiTransfer(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `no rate for the currency sends nothing`() = runTest {
        pricesAt()
        every { exchange.rateFor(CurrencyCode.CAD) } returns null

        assertTrue(send(members.take(2), "1.00").isFailure)
        assertTrue(requested.isEmpty())
    }

    @Test
    fun `parts that price above the balance send nothing and fail as insufficient balance`() = runTest {
        val part = mockk<LocalFiat>()
        every { part.nativeAmount } returns Fiat("1.00", CurrencyCode.CAD)
        coEvery { calculator.compute(any(), any(), any(), any(), any()) } returns
            Result.success(VerifiedFiat(part, state))
        val balance = mockk<Fiat>()
        every { balance.convertingTo(cad) } returns Fiat("1.50", CurrencyCode.CAD)
        every { tokenCoordinator.balanceForToken(token) } returns balance

        assertIs<GroupSendInsufficientBalance>(send(members.take(2), "1.00").exceptionOrNull())
        coVerify(exactly = 0) { transactionController.directMultiTransfer(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an amount below the minimum fails as below minimum and sends nothing`() = runTest {
        coEvery { calculator.compute(any(), any(), any(), any(), any()) } returns
            Result.failure(ComputeVerifiedFiatError.AmountBelowMinimum())

        assertIs<ComputeVerifiedFiatError.AmountBelowMinimum>(send(members.take(2), "0.01").exceptionOrNull())
        coVerify(exactly = 0) { transactionController.directMultiTransfer(any(), any(), any(), any(), any(), any()) }
    }
}
