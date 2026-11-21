package com.flipcash.shared.payments.request

import com.flipcash.app.tokens.TokenCoordinator
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration

class CollectionTopUpTest {

    private val requestId: ID = List(32) { 4 }
    private val moony = Mint(List(32) { 7 })
    private val owner = mockk<AccountCluster>(relaxed = true)
    private val store = InMemoryCollectionFundingStore()
    private var dollars = 2_010_000L
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true).also {
        every { it.currentBalance(Mint.usdf) } answers { Fiat(dollars, CurrencyCode.USD) }
        coEvery { it.update() } answers { Unit }
    }
    private val rail = FakeRail()
    private val locks = CollectionTopUpLocks()

    private fun topUp() = CollectionTopUp(rail, store, tokenCoordinator, locks).also { it.settleInterval = Duration.ZERO }

    /** A Coinbase stand-in: [land] is what the sweep adds to Dollars once the order delivers. */
    private inner class FakeRail : CollectionTopUpRail {
        val ordered = mutableListOf<Long>()
        var gate: Throwable? = null
        var payment = TopUpPayment.Succeeded
        var delivery = TopUpDelivery.Delivered
        var land = 0L
        var keptWhenPaying: PendingTopUpRecord? = null
        var paying: CompletableDeferred<Unit>? = null
        var sweeps = 0

        override suspend fun order(usdCents: Long, checkGates: Boolean): Result<TopUpOrder> {
            gate?.let { return Result.failure(it) }
            ordered += usdCents
            return Result.success(TopUpOrder("order-${ordered.size}", "https://pay"))
        }

        override suspend fun pay(order: TopUpOrder): TopUpPayment {
            keptWhenPaying = store.topUps[requestId]
            paying?.await()
            return payment
        }

        override suspend fun awaitDelivered(orderId: String): TopUpDelivery = delivery

        override fun sweep(owner: AccountCluster) {
            sweeps++
            dollars += land
            land = 0
        }
    }

    @Test
    fun `a top-up orders exact cents, is kept before Google Pay opens, and lands`() = runTest {
        rail.land = 6_370_000

        val result = topUp().topUp(requestId, usdCents = 637, payingCurrency = moony, owner = owner)

        assertEquals(TopUpResult.Landed, result)
        assertEquals(listOf(637L), rail.ordered)
        assertEquals(PendingTopUpRecord("order-1", 637, 2_010_000, payingCurrency = moony), rail.keptWhenPaying)
        assertNull(store.pendingTopUp(requestId))
    }

    @Test
    fun `a second tap while Google Pay is open waits on the first and orders nothing`() = runTest {
        rail.land = 5_000_000
        rail.paying = CompletableDeferred()
        val subject = topUp()

        val first = async { subject.exclusive(requestId) { subject.topUp(requestId, 500, moony, owner) } }
        testScheduler.runCurrent()
        val second = async { subject.exclusive(requestId) { subject.topUp(requestId, 500, moony, owner) } }
        testScheduler.runCurrent()

        // Google Pay is still open on the first order, and the second tap is held at the lock.
        assertEquals(listOf(500L), rail.ordered)
        assertEquals(false, second.isCompleted)
        second.cancel()
        rail.paying!!.complete(Unit)
        assertEquals(TopUpResult.Landed, first.await())
        assertEquals(listOf(500L), rail.ordered)
    }

    @Test
    fun `a kept top-up is waited on after a relaunch, with no second order`() = runTest {
        val kept = PendingTopUpRecord("order-9", 637, 2_010_000, payingCurrency = moony)
        store.putPendingTopUp(requestId, kept)
        rail.land = 6_370_000

        val relaunched = topUp()
        assertEquals(kept, relaunched.kept(requestId))
        val result = relaunched.resume(requestId, owner)

        assertEquals(TopUpResult.Landed, result)
        assertEquals(emptyList(), rail.ordered)
        assertNull(store.pendingTopUp(requestId))
    }

    @Test
    fun `a retry with a kept top-up waits on it instead of ordering`() = runTest {
        store.putPendingTopUp(requestId, PendingTopUpRecord("order-9", 637, 2_010_000, moony))
        rail.land = 6_370_000

        val result = topUp().topUp(requestId, usdCents = 637, payingCurrency = moony, owner = owner)

        assertEquals(TopUpResult.Landed, result)
        assertEquals(emptyList(), rail.ordered)
    }

    @Test
    fun `Dollars already seen clear the record without waiting on delivery`() = runTest {
        store.putPendingTopUp(requestId, PendingTopUpRecord("order-9", 500, 2_010_000, moony))
        dollars = 2_010_000 + 4_900_000 // 98% of $5.00
        rail.delivery = TopUpDelivery.TimedOut

        assertEquals(TopUpResult.Landed, topUp().resume(requestId, owner))
        assertEquals(0, rail.sweeps)
    }

    @Test
    fun `a short delivery still clears, so the replan can ask`() = runTest {
        rail.land = 4_000_000 // $5.00 ordered, $4.00 arrived

        assertEquals(TopUpResult.Landed, topUp().topUp(requestId, 500, moony, owner))
        assertNull(store.pendingTopUp(requestId))
    }

    @Test
    fun `Google Pay closed before charging clears the record`() = runTest {
        rail.payment = TopUpPayment.NotCharged

        assertEquals(TopUpResult.NotCharged, topUp().topUp(requestId, 500, moony, owner))
        assertNull(store.pendingTopUp(requestId))
    }

    @Test
    fun `an authorized payment that then fails is still waited on`() = runTest {
        rail.payment = TopUpPayment.Unknown
        rail.land = 5_000_000

        assertEquals(TopUpResult.Landed, topUp().topUp(requestId, 500, moony, owner))
    }

    @Test
    fun `an order known to have failed clears the record`() = runTest {
        rail.delivery = TopUpDelivery.Failed

        assertEquals(TopUpResult.Failed, topUp().topUp(requestId, 500, moony, owner))
        assertNull(store.pendingTopUp(requestId))
    }

    @Test
    fun `an order not yet delivered stays kept`() = runTest {
        rail.delivery = TopUpDelivery.TimedOut

        assertEquals(TopUpResult.StillPending, topUp().topUp(requestId, 500, moony, owner))
        assertEquals("order-1", store.pendingTopUp(requestId)?.orderId)
    }

    @Test
    fun `delivered but not yet swept stays kept`() = runTest {
        rail.land = 0

        assertEquals(TopUpResult.StillPending, topUp().topUp(requestId, 500, moony, owner))
        assertEquals("order-1", store.pendingTopUp(requestId)?.orderId)
    }

    @Test
    fun `a purchase gate orders and keeps nothing`() = runTest {
        rail.gate = CollectionTopUpBlocked(TopUpBlock.GooglePayNoPaymentMethod)

        val result = topUp().topUp(requestId, 500, moony, owner)

        assertEquals(TopUpResult.Blocked(TopUpBlock.GooglePayNoPaymentMethod), result)
        assertEquals(emptyList(), rail.ordered)
        assertNull(store.pendingTopUp(requestId))
    }
}
