package com.getcode.opencode.controllers

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.model.WindowedRange
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.DataSource
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenResult
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.minus
import com.getcode.opencode.model.financial.plus
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.util.locale.LocaleHelper
import com.getcode.utils.TraceType
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.network.retryable
import com.getcode.utils.trace
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@Singleton
@OptIn(ExperimentalAtomicApi::class)
class TokenController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val accountController: AccountController,
    private val currencyController: CurrencyController,
    private val networkObserver: NetworkConnectivityListener,
    private val exchange: Exchange,
    private val locale: LocaleHelper,
) {
    companion object {
        val mintPreferenceKey = stringPreferencesKey("tokenMint")
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val selectedToken = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler(
            produceNewData = { emptyPreferences() }
        ),
        migrations = listOf(),
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("selected-token") }
    )

    private val cluster = MutableStateFlow<AccountCluster?>(null)

    private val tokenFetchState = mutableMapOf<Mint, AtomicBoolean>()
    private fun isFetchingToken(mint: Mint): Boolean {
        return tokenFetchState[mint]?.load() ?: false
    }

    private val mintUsdAppreciationMap = MutableStateFlow(mapOf<Mint, Fiat>())

    private val mintBalances = MutableStateFlow(mapOf<Mint, Fiat>())
    val tokens = MutableStateFlow(emptyList<Token>())

    val tokenBalances: Flow<List<TokenWithBalance>> = mintBalances.map {
        it.mapNotNull { (mint, balance) ->
            val token = tokens.value.find { it.address == mint } ?: return@mapNotNull null
            val appreciation = mintUsdAppreciationMap.value[mint] ?: Fiat.Zero
            TokenWithBalance(token, balance, appreciation)
        }
    }

    fun onUserLoggedIn(cluster: AccountCluster) {
        trace(
            tag = "Balance",
            message = "onUserLoggedIn",
            type = TraceType.User
        )
        this.cluster.value = cluster
    }

    init {
        cluster.filterNotNull()
            .flatMapLatest { networkObserver.state }
            .map { it.connected }
            .onEach { connected ->
                if (connected) {
                    retryable { update() }
                }
            }.launchIn(scope)
    }

    fun balanceForToken(token: Token): Fiat {
        return mintBalances.value[token.address] ?: Fiat.Zero
    }

    fun balanceForToken(tokenAddress: Mint): Flow<Fiat> {
        return mintBalances.map { it[tokenAddress] ?: Fiat.Zero }
    }

    fun reservesBalance(): Fiat {
        return balanceForToken(Token.usdf)
    }

    fun observeReservesBalance(): Flow<Fiat> {
        return balanceForToken(Mint.usdf)
    }

    private suspend fun modifyBalance(token: Token, operation: (Fiat) -> Fiat) {
        val balance = mintBalances.value[token.address] ?: Fiat.Zero
        if (balance.decimalValue == 0.0) {
            // attempt to fetch prior to modifying balance
            fetchBalanceForToken(token.address)
        } else {
            val updatedBalance = operation(balance)
            mintBalances.update { it + (token.address to updatedBalance) }
        }
    }

    suspend fun add(token: Token, fiat: LocalFiat) {
        val balanceAdditionAmount =
            fiat.nativeAmount.convertingTo(exchange.rateToUsd(fiat.rate.currency)!!)
        modifyBalance(token) { it + balanceAdditionAmount }
    }

    suspend fun subtract(token: Token, fiat: LocalFiat) {
        val balanceReductionAmount =
            fiat.nativeAmount.convertingTo(exchange.rateToUsd(fiat.rate.currency)!!)
        modifyBalance(token) { it - balanceReductionAmount }
    }

    suspend fun update() {
        updateTokens()
    }

    suspend fun getTokenMetadata(mint: Mint): Result<TokenResult> {
        val cachedToken = tokens.value.find { it.address == mint }
        if (cachedToken != null) {
            return Result.success(TokenResult(cachedToken, DataSource.Cache))
        }

        val metadata = currencyController.getMintMetadata(listOf(mint))
            .onSuccess { token -> tokens.update { (it + token).distinctBy { t -> t.address } } }
            .map { it.firstOrNull { tokenMetadata -> tokenMetadata.address == mint } }
            .getOrNull()

        return metadata?.let { Result.success(TokenResult(it, DataSource.Network)) }
            ?: Result.failure(IllegalStateException("No metadata found for token $mint"))
    }

    suspend fun selectToken(mint: Mint) {
        selectedToken.edit {
            it[mintPreferenceKey] = mint.base58()
        }
    }

    suspend fun getHistoricalMarketCapData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange,
    ): Result<List<HistoricalMintData>> {
        return currencyController.getHistoricalMintData(
            mint,
            currencyCode,
            windowedRange,
        )
    }

    fun observeSelectedTokenMint(): Flow<Mint> {
        return selectedToken.data.mapNotNull { prefs ->
            prefs[mintPreferenceKey]?.let { Mint(it) } ?: return@mapNotNull null
        }
    }

    private suspend fun ensureValidTokenSelection() {
        val balances = mintBalances.value
        if (balances.isEmpty()) return

        val selectedToken = selectedToken.data.map { prefs ->
            prefs[mintPreferenceKey]?.let { Mint(it) } ?: return@map null
        }.firstOrNull()?.takeIf { balances[it] != null }

        if (selectedToken != null) {
            return // current selection is valid
        }

        // No valid selection, default to highest balance of the non-reserve tokens
        val highestBalanceToken =
            balances.filter { it.key != Mint.usdf }.maxByOrNull { it.value }?.key
        if (highestBalanceToken != null) {
            selectToken(highestBalanceToken)
        }
    }

    private suspend fun updateTokens() {
        val owner = cluster.value
        if (owner == null) {
            trace(
                tag = "Token",
                message = "Missing owner while updating tokens",
                type = TraceType.Error
            )
            return
        }

        retryable(
            maxRetries = 3,
            call = suspend {
                accountController.getAccounts(
                    accountOwner = owner,
                    requestingOwner = owner,
                    filter = AccountFilter.AccountType(AccountType.Primary)
                )
            }
        )?.map { response ->
            response.accounts.values
                .mapNotNull { account ->
                    val token = currencyController.getMintMetadata(listOf(account.mint))
                        .getOrDefault(emptyList())
                        .firstOrNull() ?: return@mapNotNull null

                    val tokenBalance = Fiat.tokenBalance(account.balance, token = token)
                    val costBasis = Fiat(fiat = account.usdCostBasis)

                    TokenWithBalance(
                        token = token,
                        balance = tokenBalance,
                        appreciation = tokenBalance - costBasis

                    )
                }
        }?.onSuccess { tokensWithBalance ->
            tokensWithBalance.onEach { (token, balance, appreciation) ->
                trace(
                    tag = "Tokens",
                    message = "Updated balance for ${token.symbol} is ${balance.formatted()} USD",
                    type = TraceType.Process
                )

                mintBalances.update { it + (token.address to balance) }
                mintUsdAppreciationMap.update { it + (token.address to appreciation) }
                tokens.update { (it + token).distinctBy { t -> t.address } }
            }

            ensureValidTokenSelection()
        }
    }

    private suspend fun fetchBalanceForToken(mint: Mint) {
        val owner = cluster.value
        if (owner == null) {
            trace(
                tag = "Token",
                message = "Missing owner while fetching token balance",
                type = TraceType.Error
            )
            return
        }

        if (!isFetchingToken(mint)) {
            tokenFetchState[mint]?.store(true)

            trace(
                tag = "Tokens",
                message = "Fetching Balance for token ${mint.base58()}",
                type = TraceType.Process
            )

            retryable(
                maxRetries = 3,
                call = suspend {
                    accountController.getAccounts(
                        accountOwner = owner,
                        requestingOwner = owner,
                        filter = AccountFilter.MintAddress(mint)
                    )
                }
            )?.map {
                it.accounts.values.toList()
                    .firstOrNull { accountInfo -> accountInfo.accountType == AccountType.Primary && accountInfo.mint == mint }
            }?.fold(
                onSuccess = { account ->
                    val token = getTokenMetadata(mint).getOrNull()

                    if (token == null) {
                        trace(
                            tag = "Tokens",
                            message = "Failed to fetch metadata for token ${mint.base58()}",
                            type = TraceType.Error
                        )
                        return@fold Result.failure(IllegalStateException("No metadata found for token ${mint.base58()}"))
                    }

                    Result.success(account to token)
                },
                onFailure = {
                    trace(
                        tag = "Tokens",
                        message = "Failed to fetch balance for token ${mint.base58()}",
                        type = TraceType.Error
                    )
                    Result.failure(it)
                }
            )?.map { (account, result) ->
                val token = result.token
                when {
                    account == null -> throw IllegalStateException("No account found for token with mint ${token.symbol}")
                    else -> token to Fiat.tokenBalance(account.balance, token = token)
                }
            }?.onSuccess { (token, tokenBalance) ->
                mintBalances.update { it + (mint to tokenBalance) }
                trace(
                    tag = "Tokens",
                    message = "Updated balance for ${token.symbol} is ${tokenBalance.formatted()} USD",
                    type = TraceType.Process
                )
                tokenFetchState[mint]?.store(false)
            }?.onFailure {
                tokenFetchState[mint]?.store(false)
            }
        }
    }

    suspend fun reset() {
        mintBalances.value = emptyMap()
        tokens.value = emptyList()
        cluster.value = null
        selectedToken.edit { it.clear() }
    }
}