package com.getcode.opencode.controllers

import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.opencode.model.transactions.SwapMetadata
import com.getcode.opencode.model.transactions.StatefulSwapRequest
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
        amount: VerifiedFiat,
        feeAmount: LocalFiat? = null,
        swapId: SwapId? = null,
        of: Token,
        source: SwapFundingSource = SwapFundingSource.SubmitIntent(),
        fund: (suspend (StatefulSwapRequest) -> Result<Unit>)? = null,
    ): Result<SwapId>

    /**
     * Sells [amount] of [of] into Dollars. Pass [swapId] and a [SwapFundingSource.SubmitIntent] to
     * start the swap under ids the caller kept; left out, each call mints its own. The server makes
     * no promise to dedupe a second start, so a swap it already has in CREATED is funded with
     * [fundSwap] instead of started again.
     */
    suspend fun sell(
        owner: AccountCluster,
        amount: VerifiedFiat,
        of: Token,
        swapId: SwapId? = null,
        source: SwapFundingSource = SwapFundingSource.SubmitIntent(),
    ): Result<SwapId>

    /**
     * Swaps one launchpad currency directly for another existing currency (neither side the core
     * mint). To fund a brand-new currency launch by swapping from another currency, use
     * [TransactionController.launchToken] — that path is treasury-funded and creates the
     * destination during the swap.
     */
    suspend fun swap(
        owner: AccountCluster,
        amount: VerifiedFiat,
        feeAmount: LocalFiat? = null,
        from: Token,
        to: Token,
        swapId: SwapId? = null,
        source: SwapFundingSource = SwapFundingSource.SubmitIntent(),
        fund: (suspend (StatefulSwapRequest) -> Result<Unit>)? = null,
    ): Result<SwapId>

    /**
     * Funds a swap the server already has in CREATED (started, funding never landed) under the
     * funding intent id the server recorded for it, [fundingId]. [amount] prices the swap's
     * recorded amount plus fee, in [from].
     */
    suspend fun fundSwap(
        owner: AccountCluster,
        from: Token,
        amount: VerifiedFiat,
        fundingId: List<Byte>,
    ): Result<Unit>

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
        amount: VerifiedFiat,
        mint: Mint,
        owner: AccountCluster,
        destination: PublicKey,
        destinationOwner: PublicKey?,
        fee: Fiat? = null,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    ): Result<IntentType>

    suspend fun withdrawUsdf(
        amount: VerifiedFiat,
        owner: AccountCluster,
        destination: PublicKey,
        destinationOwner: PublicKey,
        fee: LocalFiat,
    ): Result<SwapId>

    suspend fun checkWithdrawalAvailability(
        address: String,
        mint: Mint,
    ): Result<WithdrawalAvailability>

    suspend fun swapUsdc(
        owner: AccountCluster,
        amount: Long,
    ): Result<Unit>
}
