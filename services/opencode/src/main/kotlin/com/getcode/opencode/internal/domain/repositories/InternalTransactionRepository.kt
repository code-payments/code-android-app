package com.getcode.opencode.internal.domain.repositories

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.executors.IntentExecutor
import com.getcode.opencode.internal.network.services.TransactionService
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.transactions.AirdropType
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.opencode.model.transactions.SwapRequest
import com.getcode.opencode.model.transactions.SwapResult
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.model.transactions.WithdrawalAvailability
import com.getcode.opencode.repositories.TransactionRepository
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Instant
import javax.inject.Inject

internal class InternalTransactionRepository @Inject constructor(
    private val service: TransactionService,
): TransactionRepository {
    override suspend fun submitIntent(
        scope: CoroutineScope,
        intent: IntentType,
        owner: Ed25519.KeyPair
    ): Result<IntentType> = service.submitIntent(scope, intent, owner)

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

    override suspend fun airdrop(
        type: AirdropType,
        destination: Ed25519.KeyPair
    ): Result<ExchangeData.WithRate> = service.airdrop(type, destination)

    override suspend fun voidGiftCard(
        owner: Ed25519.KeyPair,
        giftCardVault: PublicKey
    ): Result<Unit> = service.voidGiftCard(owner, giftCardVault)

    override suspend fun buy(
        scope: CoroutineScope,
        owner: AccountCluster,
        amount: LocalFiat,
        of: Token,
        swapId: SwapId?,
        source: SwapFundingSource,
        fund: (suspend (SwapRequest) -> Result<Unit>)?
    ): Result<Unit> = service.buy(scope, swapId, amount, of, owner, source, fund)

    override suspend fun sell(
        scope: CoroutineScope,
        owner: AccountCluster,
        amount: LocalFiat,
        of: Token
    ): Result<Unit> = service.sell(scope, amount, of, owner)
}