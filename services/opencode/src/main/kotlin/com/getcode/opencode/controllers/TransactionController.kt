package com.getcode.opencode.controllers

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.network.api.intents.IntentDistribution
import com.getcode.opencode.internal.network.api.intents.IntentRemoteReceive
import com.getcode.opencode.internal.network.api.intents.IntentRemoteSend
import com.getcode.opencode.internal.network.api.intents.IntentTransfer
import com.getcode.opencode.internal.solana.extensions.newInstance
import com.getcode.opencode.internal.network.api.intents.IntentWithdraw
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.core.errors.GetIntentMetadataError
import com.getcode.opencode.model.core.errors.SwapError
import com.getcode.opencode.model.financial.Distribution
import com.getcode.opencode.model.financial.Fee
import com.getcode.opencode.model.financial.FeeType
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.solana.keys.TimelockDerivedAccounts
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.opencode.model.transactions.SwapMetadata
import com.getcode.opencode.model.transactions.StatefulSwapRequest
import com.getcode.opencode.model.transactions.SwapState
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.model.transactions.WithdrawalAvailability
import com.getcode.opencode.repositories.SwapRepository
import com.getcode.opencode.repositories.TransactionRepository
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.utils.flowInterval
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.getcode.utils.TraceType
import com.getcode.utils.base64
import com.getcode.utils.trace
import com.getcode.vendor.Base58
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration

