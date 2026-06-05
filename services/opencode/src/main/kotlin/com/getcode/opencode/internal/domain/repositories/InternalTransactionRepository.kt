package com.getcode.opencode.internal.domain.repositories

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.network.services.TransactionService
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.transactions.StatelessSwapRequest
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.opencode.model.transactions.StatefulSwapRequest
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.model.transactions.WithdrawalAvailability
import com.getcode.opencode.model.core.errors.SubmitIntentError
import com.getcode.opencode.repositories.TransactionRepository
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.ErrorUtils
import com.getcode.utils.network.retryableOrThrow
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds
import javax.inject.Inject

internal class InternalTransactionRepository @Inject constructor(
    private val service: TransactionService,
): TransactionRepository {
    override suspend fun submitIntent(
        scope: CoroutineScope,
        intent: IntentType,
        owner: Ed25519.KeyPair
    ): Result<IntentType> = runCatching {
        retryableOrThrow(
            maxRetries = 3,
            delayDuration = 1.seconds,
            retryIf = { it is SubmitIntentError.StaleState && it.isRaceCondition },
        ) {
            service.submitIntent(scope, intent, owner).getOrThrow()
        }
    }.onFailure {
        ErrorUtils.handleError(it)
    }

    override suspend fun getIntentMetadata(
        intentId: PublicKey,
        owner: Ed25519.KeyPair
    ): Result<TransactionMetadata> = service.getIntentMetadata(intentId, owner)

    override suspend fun getLimits(
        owner: Ed25519.KeyPair,
        consumedSince: Instant
    ): Result<Limits> = service.getLimits(owner, consumedSince)

    override suspend fun withdrawalAvailability(
        destination: PublicKey,
        mint: Mint,
    ): Result<WithdrawalAvailability> = service.withdrawalAvailability(destination, mint)

    override suspend fun voidGiftCard(
        owner: Ed25519.KeyPair,
        giftCardVault: PublicKey
    ): Result<Unit> = service.voidGiftCard(owner, giftCardVault)
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun buy(
        scope: CoroutineScope,
        owner: AccountCluster,
        amount: LocalFiat,
        feeAmount: LocalFiat?,
        of: Token,
        swapId: SwapId?,
        verifiedState: VerifiedState,
        source: SwapFundingSource,
        fund: (suspend (StatefulSwapRequest) -> Result<Unit>)?
    ): Result<SwapId> = service.buy(
        scope = scope,
        swapId = swapId,
        amount = amount,
        feeAmount = feeAmount,
        of = of,
        owner = owner,
        verifiedState = verifiedState,
        source = source,
        fund = fund
    ).onFailure { ErrorUtils.handleError(it) }

    override suspend fun sell(
        scope: CoroutineScope,
        owner: AccountCluster,
        amount: LocalFiat,
        of: Token,
        verifiedState: VerifiedState,
    ): Result<SwapId> = service.sell(
        scope = scope,
        amount = amount,
        of = of,
        owner = owner,
        verifiedState = verifiedState
    ).onFailure { ErrorUtils.handleError(it) }

    override suspend fun withdrawUsdf(
        scope: CoroutineScope,
        amount: LocalFiat,
        fee: LocalFiat,
        owner: AccountCluster,
        destinationOwner: PublicKey,
        verifiedState: VerifiedState,
    ): Result<SwapId> = service.withdrawUsdf(
        scope = scope,
        amount = amount,
        fee = fee,
        owner = owner,
        destinationOwner = destinationOwner,
        verifiedState = verifiedState,
    ).onFailure { ErrorUtils.handleError(it) }

    override suspend fun sweepUsdc(
        scope: CoroutineScope,
        owner: AccountCluster,
        amount: Long,
    ): Result<Unit> {
        return service.sweepUsdc(scope, owner, amount)
            .onFailure { ErrorUtils.handleError(it) }
    }
}