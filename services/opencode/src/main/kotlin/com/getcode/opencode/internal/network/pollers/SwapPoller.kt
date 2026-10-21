package com.getcode.opencode.internal.network.pollers

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.services.SwapService
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.core.errors.GetSwapError
import com.getcode.opencode.model.core.errors.SwapError
import com.getcode.opencode.model.transactions.SwapMetadata
import com.getcode.opencode.model.transactions.SwapState
import com.getcode.solana.keys.base58
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CancellationException
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Server errors (CodeServerError) are Throwables, not Exceptions.
            // DENIED and auth failures are permanent: retrying never changes them.
            // NOT_FOUND (replica lag) and transient RPC/network errors are not terminal:
            // keep polling until the attempt budget runs out, which surfaces as Timeout.
            val denial = accessDenialReason(e)
            if (denial != null) {
                trace(
                    type = TraceType.Error,
                    message = "Swap access denied ($denial), not retrying, Swap ID: ${swapId.publicKey.base58()}"
                )
                throw SwapError.AccessDenied(swapId.publicKey.base58(), denial)
            }
            trace(
                type = TraceType.Error,
                message = "Failed to get swap state, retrying: $e, Attempt ${attempt + 1}/$maxAttempts, Swap ID: ${swapId.publicKey.base58()}"
            )
            delay(interval)
            return pollSwapUntil(swapId, owner, targetState, maxAttempts, interval, attempt + 1)
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
                throw SwapError.UnknownState()
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

    /** Non-null when [error] is a permanent GetSwap failure; classified on type/status code only. */
    private fun accessDenialReason(error: Throwable): String? {
        if (error is GetSwapError.Denied) return "DENIED"
        return generateSequence(error) { it.cause }.take(8).firstNotNullOfOrNull { t ->
            val code = when (t) {
                is StatusRuntimeException -> t.status.code
                is StatusException -> t.status.code
                else -> null
            }
            code?.takeIf { it == Status.Code.UNAUTHENTICATED || it == Status.Code.PERMISSION_DENIED }?.name
        }
    }
}
