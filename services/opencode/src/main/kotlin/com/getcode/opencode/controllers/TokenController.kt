package com.getcode.opencode.controllers

import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.minus
import com.getcode.opencode.model.financial.plus
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.utils.TraceType
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.network.retryable
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Singleton
@OptIn(ExperimentalAtomicApi::class)
class TokenController @Inject constructor(
    private val accountController: AccountController,
    private val currencyController: CurrencyController,
    private val networkObserver: NetworkConnectivityListener,
    private val exchange: Exchange,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val cluster = MutableStateFlow<AccountCluster?>(null)

    private val tokenFetchState = mutableMapOf<Mint, AtomicBoolean>()
    private fun isFetchingToken(mint: Mint): Boolean {
        return tokenFetchState[mint]?.load() ?: false
    }

    private val mintBalances = MutableStateFlow(mapOf<Mint, Fiat>())
    val tokens = MutableStateFlow(emptyList<Token>())

    val tokenBalances: Flow<List<TokenWithBalance>>
        get() = tokens.map {
            it.map { token ->
                val balance = mintBalances.value[token.address] ?: Fiat.Zero
                TokenWithBalance(token, balance)
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

    private suspend fun modifyBalance(token: Token, operation: (Fiat) -> Fiat) {
        val balance = mintBalances.value[token.address] ?: Fiat.Zero
        if (balance.doubleValue == 0.0) {
            // attempt to fetch prior to modifying balance
            fetchBalanceForToken(token.address)
        } else {
            val updatedBalance = operation(balance)
            mintBalances.update { it + (token.address to updatedBalance) }
        }
    }

    suspend fun add(token: Token, fiat: LocalFiat) {
        val balanceAdditionAmount = fiat.nativeAmount.convertingTo(exchange.rateToUsd(fiat.rate.currency)!!)
        modifyBalance(token) { it + balanceAdditionAmount }
    }

    suspend fun subtract(token: Token, fiat: LocalFiat) {
        val balanceReductionAmount = fiat.nativeAmount.convertingTo(exchange.rateToUsd(fiat.rate.currency)!!)
        modifyBalance(token) { it - balanceReductionAmount }
    }

    suspend fun update() {
        updateTokens()
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

                    TokenWithBalance(token, tokenBalance)
                }
        }?.onSuccess { tokensWithBalance ->
            tokensWithBalance.onEach { (token, balance) ->
                trace(
                    tag = "Tokens",
                    message = "Updated balance for ${token.symbol} is ${balance.formatted()} USD",
                    type = TraceType.Process
                )

                mintBalances.update { it + (token.address to balance) }
                tokens.update { (it + token).distinctBy { t -> t.address } }
            }
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
            )?.map { (account, token) ->
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

    suspend fun getTokenMetadata(mint: Mint): Result<Token> {
        val cachedToken = tokens.value.find { it.address == mint }

        return cachedToken?.let { Result.success(it) } ?: currencyController.getMintMetadata(listOf(mint))
            .onSuccess { token -> tokens.update { (it + token).distinctBy { t -> t.address } } }
            .map { it.firstOrNull { tokenMetadata -> tokenMetadata.address == mint }
                ?: throw IllegalStateException("No metadata found for token $mint") }
    }

    fun reset() {
        mintBalances.value = emptyMap()
        tokens.value = emptyList()
        cluster.value = null
    }
}