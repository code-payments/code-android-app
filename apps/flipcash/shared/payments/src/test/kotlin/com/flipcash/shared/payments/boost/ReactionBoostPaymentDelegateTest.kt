package com.flipcash.shared.payments.boost

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.PublicKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReactionBoostPaymentDelegateTest {

    private val resolverController = mockk<ResolverController>()
    private val transactionController = mockk<TransactionController>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val calculator = mockk<VerifiedFiatCalculator>()
    private val exchange = mockk<Exchange>(relaxed = true)

    private val chatId = ChatId(ByteArray(32) { 9 })
    private val token = mockk<Token>(relaxed = true)
    private val source = mockk<AccountCluster>(relaxed = true)
    private val state = mockk<VerifiedState>()

    private val sender: ID = List(32) { 2 }
    private val creator: ID = List(32) { 1 }
    private val senderKey = mockk<PublicKey>()
    private val creatorKey = mockk<PublicKey>()

    private val delegate = ReactionBoostPaymentDelegate(
        resolverController, transactionController, tokenCoordinator, calculator, exchange,
    )

    private val requested = mutableListOf<Fiat>()

    private fun pricesAt(vararg states: VerifiedState?) {
        val queue = ArrayDeque(states.toList())
        val amount = slot<Fiat>()
        coEvery { calculator.compute(capture(amount), any(), any(), any(), any()) } answers {
            requested += amount.captured
            Result.success(VerifiedFiat(mockk<LocalFiat>(relaxed = true), queue.removeFirstOrNull() ?: state))
        }
    }

    private fun split(totalCents: Long) =
        ReactionBoostSplit.plan(payer = List(32) { 3 }, sender = sender, chat = BoostChat.Group(creator), totalCents = totalCents)
            as ReactionBoostPlan.Pay

    private suspend fun boost(plan: ReactionBoostPlan.Pay) =
        delegate.boost(chatId, messageId = 7, emoji = "💯", plan = plan, token = token, source = source)

    init {
        coEvery { resolverController.resolve(userId = sender) } returns Result.success(senderKey)
        coEvery { resolverController.resolve(userId = creator) } returns Result.success(creatorKey)
        coEvery { transactionController.directMultiTransfer(any(), any(), any(), any(), any(), any()) } returns
            Result.success(mockk())
        coEvery { transactionController.directTransfer(any(), any(), any(), any(), any(), any()) } returns
            Result.success(mockk())
    }

    @Test
    fun `two payees go in one multi-transfer, sender then creator, each priced from its own cents`() = runTest {
        pricesAt(state, state)

        assertTrue(boost(split(1001)).isSuccess)

        assertEquals(listOf(centsToUsd(801), centsToUsd(200)), requested)
        val payments = slot<List<Pair<PublicKey, LocalFiat>>>()
        coVerify { transactionController.directMultiTransfer(capture(payments), state, token, source, any(), any()) }
        assertEquals(listOf(senderKey, creatorKey), payments.captured.map { it.first })
        coVerify(exactly = 0) { transactionController.directTransfer(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `one payee is a single direct transfer of the whole`() = runTest {
        pricesAt(state)
        val plan = ReactionBoostSplit.plan(payer = creator, sender = sender, chat = BoostChat.Group(creator), totalCents = 500)
            as ReactionBoostPlan.Pay

        assertTrue(boost(plan).isSuccess)

        assertEquals(listOf(centsToUsd(500)), requested)
        coVerify { transactionController.directTransfer(any(), token, source, senderKey, any(), any()) }
        coVerify(exactly = 0) { transactionController.directMultiTransfer(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a failed payment is reported as a failure`() = runTest {
        pricesAt(state, state)
        coEvery { transactionController.directMultiTransfer(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("denied"))

        assertTrue(boost(split(1000)).isFailure)
    }

    @Test
    fun `shares that keep pricing at different states fail as stale before paying`() = runTest {
        pricesAt(state, mockk(), state, mockk())

        val result = boost(split(1000))

        assertTrue(result.exceptionOrNull() is ComputeVerifiedFiatError.StaleRate)
        coVerify(exactly = 0) { transactionController.directMultiTransfer(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a state refresh between shares is retried once`() = runTest {
        pricesAt(state, mockk(), state, state)

        assertTrue(boost(split(1000)).isSuccess)
    }
}
