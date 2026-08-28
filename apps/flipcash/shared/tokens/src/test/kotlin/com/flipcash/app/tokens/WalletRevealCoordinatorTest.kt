package com.flipcash.app.tokens

import com.flipcash.app.core.dispatchers.TestDispatchers
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class WalletRevealCoordinatorTest {

    private val mint = Mint.usdf
    private val tokenCoordinator: TokenCoordinator = mockk(relaxed = true)

    private fun coordinator(dispatchers: TestDispatchers) =
        WalletRevealCoordinator(tokenCoordinator = tokenCoordinator, dispatchers = dispatchers)

    private fun wallet(total: Fiat, mintBalance: Fiat, holdsMint: Boolean) {
        every { tokenCoordinator.currentTotalBalance() } returns total
        every { tokenCoordinator.currentBalance(mint) } returns mintBalance
        every { tokenCoordinator.holdsDisplayableBalance(mint) } returns holdsMint
    }

    @Test
    fun `arm publishes the wallet as it stood when the claim was captured`() =
        runTest {
            val reveal = coordinator(TestDispatchers(testScheduler))
            wallet(total = Fiat(10), mintBalance = Fiat(4), holdsMint = true)

            reveal.capture(mint)
            // The claim credits the balance right after the capture; the reveal must not follow it.
            wallet(total = Fiat(15), mintBalance = Fiat(9), holdsMint = true)
            reveal.arm()

            val pending = requireNotNull(reveal.pending.value)
            assertEquals(mint, pending.mint)
            assertEquals(Fiat(10), pending.totalBefore)
            assertEquals(Fiat(4), pending.mintBalanceBefore)
            assertEquals(false, pending.isNewToken)
        }

    @Test
    fun `a currency the wallet did not hold is flagged as a new card`() = runTest {
        val reveal = coordinator(TestDispatchers(testScheduler))
        wallet(total = Fiat(0), mintBalance = Fiat(0), holdsMint = false)

        reveal.capture(mint)
        reveal.arm()

        assertTrue(requireNotNull(reveal.pending.value).isNewToken)
    }

    @Test
    fun `arming without a capture publishes nothing, and says so`() = runTest {
        val reveal = coordinator(TestDispatchers(testScheduler))

        // What a cash link claim looks like: nothing was scanned, so nothing was captured, and the
        // caller is told there is no wallet to route to.
        assertFalse(reveal.arm())

        assertNull(reveal.pending.value)
    }

    @Test
    fun `a capture is consumed once, so a later tap cannot replay it`() = runTest {
        val dispatchers = TestDispatchers(testScheduler)
        val reveal = coordinator(dispatchers)
        wallet(total = Fiat(10), mintBalance = Fiat(4), holdsMint = true)

        reveal.capture(mint)
        reveal.arm()
        reveal.onDisplayed()
        advanceUntilIdle()
        assertNull(reveal.pending.value)

        reveal.arm()

        assertNull(reveal.pending.value)
    }

    @Test
    fun `the reveal is released once the wallet has held it on screen`() = runTest {
        val reveal = coordinator(TestDispatchers(testScheduler))
        wallet(total = Fiat(10), mintBalance = Fiat(4), holdsMint = true)

        reveal.capture(mint)
        reveal.arm()
        reveal.onDisplayed()

        advanceTimeBy(WalletRevealCoordinator.HoldDuration - 1.milliseconds)
        assertEquals(mint, reveal.pending.value?.mint)

        advanceTimeBy(2.milliseconds)
        assertNull(reveal.pending.value)
    }

    @Test
    fun `a reveal nobody comes to collect releases itself`() = runTest {
        val reveal = coordinator(TestDispatchers(testScheduler))
        wallet(total = Fiat(10), mintBalance = Fiat(4), holdsMint = true)

        reveal.capture(mint)
        reveal.arm()

        // The user backed out before the wallet drew: no onDisplayed ever arrives.
        advanceTimeBy(WalletRevealCoordinator.HoldDuration + 1.milliseconds)
        assertEquals(mint, reveal.pending.value?.mint)

        advanceTimeBy(WalletRevealCoordinator.UnclaimedTimeout)
        assertNull(reveal.pending.value)
    }

    @Test
    fun `a redraw part way through the hold does not extend it`() = runTest {
        val reveal = coordinator(TestDispatchers(testScheduler))
        wallet(total = Fiat(10), mintBalance = Fiat(4), holdsMint = true)

        reveal.capture(mint)
        reveal.arm()
        reveal.onDisplayed()

        advanceTimeBy(WalletRevealCoordinator.HoldDuration - 1.milliseconds)
        reveal.onDisplayed()

        advanceTimeBy(2.milliseconds)
        assertNull(reveal.pending.value)
    }
}