@Singleton
class TransactionController @Inject constructor(
    private val repository: TransactionRepository,
    private val swapRepository: SwapRepository,
    private val accountController: AccountController,
) : TransactionOperations {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun refreshAccountState() {
        scope.launch { accountController.refreshAccountState() }
    }

    private val _limits = MutableStateFlow<Limits?>(Limits.Empty)
    override val limits: StateFlow<Limits?>
        get() = _limits.asStateFlow()

    override val areLimitsStale: Boolean
        get() = _limits.value == null || _limits.value?.isStale == true

    override suspend fun updateLimits(owner: AccountCluster, force: Boolean) {
        if (areLimitsStale || force) {
            val since = Clock.System.now()
            trace(
                tag = "Transactions",
                message = "updating limits from $since",
                type = TraceType.Process
            )
            repository.getLimits(
                owner = owner.authority.keyPair,
                consumedSince = since
            ).onSuccess {
                _limits.value = it
            }.onFailure {
                trace(
                    tag = "Transactions",
                    message = "Failed to update limits",
                    error = it
                )
            }
        }
    }

    suspend fun transfer(
        amount: LocalFiat,
        mint: Mint,
        source: AccountCluster,
        destination: PublicKey,
        rendezvous: PublicKey,
        scope: CoroutineScope = this.scope,
        exchangeData: ExchangeData.Verified,
    ): Result<IntentType> {
        val intent = IntentTransfer.create(
            amount = amount,
            mint = mint,
            sourceCluster = source,
            destination = destination,
            rendezvous = rendezvous,
            exchangeData = exchangeData,
        )

        return submitIntent(scope, intent, source.authority.keyPair)
    }

    /**
     * Sends funds directly to another user's vault by resolving their
     * [destinationOwner] into a timelock-derived vault address.
     */
    suspend fun directTransfer(
        amount: VerifiedFiat,
        token: Token,
        source: AccountCluster,
        destinationOwner: PublicKey,
        appMetadata: ByteArray? = null,
        scope: CoroutineScope = this.scope,
    ): Result<IntentType> {
        val verifiedState = amount.verifiedState
            ?: return Result.failure(IllegalStateException("No verified state"))

        val timelock = TimelockDerivedAccounts.newInstance(owner = destinationOwner, token = token)
        val destinationVault = timelock.vault.publicKey

        val intent = IntentTransfer.create(
            amount = amount.localFiat,
            mint = token.address,
            sourceCluster = source,
            destination = destinationVault,
            destinationOwner = destinationOwner,
            verifiedState = verifiedState,
            appMetadata = appMetadata,
        )

        return submitIntent(scope, intent, source.authority.keyPair)
    }

    override suspend fun withdraw(
        amount: VerifiedFiat,
        mint: Mint,
        owner: AccountCluster,
        destination: PublicKey,
        destinationOwner: PublicKey?,
        fee: Fiat?,
        scope: CoroutineScope,
    ): Result<IntentType> {
        trace(
            tag = "TransactionController",
            message = "Starting withdrawal",
            type = TraceType.Process,
            metadata = {
                "amount (native)" to amount.localFiat.nativeAmount.formatted()
                "amount (underlying)" to amount.localFiat.underlyingTokenAmount.formatted()
                "amount currency" to amount.localFiat.nativeAmount.currencyCode.name
                "fee" to (fee?.formatted() ?: "none")
                "mint" to mint.base58()
            }
        )

        val verifiedState = amount.verifiedState
            ?: return Result.failure(SwapError.Other(IllegalStateException("No verified state found")))

        val intent = IntentWithdraw.create(
            amount = amount.localFiat,
            mint = mint,
            fee = fee?.let { Fee(it, FeeType.CreateOnSendWithdrawal) },
            sourceCluster = owner,
            destination = destination,
            destinationOwner = destinationOwner,
            verifiedState = verifiedState,
        )

        return submitIntent(scope, intent, owner.authority.keyPair)
            .onSuccess {
                refreshAccountState()
            }
    }

    override suspend fun withdrawUsdf(
        amount: VerifiedFiat,
        owner: AccountCluster,
        destination: PublicKey,
        destinationOwner: PublicKey,
        fee: LocalFiat,
    ): Result<SwapId> {
        trace(
            tag = "TransactionController",
            message = "Starting USDF->USDC withdrawal",
            type = TraceType.Process,
            metadata = {
                "amount (native)" to "${amount.localFiat.nativeAmount.formatted()} (${amount.localFiat.nativeAmount.currencyCode})"
                "amount (underlying)" to "${amount.localFiat.underlyingTokenAmount.formatted()} (${amount.localFiat.underlyingTokenAmount.currencyCode})"
                "fee (native)" to "${fee.nativeAmount.formatted()} (${fee.nativeAmount.currencyCode})"
                "fee (underlying)" to "${fee.underlyingTokenAmount.formatted()} (${fee.underlyingTokenAmount.currencyCode})"
            }
        )

        val verifiedState = amount.verifiedState
            ?: return Result.failure(SwapError.Other(IllegalStateException("No verified state found")))

        return repository.withdrawUsdf(
            scope = scope,
            owner = owner,
            amount = amount.localFiat,
            fee = fee,
            destinationOwner = destinationOwner,
            verifiedState = verifiedState
        ).onSuccess { refreshAccountState() }.onFailure { error ->
            trace(
                tag = "TransactionController",
                message = error.message.orEmpty(),
                type = TraceType.Error,
                error = error
            )
        }
    }

    suspend fun remoteSend(
        giftCard: GiftCardAccount,
        amount: LocalFiat,
        token: Token,
        owner: AccountCluster,
        source: AccountCluster,
        rendezvous: PublicKey,
        verifiedState: VerifiedState,
        scope: CoroutineScope = this.scope,
    ): Result<IntentType> {

        val intent = IntentRemoteSend.create(
            amount = amount,
            token = token,
            owner = owner,
            source = source,
            giftCard = giftCard,
            rendezvous = rendezvous,
            verifiedState = verifiedState,
        )

        return submitIntent(scope, intent, owner.authority.keyPair)
            .onFailure {
                trace(
                    tag = "Transactions",
                    message = "Failed to fund cash link",
                    error = it,
                    type = TraceType.Process
                )
            }
    }

    override suspend fun cancelRemoteSend(
        owner: AccountCluster,
        vault: PublicKey,
    ): Result<Unit> {
        trace(
            tag = "Transactions",
            message = "Canceling remote send for vault ${vault.bytes.base64}",
            type = TraceType.User
        )
        return repository.voidGiftCard(owner.authority.keyPair, vault)
            .onSuccess { refreshAccountState() }
            .onFailure {
                trace(
                    tag = "Transactions",
                    message = "Failed to cancel cash link",
                    error = it,
                    type = TraceType.Process
                )
            }
    }

    suspend fun receiveRemotely(
        owner: AccountCluster,
        giftCard: GiftCardAccount,
        amount: LocalFiat,
        mint: Mint,
    ): Result<IntentType> {
        val intent = IntentRemoteReceive.create(
            amount = amount,
            giftCard = giftCard,
            owner = owner,
            mint = mint
        )

        return submitIntent(scope, intent, owner.authority.keyPair)
    }

    suspend fun distributeFunds(
        owner: AccountCluster,
        from: AccountCluster,
        distributions: List<Distribution>,
        mint: Mint,
    ): Result<IntentType> {
        val intent = IntentDistribution.create(
            owner = owner,
            source = from,
            distributions = distributions,
            mint = mint
        )

        return submitIntent(scope, intent, owner.authority.keyPair)
    }

    override suspend fun buy(
        owner: AccountCluster,
        amount: VerifiedFiat,
        feeAmount: LocalFiat?,
        swapId: SwapId?,
        of: Token,
        source: SwapFundingSource,
        fund: (suspend (StatefulSwapRequest) -> Result<Unit>)?,
    ): Result<SwapId> {
        trace("Starting ${amount.localFiat.nativeAmount.formatted()} buy of ${of.symbol}")
        val tokenizedOwner = owner.withTimelockForToken(of)

        // A Token whose launchpadMetadata is null and whose address isn't USDF is
        // a freshly-launched stub (see MintMetadata.fromLaunch). For that case the
        // on-chain VM doesn't exist yet; the atomic new-currency swap transaction
        // itself creates the VM (VM::InitializeVm) and the VM deposit ATA
        // (CreateIdempotent), so no pre-flight createUserAccount call is needed —
        // and attempting one would fail against a non-existent VM.
        val isFreshlyLaunchedStub =
            of.launchpadMetadata == null && of.address != Mint.usdf

        // create an account if we don't currently have one for this token
        val accountResult = when {
            isFreshlyLaunchedStub -> Result.success(Unit)
            accountController.hasAccountFor(of.address) -> Result.success(Unit)
            else -> accountController.createUserAccount(
                ownerForMint = tokenizedOwner,
                mint = of.address,
            )
        }

        val verifiedState = amount.verifiedState
            ?: return Result.failure(SwapError.Other(IllegalStateException("No verified state found")))

        return accountResult.fold(
            onSuccess = {
                repository.buy(
                    scope = scope,
                    swapId = swapId,
                    owner = owner,
                    amount = amount.localFiat,
                    feeAmount = feeAmount,
                    of = of,
                    source = source,
                    fund = fund,
                    verifiedState = verifiedState,
                ).onSuccess { refreshAccountState() }
            },
            onFailure = { error ->
                trace(
                    tag = "TransactionController",
                    message = error.message.orEmpty(),
                    type = TraceType.Error,
                    error = error
                )
                Result.failure(error)
            }
        )
    }

    override suspend fun sell(
        owner: AccountCluster,
        amount: VerifiedFiat,
        of: Token,
    ): Result<SwapId> {
        val verifiedState = amount.verifiedState
            ?: return Result.failure(SwapError.Other(IllegalStateException("No verified state found")))

        return repository.sell(
            scope = scope,
            owner = owner,
            amount = amount.localFiat,
            of = of,
            verifiedState = verifiedState
        )
            .onSuccess { refreshAccountState() }
            .onFailure { error ->
                trace(
                    tag = "TransactionController",
                    message = error.message.orEmpty(),
                    type = TraceType.Error,
                    error = error
                )
            }
    }

    override suspend fun pollSwapForState(
        swapId: SwapId,
        owner: AccountCluster,
        targetState: SwapState,
        maxAttempts: Int,
        interval: Duration,
    ): Result<SwapMetadata> {
        return swapRepository.pollForState(
            swapId,
            owner.authority.keyPair,
            targetState,
            maxAttempts,
            interval
        )
    }

    internal suspend fun submitIntent(
        scope: CoroutineScope,
        intent: IntentType,
        authority: KeyPair,
    ): Result<IntentType> = repository.submitIntent(scope, intent, authority)
        .onSuccess { refreshAccountState() }

    suspend fun getIntentMetadata(
        intentId: PublicKey,
        owner: KeyPair,
    ): Result<TransactionMetadata> = repository.getIntentMetadata(intentId, owner)

    suspend fun <T : TransactionMetadata> pollIntentMetadata(
        intentId: PublicKey,
        owner: KeyPair,
        maxAttempts: Int = 10,
        debugLogs: Boolean = false,
    ): Result<T> {
        val attemptCount = AtomicInteger()

        if (debugLogs) {
            trace(
                tag = "CodeScan",
                message = "pollIntentMetadata: start",
                type = TraceType.Process
            )
        }

        return flowInterval({ 50L * (attemptCount.get() / 10) })
            .takeWhile { attemptCount.get() < maxAttempts }
            .map { attemptCount.incrementAndGet() }
            .onEach {
                if (debugLogs) {
                    trace(
                        tag = "CodeScan",
                        message = "pollIntentMetadata: [$it] fetch data",
                        type = TraceType.Process
                    )
                }
            }
            .map { repository.getIntentMetadata(intentId, owner) }
            .onEach {
                if (debugLogs) {
                    trace(
                        tag = "CodeScan",
                        message = "pollIntentMetadata: [$it] received metadata",
                        type = TraceType.Process
                    )
                }
            }
            .mapNotNull { result ->
                result.fold(
                    onSuccess = { metadata ->
                        if (debugLogs) {
                            trace(
                                tag = "CodeScan",
                                message = "pollIntentMetadata: took ${attemptCount.get()} attempts",
                                type = TraceType.Process
                            )
                        }
                        metadata
                    },
                    onFailure = { error ->
                        if (error is GetIntentMetadataError.Denied) throw error
                        null
                    }
                )
            }.mapNotNull { it as? T }
            .map { Result.success(it) }
            .catch { emit(Result.failure(it)) }
            .firstOrNull()
            ?: Result.failure(GetIntentMetadataError.Timeout())
    }

    override suspend fun swapUsdc(
        owner: AccountCluster,
        amount: Long,
    ): Result<Unit> {
        if (amount <= 0L) return Result.success(Unit)
        return repository.sweepUsdc(scope, owner, amount)
    }

    override suspend fun checkWithdrawalAvailability(
        address: String,
        mint: Mint,
    ): Result<WithdrawalAvailability> {
        val result: Result<PublicKey> = runCatching {
            val decoded = Base58.decode(address)
            val isValid = decoded.size == 32
            if (isValid) PublicKey(decoded.toList()) else throw IllegalArgumentException("Invalid address")
        }

        result.exceptionOrNull()?.let {
            return Result.failure(it)
        }

        return repository.withdrawalAvailability(
            destination = result.getOrNull()!!,
            mint = mint,
        )
    }
}