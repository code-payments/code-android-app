package com.flipcash.app.tokens

import com.flipcash.app.core.MainCoroutineRule
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class BalancePollerTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(StandardTestDispatcher())

    private val mint = Mint("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAaaaaaaaaaaa")

    private fun fiat(usd: Double): Fiat = Fiat(usd, CurrencyCode.USD)

    private class CoordinatorFake(
        val balance: MutableStateFlow<Fiat>,
    ) {
        var updateCount: Int = 0
    }

    /**
     * Wires a mocked [TokenCoordinator] around a simple state flow so tests can
     * mutate the observable balance and count refresh calls.
     */
    private fun fakeCoordinator(initial: Fiat = Fiat.Zero): Pair<TokenCoordinator, CoordinatorFake> {
        val state = MutableStateFlow(initial)
        val fake = CoordinatorFake(state)
        val coordinator = mockk<TokenCoordinator>()
        every { coordinator.balanceForToken(any<Mint>()) } returns state.map { it }
        coEvery { coordinator.updateTokenAccount(any<Mint>()) } answers {
            fake.updateCount += 1
        }
        return coordinator to fake
    }

    @Test
    fun `returns new balance when it changes on a later attempt`() = runTest {
        val (coordinator, fake) = fakeCoordinator(initial = Fiat.Zero)
        val poller = BalancePoller(coordinator)

        val deferred = async {
            poller.awaitBalanceChange(
                mint = mint,
                maxAttempts = 5,
                interval = 10.seconds,
            )
        }

        // First attempt happens immediately — balance still Zero, so poller delays.
        advanceTimeBy(1.seconds)
        assertEquals(1, fake.updateCount)
        assertTrue(!deferred.isCompleted)

        // Flip the balance *before* the second attempt fires.
        fake.balance.value = fiat(5.0)
        advanceUntilIdle()

        val result = deferred.await()
        assertTrue(result.isSuccess)
        assertEquals(fiat(5.0), result.getOrNull())
        assertEquals(2, fake.updateCount)
    }

    @Test
    fun `times out after maxAttempts when balance never changes`() = runTest {
        val (coordinator, fake) = fakeCoordinator(initial = Fiat.Zero)
        val poller = BalancePoller(coordinator)

        val maxAttempts = 3
        val interval = 10.seconds

        val startTime = testScheduler.currentTime
        val result = poller.awaitBalanceChange(
            mint = mint,
            maxAttempts = maxAttempts,
            interval = interval,
        )
        val elapsed = testScheduler.currentTime - startTime

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is BalancePollError.Timeout)
        assertEquals(maxAttempts, error.attempts)
        assertEquals(maxAttempts, fake.updateCount)
        // Poller delays after each failed attempt before recursing; the guard fires at attempt == maxAttempts.
        assertEquals(maxAttempts * interval.inWholeMilliseconds, elapsed)

        coVerify(exactly = maxAttempts) { coordinator.updateTokenAccount(mint) }
    }

    @Test
    fun `custom predicate ignores decreases when requiring an increase`() = runTest {
        val baseline = fiat(10.0)
        val (coordinator, fake) = fakeCoordinator(initial = baseline)
        val poller = BalancePoller(coordinator)

        val deferred = async {
            poller.awaitBalanceChange(
                mint = mint,
                baseline = baseline,
                predicate = { base, current -> current.decimalValue > base.decimalValue },
                maxAttempts = 5,
                interval = 10.seconds,
            )
        }

        // First attempt: still baseline — not satisfied.
        advanceTimeBy(1.seconds)
        assertTrue(!deferred.isCompleted)

        // Balance *decreases* — predicate should still be unsatisfied.
        fake.balance.value = fiat(4.0)
        advanceTimeBy(10.seconds)
        assertTrue(!deferred.isCompleted)

        // Balance finally goes above baseline.
        fake.balance.value = fiat(15.0)
        advanceUntilIdle()

        val result = deferred.await()
        assertTrue(result.isSuccess)
        assertEquals(fiat(15.0), result.getOrNull())
    }
}
