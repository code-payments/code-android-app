package com.getcode.opencode.internal.network.funding

import com.getcode.opencode.internal.network.api.TransactionApi
import com.getcode.opencode.internal.network.api.intents.IntentFundSwap
import com.getcode.opencode.internal.network.executors.IntentExecutor
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.transactions.StatefulSwapRequest
import com.getcode.opencode.model.transactions.VerifiedExchangeState
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
        request: StatefulSwapRequest,
    ): Result<IntentType> = fund(
        scope = scope,
        owner = owner,
        intentId = PublicKey(request.fundingIntentId),
        sourceCluster = request.owner,
        amount = request.totalTransferAmount,
        from = request.route.sourceMint,
        verifiedState = request.verifiedState,
    )

    /**
     * Submits the funding intent [intentId] moving [amount] of [from] out of [sourceCluster] into
     * the swap's PDA. [amount] is the swap's amount plus its fee.
     */
    suspend fun fund(
        scope: CoroutineScope,
        owner: AccountCluster,
        intentId: PublicKey,
        sourceCluster: AccountCluster,
        amount: LocalFiat,
        from: Token,
        verifiedState: VerifiedExchangeState,
    ): Result<IntentType> {
        val fundingIntent = IntentFundSwap.create(
            intentId = intentId,
            sourceCluster = sourceCluster,
            amount = amount,
            fromMint = from,
            verifiedState = verifiedState,
        )

        val executor = IntentExecutor(transactionApi)
        return executor.execute(scope, fundingIntent, owner.authority.keyPair)
    }
}
