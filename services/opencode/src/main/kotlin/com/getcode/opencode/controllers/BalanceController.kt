package com.getcode.opencode.controllers

import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.accounts.unusable
import com.getcode.opencode.model.core.errors.GetAccountsError
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.minus
import com.getcode.opencode.model.financial.plus
import com.getcode.opencode.model.financial.sum
import com.getcode.solana.keys.Mint
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

@Singleton
@Deprecated(
    message = "Replaced by multi-token controller TokenController",
    replaceWith = ReplaceWith("TokenController")
)
@OptIn(ExperimentalAtomicApi::class)
class BalanceController @Inject constructor(
    private val accountController: AccountController,
    private val networkObserver: NetworkConnectivityListener,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _rawBalance = MutableStateFlow(Fiat.Zero)

    internal val mintBalanceMap = MutableStateFlow(mapOf<Mint, Fiat>())

    private val cluster = MutableStateFlow<AccountCluster?>(null)

    private val fetching = AtomicBoolean(false)

    var onTimelockUnlocked: (() -> Unit) = { }
    var onNextIndexDetermined: ((Long) -> Unit) = { }

    val rawBalance: StateFlow<Fiat>
        get() = _rawBalance.asStateFlow()

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
            }.launchIn(scope)
    }

    suspend fun add(fiat: LocalFiat) {
        if (_rawBalance.value.doubleValue == 0.0) {
            // attempt to fetch prior to append
            fetchBalance()
        } else {
            _rawBalance.value += fiat.underlyingTokenAmount
        }
    }

    suspend fun subtract(fiat: LocalFiat) {
        if (_rawBalance.value.doubleValue == 0.0) {
            // attempt to fetch prior to append
            fetchBalance()
        } else {
            _rawBalance.value = (_rawBalance.value - fiat.underlyingTokenAmount).coerceAtLeast(Fiat.Zero)
        }
    }

    // TODO: split non-balance account handling out into a separate controller
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

            // TODO: segment this to have balance utilize getAccount(filter: Primary) and timelock check and nextPoolIndex to be handled separately
            retryable(
                maxRetries = 3,
                call = suspend { accountController.getAccounts(owner, owner) }
            )?.recoverCatching { error ->
                if (error is GetAccountsError.NotFound) {
                    // No account yet, let's create it
                    val createResult = accountController.createUserAccount(owner, Mint.usdc)
                    if (createResult.isSuccess) {
                        accountController.getAccounts(owner, owner)
                            .getOrElse { throw it }
                    } else {
                        throw createResult.exceptionOrNull() ?: Exception("Account creation failed")
                    }
                } else {
                    throw error
                }
            }?.map { response ->
                val primary =
                    // for now find the primary account for USDC
                    response.accounts.values.find {
                        it.accountType == AccountType.Primary && it.mint == Mint.usdc
                    }
                if (primary?.unusable == true) {
                    onTimelockUnlocked()
                }
                onNextIndexDetermined(response.nextPoolIndex)
                retrieveBalancesFromAccounts(response.accounts)
            }?.onSuccess { balances ->
                val totalBalance = balances.values.sum()
                mintBalanceMap.value = balances
                trace(
                    tag = "Balance",
                    message = "Updated balance is ${totalBalance.formatted()} USD",
                    type = TraceType.Process
                )
                _rawBalance.update { totalBalance }
                fetching.store(false)
            }?.onFailure {
                fetching.store(false)
            }
        }
    }

    private fun retrieveBalancesFromAccounts(accounts: Map<PublicKey, AccountInfo>): Map<PublicKey, Fiat> {
        val balances = mutableMapOf<PublicKey, Fiat>()
        timedTrace(
            tag = "Balance",
            message = "parsing balance from accounts",
            type = TraceType.Process,
        ) {
            for ((_, info) in accounts) {
                if (info.accountType == AccountType.Primary) {
//                    balances[info.mint] = info.balance
                }
            }
        }

        return balances
    }

    fun reset() {
        _rawBalance.value = Fiat.Zero
        mintBalanceMap.value = emptyMap()
        cluster.value = null
    }
}