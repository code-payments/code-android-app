package com.getcode.opencode.repositories

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.transactions.AirdropType
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.opencode.model.transactions.SwapRequest
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.model.transactions.WithdrawalAvailability
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Instant

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

    suspend fun airdrop(
        type: AirdropType,
        destination: KeyPair,
    ): Result<ExchangeData.WithRate>

    suspend fun voidGiftCard(
        owner: KeyPair,
        giftCardVault: PublicKey
    ): Result<Unit>

    suspend fun buy(
        scope: CoroutineScope,
        owner: AccountCluster,
        amount: LocalFiat,
        of: Token,
        swapId: SwapId? = null,
        source: SwapFundingSource = SwapFundingSource.SubmitIntent(),
        fund: (suspend (SwapRequest) -> Result<Unit>)? = null,
    ): Result<Unit>

    suspend fun sell(
        scope: CoroutineScope,
        owner: AccountCluster,
        amount: LocalFiat,
        of: Token
    ): Result<Unit>
}