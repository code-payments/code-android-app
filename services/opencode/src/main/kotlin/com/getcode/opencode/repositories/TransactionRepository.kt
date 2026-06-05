package com.getcode.opencode.repositories

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.opencode.model.transactions.StatefulSwapRequest
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.model.transactions.WithdrawalAvailability
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Instant

interface TransactionRepository {

    suspend fun submitIntent(
        scope: CoroutineScope,
        intent: IntentType,
        owner: KeyPair,
    ): Result<IntentType>

    suspend fun getIntentMetadata(
        intentId: PublicKey,
        owner: KeyPair
    ): Result<TransactionMetadata>

    suspend fun getLimits(
        owner: KeyPair,
        consumedSince: Instant,
    ): Result<Limits>

    suspend fun withdrawalAvailability(
        destination: PublicKey,
        mint: Mint,
    ): Result<WithdrawalAvailability>

    suspend fun voidGiftCard(
        owner: KeyPair,
        giftCardVault: PublicKey
    ): Result<Unit>

    suspend fun buy(
        scope: CoroutineScope,
        owner: AccountCluster,
        amount: LocalFiat,
        feeAmount: LocalFiat? = null,
        of: Token,
        swapId: SwapId? = null,
        verifiedState: VerifiedState,
        source: SwapFundingSource = SwapFundingSource.SubmitIntent(),
        fund: (suspend (StatefulSwapRequest) -> Result<Unit>)? = null,
    ): Result<SwapId>

    suspend fun sell(
        scope: CoroutineScope,
        owner: AccountCluster,
        amount: LocalFiat,
        of: Token,
        verifiedState: VerifiedState,
    ): Result<SwapId>

    suspend fun withdrawUsdf(
        scope: CoroutineScope,
        amount: LocalFiat,
        fee: LocalFiat,
        owner: AccountCluster,
        destinationOwner: PublicKey,
        verifiedState: VerifiedState,
    ): Result<SwapId>

    suspend fun sweepUsdc(
        scope: CoroutineScope,
        owner: AccountCluster,
        amount: Long,
    ): Result<Unit>
}