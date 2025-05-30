package com.getcode.opencode.controllers

import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.accounts.unusable
import com.getcode.opencode.model.core.errors.GetAccountsError
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.minus
import com.getcode.opencode.model.financial.plus
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.TraceType
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.network.retryable
import com.getcode.utils.timedTrace
import com.getcode.utils.trace
import com.hoc081098.channeleventbus.ChannelEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@Singleton
@OptIn(ExperimentalAtomicApi::class)
class BalanceController @Inject constructor(
    private val accountController: AccountController,
    private val networkObserver: NetworkConnectivityListener,
    private val exchange: Exchange,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _rawBalance = MutableStateFlow(Fiat.Zero)
    private val localizedBalance = MutableStateFlow(LocalFiat.Zero)
    private val cluster = MutableStateFlow<AccountCluster?>(null)

    private val fetching = AtomicBoolean(false)

    var onTimelockUnlocked: (() -> Unit) = { }

    val rawBalance: StateFlow<Fiat>
        get() = _rawBalance.asStateFlow()

    val balance: StateFlow<LocalFiat>
        get() = localizedBalance.asStateFlow()

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
                    retryable { fetchBalance() }
                }
            }.flatMapLatest {
                combine(
                    exchange.observeBalanceRate()
                        .flowOn(Dispatchers.IO)
                        .onEach { exchange.fetchRatesIfNeeded() },
                    _rawBalance
                ) { rate, balance ->
                    LocalFiat(
                        usdc = balance,
                        converted = balance.convertingTo(rate),
                        rate = rate
                    )
                }
            }.distinctUntilChanged()
            .onEach { newBalance ->
                localizedBalance.update { newBalance }
            }.launchIn(scope)
    }

    suspend fun add(fiat: LocalFiat) {
        if (_rawBalance.value.doubleValue == 0.0) {
            // attempt to fetch prior to append
            fetchBalance()
        } else {
            _rawBalance.value += fiat.usdc
        }
        localizedBalance.value = fiat
    }

    suspend fun subtract(fiat: LocalFiat) {
        if (_rawBalance.value.doubleValue == 0.0) {
            // attempt to fetch prior to append
            fetchBalance()
        } else {
            _rawBalance.value = (_rawBalance.value - fiat.usdc).coerceAtLeast(Fiat.Zero)
        }
        localizedBalance.value = fiat
    }

    suspend fun fetchBalance() {
        val owner = cluster.value
        if (owner == null) {
            trace(
                tag = "Balance",
                message = "Missing owner while fetching balance",
                type = TraceType.Error
            )
            return
        }

        if (!fetching.load()) {
            fetching.store(true)
            trace(
                tag = "Balance",
                message = "Fetching Balance",
                type = TraceType.Process
            )

            retryable(
                maxRetries = 3,
                call = suspend { accountController.getAccounts(owner) }
            )?.recoverCatching { error ->
                if (error is GetAccountsError.NotFound) {
                    // No account yet, let's create it
                    val createResult = accountController.createUserAccount(owner)
                    if (createResult.isSuccess) {
                        accountController.getAccounts(owner)
                            .getOrElse { throw it }
                    } else {
                        throw createResult.exceptionOrNull() ?: Exception("Account creation failed")
                    }
                } else {
                    throw error
                }
            }?.map { accounts ->
                if (accounts.values.any { it.unusable }) {
                    onTimelockUnlocked()
                }
                retrieveBalanceFromAccounts(accounts)
            }?.onSuccess { newBalance ->
                trace(
                    tag = "Balance",
                    message = "Updated balance is ${newBalance.formatted()} USD",
                    type = TraceType.Process
                )
                _rawBalance.update { newBalance }
                fetching.store(false)
            }?.onFailure {
                fetching.store(false)
            }
        }
    }

    private fun retrieveBalanceFromAccounts(accounts: Map<PublicKey, AccountInfo>): Fiat {
        var balance = Fiat.Zero
        timedTrace(
            tag = "Balance",
            message = "parsing balance from accounts",
            type = TraceType.Process,
        ) {
            for ((_, info) in accounts) {
                if (info.accountType == AccountType.Primary) {
                    balance += info.balance
                }
            }
        }

        return balance
    }

    fun reset() {
        _rawBalance.value = Fiat.Zero
        localizedBalance.value = LocalFiat.Zero
        cluster.value = null
    }
}