package com.getcode.opencode.internal.domain.repositories

import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.model.account.AccountCluster
import com.getcode.opencode.internal.network.services.AccountService
import com.getcode.opencode.internal.network.services.TransactionService
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.accounts.unusable
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.plus
import com.getcode.opencode.repositories.BalanceRepository
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.TraceType
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.network.retryable
import com.getcode.utils.timedTrace
import com.getcode.utils.trace
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

@Suppress("OPT_IN_USAGE")
internal class InternalBalanceRepository @Inject constructor(
    exchange: Exchange,
    networkObserver: NetworkConnectivityListener,
    private val accountService: AccountService,
    private val transactionService: TransactionService,
) : BalanceRepository {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val rawBalance = MutableStateFlow(Fiat.Zero)
    private val localizedBalance = MutableStateFlow(LocalFiat.Zero)
    private val cluster = MutableStateFlow<AccountCluster?>(null)

    override val balance: StateFlow<LocalFiat>
        get() = localizedBalance.asStateFlow()

    override fun onUserLoggedIn(cluster: AccountCluster) {
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
                    exchange.observeLocalRate()
                        .flowOn(Dispatchers.IO)
                        .onEach { exchange.fetchRatesIfNeeded() },
                    rawBalance
                ) { rate, balance ->
                    LocalFiat(
                        usdc = balance,
                        converted = balance.convertingTo(rate),
                        rate = rate
                    )
                }
            }.distinctUntilChanged()
            .onEach {
                localizedBalance.update { it }
            }.launchIn(scope)
    }

    override suspend fun fetchBalance(): Result<Fiat> {
        val owner = cluster.value?.authority?.keyPair ?: return Result.failure(IllegalStateException("Missing owner while fetching balance"))

        trace(
            tag = "Balance",
            message = "Fetching Balance",
            type = TraceType.Process
        )

        return try {
            return accountService.getAccounts(owner)
                .map {
                    if (it.values.any { it.unusable }) {
                        // TODO: relay back to UserManager
                    }
                    it
                }
                .map { retrieveBalanceFromAccounts(it) }
                .onSuccess {
                    println("balance is ${it.formatted()} USD")
                    rawBalance.update { it }
                }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    private fun retrieveBalanceFromAccounts(accounts: Map<PublicKey, AccountInfo>): Fiat {
        var balance = Fiat.Zero
        timedTrace("parsing balance from accounts") {
            for ((_, info) in accounts) {
                if (info.accountType == AccountType.Primary) {
                    balance += info.balance
                }
            }
        }

        return balance
    }

    override fun reset() {
        rawBalance.value = Fiat.Zero
        localizedBalance.value = LocalFiat.Zero
        cluster.value = null
    }
}