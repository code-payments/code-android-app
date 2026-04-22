package com.getcode.opencode.internal.network.funding

import com.getcode.opencode.internal.network.api.TransactionApi
import com.getcode.opencode.internal.network.api.intents.IntentFundSwap
import com.getcode.opencode.internal.network.executors.IntentExecutor
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.plus
import com.getcode.opencode.model.transactions.SwapRequest
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.solana.keys.PublicKey
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

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
            amount = request.swapAmount + (request.feeAmount ?: LocalFiat.Zero),
            fromMint = request.direction.sourceMint,
            verifiedState = request.verifiedState,
        )

        val executor = IntentExecutor(transactionApi)
        return executor.execute(scope, fundingIntent, owner.authority.keyPair)
    }
}