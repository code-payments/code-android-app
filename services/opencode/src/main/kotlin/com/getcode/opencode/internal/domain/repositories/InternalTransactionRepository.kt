package com.getcode.opencode.internal.domain.repositories

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.internal.network.services.TransactionService
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.transactions.AirdropType
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.model.transactions.WithdrawalAvailability
import com.getcode.opencode.repositories.TransactionRepository
import com.getcode.solana.keys.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Instant
import javax.inject.Inject

internal class InternalTransactionRepository @Inject constructor(
    private val service: TransactionService
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
    ): Result<WithdrawalAvailability> = service.withdrawalAvailability(destination)

    override suspend fun airdrop(
        type: AirdropType,
        destination: Ed25519.KeyPair
    ): Result<ExchangeData.WithRate> = service.airdrop(type, destination)

    override suspend fun voidGiftCard(
        owner: Ed25519.KeyPair,
        giftCardVault: PublicKey
    ): Result<Unit> = service.voidGiftCard(owner, giftCardVault)
}