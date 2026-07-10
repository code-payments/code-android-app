package com.getcode.opencode.internal.network.pollers

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.services.SwapService
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.core.errors.SwapError
import com.getcode.opencode.model.transactions.SwapMetadata
import com.getcode.opencode.model.transactions.SwapState
import com.getcode.solana.keys.base58
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class SwapPoller @Inject constructor(
    private val swapService: SwapService,
) {

    suspend fun pollUntil(
        swapId: SwapId,
        owner: Ed25519.KeyPair,
        targetState: SwapState,
        maxAttempts: Int,
        interval: Duration,
    ): Result<SwapMetadata> = runCatching {
        pollSwapUntil(
            swapId = swapId,
            owner = owner,
            maxAttempts = maxAttempts,
            interval = interval,
            targetState = targetState,
        )
    }


    private suspend fun pollSwapUntil(
        swapId: SwapId,
        owner: Ed25519.KeyPair,
        targetState: SwapState,
        maxAttempts: Int,
        interval: Duration,
        attempt: Int = 0
    ): SwapMetadata {
        if (attempt >= maxAttempts) {
            trace(
                type = TraceType.Error,
                message = "Polling timed out after $maxAttempts attempts, Swap ID: ${swapId.publicKey.base58()}"
            )
            throw SwapError.Timeout()
        }

        val metadata = try {
            swapService.getSwap(swapId, owner).getOrThrow()
        } catch (e: Exception) {
            trace(
                type = TraceType.Error,
                message = "Failed to get swap state: $e, Swap ID: ${swapId.publicKey.base58()}"
            )
            throw SwapError.Other(cause = e)
        }

        return when (metadata.state) {
            targetState -> {
                // Reached desired state
                metadata
            }
            in listOf(SwapState.FAILED, SwapState.CANCELLED) -> {
                trace(
                    type = TraceType.Error,
                    message = "Swap reached terminal state: ${metadata.state}, Swap ID: ${swapId.publicKey.base58()}"
                )
                throw SwapError.Terminal(metadata.state)
            }
            SwapState.UNKNOWN -> {
                trace(
                    type = TraceType.Error,
                    message = "Swap in unknown state, Swap ID: ${swapId.publicKey.base58()}"
                )
                throw SwapError.Other()
            }
            else -> {
                // Still in progress, poll again
                trace(
                    type = TraceType.Log,
                    message = "Swap state: ${metadata.state}, waiting for $targetState..., Attempt ${attempt + 1}/$maxAttempts"
                )
                delay(interval)
                pollSwapUntil(swapId, owner, targetState, maxAttempts, interval, attempt + 1)
            }
        }
    }
}