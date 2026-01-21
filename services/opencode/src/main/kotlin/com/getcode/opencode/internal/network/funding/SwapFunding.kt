package com.getcode.opencode.internal.network.funding

import com.getcode.opencode.internal.network.api.TransactionApi
import com.getcode.opencode.internal.network.api.intents.IntentFundSwap
import com.getcode.opencode.internal.network.executors.IntentExecutor
import com.getcode.opencode.internal.network.services.SwapService
import com.getcode.opencode.internal.network.services.TransactionService
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.errors.SwapError
import com.getcode.opencode.model.transactions.SwapMetadata
import com.getcode.opencode.model.transactions.SwapRequest
import com.getcode.opencode.model.transactions.SwapState
import com.getcode.solana.keys.base58
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class SwapFunding @Inject constructor(
    private val swapService: SwapService,
    private val transactionApi: TransactionApi,
) {
    suspend fun fund(
        scope: CoroutineScope,
        owner: AccountCluster,
        request: SwapRequest,
    ): Result<SwapMetadata> {
        val fundingIntent = IntentFundSwap.create(
            intentId = request.fundingIntentId,
            sourceCluster = request.owner,
            amount = request.amount,
            fromMint = request.direction.sourceMint
        )

        val executor = IntentExecutor(transactionApi)
        return executor.execute(scope, fundingIntent, owner.authority.keyPair)
            .map {
                pollSwapUntilFunded(
                    swapId = request.swapId,
                    owner = request.owner,
                    maxAttempts = 30,
                    interval = 1.seconds
                )
            }
    }

    private suspend fun pollSwapUntilFunded(
        swapId: SwapId,
        owner: AccountCluster,
        maxAttempts: Int,
        interval: Duration,
        attempt: Int = 0
    ): SwapMetadata {
        if (attempt >= maxAttempts) {
            trace(
                type = TraceType.Error,
                message = "Polling timed out after $maxAttempts attempts, Swap ID: ${swapId.publicKey.base58()}"
            )
            throw SwapError.Other()
        }

        val metadata = try {
            swapService.getSwap(swapId, owner.authority.keyPair).getOrThrow()
        } catch (e: Exception) {
            trace(
                type = TraceType.Error,
                message = "Failed to get swap state: $e, Swap ID: ${swapId.publicKey.base58()}"
            )
            throw SwapError.Other(cause = e)
        }

        return when (metadata.state) {
            SwapState.FUNDED,
            SwapState.FINALIZED -> {
                // Swap is ready to execute or already finalized
                metadata
            }

            SwapState.FAILED,
            SwapState.CANCELLED -> {
                trace(
                    type = TraceType.Error,
                    message = "Swap reached terminal state: ${metadata.state}, Swap ID: ${swapId.publicKey.base58()}"
                )
                throw SwapError.Other()
            }

            SwapState.CREATED,
            SwapState.FUNDING,
            SwapState.SUBMITTING,
            SwapState.CANCELLING -> {
                // Still in progress, poll again
                trace(
                    type = TraceType.Log,
                    message = "Swap state: ${metadata.state}, polling again..., Attempt ${attempt + 1}/$maxAttempts"
                )
                delay(interval)
                pollSwapUntilFunded(swapId, owner, maxAttempts, interval, attempt + 1)
            }

            SwapState.UNKNOWN -> {
                trace(
                    type = TraceType.Error,
                    message = "Swap in unknown state, Swap ID: ${swapId.publicKey.base58()}"
                )
                throw SwapError.Other()
            }
        }
    }
}