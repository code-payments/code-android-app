package com.getcode.opencode.internal.network.services

import com.codeinc.opencode.gen.transaction.v1.TransactionService
import com.codeinc.opencode.gen.transaction.v1.feeAmountOrNull
import com.getcode.ed25519.Ed25519
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.domain.mapping.TransactionMetadataMapper
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.network.api.TransactionApi
import com.getcode.opencode.internal.network.executors.IntentExecutor
import com.getcode.opencode.internal.network.executors.SwapExecutor
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.internal.network.extensions.toModel
import com.getcode.opencode.internal.network.funding.SwapFunding
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.errors.GetIntentMetadataError
import com.getcode.opencode.model.core.errors.GetLimitsError
import com.getcode.opencode.model.core.errors.SendMessageError
import com.getcode.opencode.model.core.errors.VoidGiftCardError
import com.getcode.opencode.model.core.errors.WithdrawalAvailabilityError
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.transactions.SwapDirection
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.opencode.model.transactions.SwapRequest
import com.getcode.opencode.model.transactions.SwapStartKind
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.model.transactions.WithdrawalAvailability
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.utils.toValidationOrElse
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Instant
import javax.inject.Inject


internal class TransactionService @Inject constructor(
    private val api: TransactionApi,
    private val transactionMetadataMapper: TransactionMetadataMapper,
    private val swapFunding: SwapFunding,
) {
    suspend fun submitIntent(
        scope: CoroutineScope,
        intent: IntentType,
        owner: KeyPair,
    ): Result<IntentType> {
        val executor = IntentExecutor(api)
        return executor.execute(scope, intent, owner)
    }

    suspend fun getIntentMetadata(
        intentId: PublicKey,
        owner: KeyPair
    ): Result<TransactionMetadata> {
        return runCatching {
            api.getIntentMetadata(intentId, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    TransactionService.GetIntentMetadataResponse.Result.OK -> {
                        Result.success(transactionMetadataMapper.map(response.metadata))
                    }

                    TransactionService.GetIntentMetadataResponse.Result.NOT_FOUND -> Result.failure(
                        GetIntentMetadataError.NotFound()
                    )

                    TransactionService.GetIntentMetadataResponse.Result.UNRECOGNIZED -> Result.failure(
                        GetIntentMetadataError.Unrecognized()
                    )

                    TransactionService.GetIntentMetadataResponse.Result.DENIED -> Result.failure(
                        GetIntentMetadataError.Denied()
                    )

                    else -> Result.failure(GetIntentMetadataError.Other())
                }
            },
            onFailure = { error ->
                Result.failure(error.toValidationOrElse { GetIntentMetadataError.Other(cause = it) })
            }
        )
    }

    suspend fun getLimits(
        owner: KeyPair,
        consumedSince: Instant,
    ): Result<Limits> {
        return runCatching {
            api.getLimits(owner, consumedSince.toEpochMilliseconds())
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    TransactionService.GetLimitsResponse.Result.OK -> {
                        val limits = Limits.newInstance(
                            sinceDate = consumedSince.toEpochMilliseconds(),
                            fetchDate = System.currentTimeMillis(),
                            sendLimits = response.sendLimitsByCurrencyMap,
                            usdTransactedSinceConsumption = response.usdTransacted
                        )
                        Result.success(limits)
                    }

                    TransactionService.GetLimitsResponse.Result.UNRECOGNIZED -> Result.failure(
                        GetLimitsError.Unrecognized()
                    )

                    else -> Result.failure(GetLimitsError.Other())
                }
            },
            onFailure = { error ->
                Result.failure(error.toValidationOrElse { GetLimitsError.Other(cause = it) })
            }
        )
    }

    suspend fun withdrawalAvailability(
        destination: PublicKey,
        mint: Mint,
    ): Result<WithdrawalAvailability> {
        return runCatching {
            api.canWithdrawToAccount(destination, mint)
        }.foldWithSuppression(
            onSuccess = { response ->
                val availability = WithdrawalAvailability.newInstance(
                    destination = destination,
                    isValid = response.isValidPaymentDestination,
                    kind = WithdrawalAvailability.Kind.tryValueOf(response.accountType.name)
                        ?: WithdrawalAvailability.Kind.Unknown,
                    requiresInitialization = response.requiresInitialization,
                    feeAmount = response.feeAmountOrNull?.toModel(),
                    mint = mint,
                )

                Result.success(availability)
            },
            onFailure = { error ->
                Result.failure(error.toValidationOrElse { WithdrawalAvailabilityError.Other(cause = it) })
            }
        )
    }

    suspend fun voidGiftCard(
        owner: KeyPair,
        giftCardVault: PublicKey,
    ): Result<Unit> {
        return runCatching {
            api.voidGiftCard(owner, giftCardVault)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    TransactionService.VoidGiftCardResponse.Result.OK -> Result.success(Unit)
                    TransactionService.VoidGiftCardResponse.Result.DENIED -> Result.failure(VoidGiftCardError.Denied())
                    TransactionService.VoidGiftCardResponse.Result.CLAIMED_BY_OTHER_USER -> Result.failure(VoidGiftCardError.AlreadyClaimed())
                    TransactionService.VoidGiftCardResponse.Result.NOT_FOUND -> Result.failure(VoidGiftCardError.NotFound())
                    TransactionService.VoidGiftCardResponse.Result.UNRECOGNIZED -> Result.failure(VoidGiftCardError.Unrecognized())
                    else -> Result.failure(VoidGiftCardError.Other())
                }
            },
            onFailure = { error ->
                Result.failure(error.toValidationOrElse { VoidGiftCardError.Other(cause = it) })
            }
        )
    }

    suspend fun buy(
        scope: CoroutineScope,
        swapId: SwapId?,
        amount: LocalFiat,
        of: Token,
        owner: AccountCluster,
        verifiedState: VerifiedState,
        source: SwapFundingSource = SwapFundingSource.SubmitIntent(),
        fund: (suspend (SwapRequest) -> Result<Unit>)?,
    ): Result<SwapId> {
        // For a freshly-launched stub Token (launchpadMetadata == null && address != USDF), the
        // Solana program requires authority == buyer == swapAuthority for the atomic 11-instruction
        // swap that initializes the currency + pool + VM. The server infers new-vs-existing from
        // owner == swapAuthority in the Initiate request, so we must use the owner's keypair here.
        // Otherwise the server returns RESERVE_EXISTING_CURRENCY params, and the client crashes at
        // ExistingCurrencyBuyInstructions ("Target mint has no launchpad metadata").
        val isFreshlyLaunchedStub =
            of.launchpadMetadata == null && of.address != Mint.usdf
        val swapAuthority =
            if (isFreshlyLaunchedStub) owner.authority.keyPair else Ed25519.createKeyPair()

        val request = SwapRequest(
            owner = owner,
            swapAuthority = swapAuthority,
            kind = SwapStartKind.Reserve(
                fromMint = Mint.usdf,
                toMint = of.address,
                amount = amount.underlyingTokenAmount.quarks,
                fundingSource = source,
            ),
            direction = SwapDirection.Buy(of),
            amount = amount,
            swapId = swapId ?: SwapId.generate(),
            verifiedState = verifiedState,
        )

        val fundedWith = fund ?: { swapFunding.fund(scope, owner, it).map { Unit } }
        return swap(scope, request, owner, fundedWith)
    }

    suspend fun sell(
        scope: CoroutineScope,
        amount: LocalFiat,
        of: Token,
        owner: AccountCluster,
        source: SwapFundingSource = SwapFundingSource.SubmitIntent(),
        verifiedState: VerifiedState,
    ): Result<SwapId> {
        val tokenCluster = owner.withTimelockForToken(of)

        val request = SwapRequest(
            owner = tokenCluster,
            swapId = SwapId.generate(),
            swapAuthority = Ed25519.createKeyPair(),
            kind = SwapStartKind.Reserve(
                fromMint = of.address,
                toMint = Mint.usdf,
                amount = amount.underlyingTokenAmount.quarks,
                fundingSource = source,
            ),
            direction = SwapDirection.Sell(of),
            amount = amount,
            verifiedState = verifiedState,
        )

        return swap(scope, request, tokenCluster)
    }

    private suspend fun swap(
        scope: CoroutineScope,
        request: SwapRequest,
        owner: AccountCluster,
        fund: suspend (SwapRequest) -> Result<Unit> = { swapFunding.fund(scope, owner, it).map { Unit } },
    ): Result<SwapId> {
        val executor = SwapExecutor(api)
        return executor.execute(scope, request)
            .fold(
                onSuccess = {
                    trace("Swap submitted, Swap ID: ${request.swapId.publicKey.base58()}")
                    fund(request)
                },
                onFailure = {
                    Result.failure(it)
                }
            ).map { request.swapId }
    }
}