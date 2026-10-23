package com.getcode.opencode.controllers

import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.errors.CheckTokenAvailabilityError
import com.getcode.opencode.model.core.errors.LaunchTokenError
import com.getcode.opencode.model.financial.LiveMintDataResponse
import com.getcode.opencode.model.ui.WindowedRange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenCreateRequest
import com.getcode.opencode.model.financial.fromLaunch
import com.getcode.opencode.model.ui.DiscoverCategory
import com.getcode.opencode.repositories.CurrencyRepository
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides access to live and historical mint/currency data backed by
 * server-streamed updates and one-shot RPCs.
 */
@Singleton
class CurrencyController @Inject constructor(
    private val repository: CurrencyRepository,
    private val transactionController: TransactionController,
) {
    /**
     * Returns a long-lived [Flow] of [LiveMintDataResponse] events for the
     * given [mints]. The flow re-subscribes whenever the mint list changes
     * and replays the most recent emission to late collectors.
     */
    fun streamLiveMintData(
        scope: CoroutineScope,
        mints: Flow<List<Mint>>,
        tag: String? = null,
    ): Flow<LiveMintDataResponse> {
        return mints.flatMapLatest { mintList ->
            if (mintList.isEmpty()) {
                emptyFlow()
            } else {
                callbackFlow {
                    val reference = repository.streamMintData(scope = scope, mints = mintList, tag = tag) {
                        scope.launch {
                            send(it)
                        }
                    }
                    awaitClose {
                        reference.cancel()
                    }
                }.shareIn(
                    scope = scope,
                    started = SharingStarted.Lazily,
                    replay = 1
                )
            }
        }
    }

    /**
     * Opens a short-lived mint data stream and suspends until all required data
     * has been received and persisted to the proto store.
     *
     * For most mints this waits for both exchange rates and launchpad reserve
     * state. USDF only requires exchange rates (no reserve state exists).
     */
    suspend fun getLiveMintData(
        scope: CoroutineScope,
        mint: Mint,
        tag: String? = null
    ): Result<Unit> = runCatching {
        val needsReserveState = mint != Mint.usdf

        callbackFlow {
            val reference = repository.streamMintData(scope = scope, mints = listOf(mint), tag = tag) {
                trySend(it)
            }
            awaitClose { reference.cancel() }
        }.scan(emptySet<Class<out LiveMintDataResponse>>()) { seen, response ->
            seen + response::class.java
        }.first { seen ->
            seen.contains(LiveMintDataResponse.ExchangeRates::class.java) &&
                    (!needsReserveState || seen.contains(LiveMintDataResponse.LaunchpadReserveState::class.java))
        }
    }


    /**
     * Fetches static metadata (name, symbol, decimals, etc.) for the given
     * mint [addresses] in a single RPC call.
     */
    suspend fun getMintMetadata(
        addresses: List<Mint>
    ): Result<List<MintMetadata>> {
        return repository.getMintMetadata(addresses)
    }

    /**
     * Fetches historical price/exchange data for [mint] denominated in
     * [currencyCode] over the specified [windowedRange].
     */
    suspend fun getHistoricalMintData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange,
    ): Result<List<HistoricalMintData>> = runCatching {
        return repository.getHistoricalMintData(mint, currencyCode, windowedRange)
    }

    suspend fun discoverTokens(
        category: DiscoverCategory
    ): Result<List<Token>> {
        return repository.discoverTokens(category)
    }

    suspend fun checkTokenAvailability(name: String): Result<Unit> {
        return repository.checkTokenAvailability(name)
            .mapCatching { available ->
                if (!available) {
                    throw CheckTokenAvailabilityError.Unavailable()
                }
            }
    }

    /**
     * Registers the new currency's metadata on-chain and funds it in one operation, returning the
     * created [Mint] and the funding [SwapId].
     *
     * Funding is driven by [TokenCreateRequest.funding] and routed by [TransactionController.launchToken]:
     * USDF funds via a plain buy of the freshly-launched stub, any other currency via the treasury
     * swap that creates and funds the new currency in a single transaction.
     */
    suspend fun launchToken(
        request: TokenCreateRequest,
        owner: AccountCluster,
        existingMint: Mint? = null,
        onMinted: (Mint) -> Unit = {},
    ): Result<LaunchResult> {
        val funding = request.funding
        return repository.launchToken(request, owner.authority.keyPair)
            .recoverCatching { cause ->
                // The server returns Exists when the mint is already registered. If a prior attempt
                // this session minted but its funding failed, reuse that mint so funding can retry.
                if (cause is LaunchTokenError.Exists && existingMint != null) existingMint
                else throw cause
            }
            .mapCatching { mint ->
                // Surface the mint before funding so the caller can track it for a retry.
                onMinted(mint)
                val stub = MintMetadata.fromLaunch(
                    mint = mint,
                    request = request,
                    owner = owner.authorityPublicKey,
                )

                val swapId = transactionController.launchToken(
                    owner = owner,
                    amount = funding.amount,
                    fullAmount = funding.fullAmount,
                    feeAmount = funding.feeAmount,
                    fundingSource = funding.token,
                    newToken = stub,
                ).getOrThrow()

                LaunchResult(mint = mint, swapId = swapId)
            }
    }
}

/** The result of launching and funding a new currency. */
data class LaunchResult(
    val mint: Mint,
    val swapId: SwapId,
)