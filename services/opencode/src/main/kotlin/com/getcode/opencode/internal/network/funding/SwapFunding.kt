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
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class SwapFunding @Inject constructor(
    private val transactionApi: TransactionApi,
) {
    suspend fun fund(
        scope: CoroutineScope,
        owner: AccountCluster,
        request: SwapRequest,
    ): Result<IntentType> {
        val fundingIntent = IntentFundSwap.create(
            intentId = PublicKey(request.fundingIntentId),
            sourceCluster = request.owner,
            amount = request.amount,
            fromMint = request.direction.sourceMint
        )

        val executor = IntentExecutor(transactionApi)
        return executor.execute(scope, fundingIntent, owner.authority.keyPair)
    }
}