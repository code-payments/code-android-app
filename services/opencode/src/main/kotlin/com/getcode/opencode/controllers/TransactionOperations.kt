package com.getcode.opencode.controllers

import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.opencode.model.transactions.SwapMetadata
import com.getcode.opencode.model.transactions.SwapRequest
import com.getcode.opencode.model.transactions.SwapState
import com.getcode.opencode.model.transactions.WithdrawalAvailability
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface TransactionOperations {
    val limits: StateFlow<Limits?>
    val areLimitsStale: Boolean

    suspend fun updateLimits(owner: AccountCluster, force: Boolean = false)

    suspend fun buy(
        owner: AccountCluster,
        amount: LocalFiat,
        swapId: SwapId? = null,
        of: Token,
        source: SwapFundingSource = SwapFundingSource.SubmitIntent(),
        fund: (suspend (SwapRequest) -> Result<Unit>)? = null,
    ): Result<SwapId>

    suspend fun sell(
        owner: AccountCluster,
        amount: LocalFiat,
        of: Token,
    ): Result<SwapId>

    suspend fun pollSwapForState(
        swapId: SwapId,
        owner: AccountCluster,
        targetState: SwapState,
        maxAttempts: Int = 90,
        interval: Duration = 1.seconds,
    ): Result<SwapMetadata>

    suspend fun cancelRemoteSend(
        owner: AccountCluster,
        vault: PublicKey,
    ): Result<Unit>

    suspend fun withdraw(
        amount: LocalFiat,
        mint: Mint,
        owner: AccountCluster,
        destination: PublicKey,
        destinationOwner: PublicKey?,
        fee: Fiat? = null,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    ): Result<IntentType>

    suspend fun checkWithdrawalAvailability(
        address: String,
        mint: Mint,
    ): Result<WithdrawalAvailability>
}
