package com.getcode.opencode.internal.network.pollers

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.services.SwapService
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.core.errors.SwapError
import com.getcode.opencode.model.transactions.SwapMetadata
import com.getcode.opencode.model.transactions.SwapState
import com.getcode.utils.NotifiableError
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SwapPollerTest {

    private val owner = mockk<Ed25519.KeyPair>(relaxed = true)
    private val swapId = SwapId.generate()

    private fun serviceReturning(state: SwapState): SwapService {
        val metadata = mockk<SwapMetadata> { every { this@mockk.state } returns state }
        return mockk<SwapService> {
            coEvery { getSwap(swapId, owner) } returns Result.success(metadata)
        }
    }

    @Test
    fun terminalCancelledThrowsNonNotifiableTerminalError() = runTest {
        val poller = SwapPoller(serviceReturning(SwapState.CANCELLED))

        val result = poller.pollUntil(
            swapId = swapId,
            owner = owner,
            targetState = SwapState.FINALIZED,
            maxAttempts = 3,
            interval = 0.seconds,
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<SwapError.Terminal>(error)
        assertEquals(SwapState.CANCELLED, error.state)
        assertFalse(error is NotifiableError)
    }

    @Test
    fun exhaustingAttemptsThrowsNonNotifiableTimeoutError() = runTest {
        // FUNDING is neither the target, terminal, nor UNKNOWN — poller recurses until timeout.
        val poller = SwapPoller(serviceReturning(SwapState.FUNDING))

        val result = poller.pollUntil(
            swapId = swapId,
            owner = owner,
            targetState = SwapState.FINALIZED,
            maxAttempts = 2,
            interval = 0.seconds,
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<SwapError.Timeout>(error)
        assertFalse(error is NotifiableError)
    }
}
