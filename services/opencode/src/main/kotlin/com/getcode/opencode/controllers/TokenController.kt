package com.getcode.opencode.controllers

import com.getcode.opencode.internal.model.LiveMintDataResponse
import com.getcode.opencode.internal.model.WindowedRange
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.TokenResult
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.minus
import com.getcode.opencode.providers.TokenMetadataProvider
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.utils.TraceType
import com.getcode.utils.network.retryable
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stateless network gateway for token operations.
 *
 * This controller provides direct access to token-related network APIs
 * without any caching, persistence, or state management. It is designed
 * to be usable both within the Flipcash app (wrapped by [TokenCoordinator])
 * and as part of a standalone public SDK.
 *
 * All state management (caching, persistence, lifecycle, balance tracking)
 * is the responsibility of the consumer.
 */
@Singleton
class TokenController @Inject constructor(
    private val accountController: AccountController,
    private val currencyController: CurrencyController,
) : TokenMetadataProvider {

    companion object {
        private const val TAG = "TokenController"
    }

    // region TokenMetadataProvider

    /**
     * Fetches token metadata directly from the network.
     *
     * In a standalone SDK context, this is the primary way to resolve token metadata.
     * When wrapped by a coordinator with caching, this serves as the network-tier fallback.
     *
     * @param mint The [Mint] address of the token to fetch metadata for.
     * @return A [Result] containing a [TokenResult] on success, or an error on failure.
     */
    override suspend fun getTokenMetadata(mint: Mint): Result<TokenResult> {
        trace(
            tag = TAG,
            message = "Fetching token metadata for ${mint.base58()} from network",
            type = TraceType.Process
        )

        return currencyController.getMintMetadata(listOf(mint))
            .onSuccess { tokens ->
                trace(
                    tag = TAG,
                    message = "Fetched metadata for ${tokens.size} token(s)",
                    type = TraceType.Process
                )
            }
            .onFailure { error ->
                trace(
                    tag = TAG,
                    message = "Failed to fetch token metadata for ${mint.base58()}: ${error.message}",
                    type = TraceType.Error
                )
            }
            .mapCatching { it.first { t -> t.address == mint } }
            .map { TokenResult(it, com.getcode.opencode.model.financial.DataSource.Network) }
    }

    // endregion

    // region Token account fetching

    /**
     * Fetches all primary token accounts for the given user and builds
     * [TokenWithBalance] for each, resolving metadata as needed.
     *
     * @param cluster The authenticated user's [AccountCluster].
     * @param metadataProvider The [TokenMetadataProvider] to use for resolving metadata.
     *   In a standalone context, pass `this`. When wrapped by a coordinator with
     *   caching, the coordinator passes itself to benefit from cache hits.
     * @return A list of [TokenWithBalance] for all primary accounts, or null on failure.
     */
    suspend fun fetchTokenAccounts(
        cluster: AccountCluster,
        metadataProvider: TokenMetadataProvider = this,
    ): Result<List<TokenWithBalance>> {
        trace(tag = TAG, message = "Fetching all primary token accounts", type = TraceType.Process)

        val response = retryable(maxRetries = 3) {
            accountController.getAccounts(
                cluster, cluster, AccountFilter.AccountType(AccountType.Primary)
            )
        } ?: return Result.failure(IllegalStateException("Failed to fetch accounts after retries"))

        return response.map { accountResponse ->
            accountResponse.accounts.values.mapNotNull { account ->
                buildTokenWithBalance(account, metadataProvider)
            }.also {
                trace(
                    tag = TAG,
                    message = "Built ${it.size} token balances from ${accountResponse.accounts.size} accounts",
                    type = TraceType.Process
                )
            }
        }
    }

    /**
     * Fetches a single token account for the given mint and builds a [TokenWithBalance].
     *
     * @param cluster The authenticated user's [AccountCluster].
     * @param mint The [Mint] address of the token account to fetch.
     * @param metadataProvider The [TokenMetadataProvider] to use for resolving metadata.
     * @return A [TokenWithBalance] if found, or null.
     */
    suspend fun fetchTokenAccount(
        cluster: AccountCluster,
        mint: Mint,
        metadataProvider: TokenMetadataProvider = this,
    ): Result<TokenWithBalance> {
        trace(tag = TAG, message = "Fetching token account for ${mint.base58()}", type = TraceType.Process)

        val response = retryable(maxRetries = 3) {
            accountController.getAccounts(cluster, cluster, AccountFilter.MintAddress(mint))
        } ?: return Result.failure(IllegalStateException("Failed to fetch account for ${mint.base58()} after retries"))

        return response.mapCatching { accountResponse ->
            val account = accountResponse.accounts.values
                .firstOrNull { it.accountType == AccountType.Primary && it.mint == mint }
                ?: throw NoSuchElementException("No primary account found for ${mint.base58()}")

            buildTokenWithBalance(account, metadataProvider)
                ?: throw IllegalStateException("Failed to build token with balance for ${mint.base58()}")
        }
    }

    // endregion

    // region Historical data

    /**
     * Fetches historical market capitalization data for a given token.
     *
     * @param mint The [Mint] address of the token.
     * @param currencyCode The [CurrencyCode] for the data representation.
     * @param windowedRange The time window for the historical data.
     * @return A [Result] containing a list of [HistoricalMintData] on success.
     */
    suspend fun getHistoricalMarketCapData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange,
    ): Result<List<HistoricalMintData>> {
        trace(
            tag = TAG,
            message = "Fetching historical market cap for ${mint.base58()}, range=$windowedRange",
            type = TraceType.Process
        )

        return currencyController.getHistoricalMintData(mint, currencyCode, windowedRange)
            .onSuccess { data ->
                trace(tag = TAG, message = "Received ${data.size} historical data points", type = TraceType.Process)
            }
            .onFailure { error ->
                trace(tag = TAG, message = "Failed to fetch historical market cap: ${error.message}", type = TraceType.Error)
            }
    }

    // endregion

    // region Reserve state streaming

    /**
     * Creates a flow of reserve state updates for the given mints.
     *
     * This is a stateless factory — the caller owns the [CoroutineScope] and
     * manages the collection lifecycle. The underlying stream also feeds
     * [VerifiedProtoManager] as a side effect inside [CurrencyService], ensuring
     * verified protos are available at intent submission time.
     *
     * @param scope The [CoroutineScope] that controls the stream's lifetime.
     * @param mints A reactive flow of mint lists to subscribe to. The stream
     *   restarts when the mint list changes.
     * @return A [Flow] of [LiveMintDataResponse.LaunchpadReserveState] updates.
     */
    fun streamReserveStates(
        scope: CoroutineScope,
        mints: Flow<List<Mint>>,
    ): Flow<LiveMintDataResponse.LaunchpadReserveState> {
        return currencyController.streamLiveMintData(
            scope = scope,
            mints = mints,
            tag = "token-reserves"
        ).filterIsInstance<LiveMintDataResponse.LaunchpadReserveState>()
    }

    // endregion

    // region Internal

    private suspend fun buildTokenWithBalance(
        account: AccountInfo,
        metadataProvider: TokenMetadataProvider,
    ): TokenWithBalance? {
        val metadata = metadataProvider.getTokenMetadata(account.mint).getOrNull()?.token ?: return null

        val tokenBalance = Fiat.tokenBalance(account.balance, metadata)
        val appreciation = tokenBalance - Fiat(fiat = account.usdCostBasis)

        return TokenWithBalance(metadata, tokenBalance, appreciation)
    }

    // endregion
}