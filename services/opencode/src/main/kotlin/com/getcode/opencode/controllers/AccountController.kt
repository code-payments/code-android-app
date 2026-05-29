package com.getcode.opencode.controllers

import com.getcode.opencode.internal.network.executors.IntentExecutor
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountResponse
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.accounts.unusable
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.GetAccountsError
import com.getcode.opencode.repositories.AccountRepository
import com.getcode.solana.keys.Mint
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
@Singleton
class AccountController @Inject constructor(
    private val accountRepository: AccountRepository,
    private val networkObserver: NetworkConnectivityListener,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val cluster = MutableStateFlow<AccountCluster?>(null)

    private val accounts = MutableStateFlow<List<AccountInfo>>(emptyList())

    fun hasAccountFor(mint: Mint): Boolean {
        return accounts.value.any { it.mint == mint }
    }

    fun observeHasAccountFor(mint: Mint): Flow<Boolean> =
        accounts.map { list -> list.any { it.mint == mint } }.distinctUntilChanged()

    private val fetching = AtomicBoolean(false)

    var onTimelockUnlocked: (() -> Unit) = { }

    fun onUserLoggedIn(cluster: AccountCluster) {
        trace(
            tag = "Accounts",
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
                    retryable { fetchAdditionalAccountInfo() }
                }
            }.launchIn(scope)
    }

    /**
     * Creates a new user account for the given owner and mint.
     *
     * @param ownerForMint The AccountCluster to create the account for.
     * @param mint The Mint to create the account for.
     *
     * NOTE: The cluster should be updated for the corresponding Token associated with the Mint to ensure the vault is correct.
     *
     * @return The ID of the created account.
     */
    suspend fun createUserAccount(ownerForMint: AccountCluster, mint: Mint): Result<ID> {
        return accountRepository.createUserAccount(scope, ownerForMint, mint)
    }

    suspend fun getAccounts(
        accountOwner: AccountCluster,
        requestingOwner: AccountCluster,
        filter: AccountFilter? = null,
    ): Result<AccountResponse> {
        return accountRepository.getAccounts(
            accountOwner = accountOwner.authority.keyPair,
            requestingOwner = requestingOwner.authority.keyPair,
            filter = filter,
        )
    }

    suspend fun getAccount(
        accountOwner: AccountCluster,
        requestingOwner: AccountCluster,
        filter: AccountFilter,
    ): Result<AccountInfo> {
        return accountRepository.getAccount(
            accountOwner = accountOwner.authority.keyPair,
            requestingOwner = requestingOwner.authority.keyPair,
            filter = filter,
        )
    }

    suspend fun refreshAccountState() {
        fetchAdditionalAccountInfo()
    }

    private suspend fun fetchAdditionalAccountInfo() {
        val owner = cluster.value
        if (owner == null) {
            trace(
                tag = "Account",
                message = "Missing owner while fetching account info",
                type = TraceType.Error
            )
            return
        }

        if (!fetching.load()) {
            fetching.store(true)
            trace(
                tag = "Accounts",
                message = "Fetching Additional Data",
                type = TraceType.Process
            )
            retryable(
                maxRetries = 3,
                call = suspend { getAccounts(owner, owner) }
            )?.recoverCatching { error ->
                if (error is GetAccountsError.NotFound) {
                    // No account yet, let's create it
                    trace("Creating account for USDF")
                    val createResult = createUserAccount(owner, mint = Mint.usdf)
                    if (createResult.isSuccess) {
                        getAccounts(owner, owner)
                            .getOrElse { throw it }
                    } else {
                        throw createResult.exceptionOrNull() ?: Exception("Account creation failed")
                    }
                } else {
                    throw error
                }
            }?.map { response ->
                accounts.value = response.accounts.values.toList()
                val isUnlocked = response.accounts.any { it.value.unusable }

                if (isUnlocked) {
                    onTimelockUnlocked()
                }
            }?.onSuccess {
                fetching.store(false)
            }?.onFailure {
                fetching.store(false)
            }
        }
    }
}