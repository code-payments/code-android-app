package com.getcode.opencode.controllers

import com.getcode.crypt.MnemonicPhrase
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.api.intents.IntentCreateAccount
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountResponse
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.accounts.PoolAccount
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
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
    private val transactionController: TransactionController,
    private val networkObserver: NetworkConnectivityListener,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val cluster = MutableStateFlow<AccountCluster?>(null)

    private val fetching = AtomicBoolean(false)

    var onTimelockUnlocked: (() -> Unit) = { }
    var onNextIndexDetermined: ((Long) -> Unit) = { }

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

    suspend fun createUserAccount(owner: AccountCluster): Result<ID> {
        // Authority is the owner of the account
        val intent = IntentCreateAccount.createUserAccount(owner)

        return transactionController.submitIntent(scope, intent, owner.authority.keyPair)
            .map { it.id.bytes }
    }

    suspend fun createPoolAccount(
        owner: AccountCluster,
        index: Long,
        mnemonic: MnemonicPhrase,
    ): Result<PoolAccount> {
        val poolAccount = PoolAccount.create(index, mnemonic)
        val intent = IntentCreateAccount.createPoolAccount(owner, poolAccount.cluster, index)

        return transactionController.submitIntent(scope, intent, owner.authority.keyPair)
            .map { poolAccount }
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
                    val createResult = createUserAccount(owner)
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
                val primary =
                    response.accounts.values.find {
                        it.accountType == AccountType.Primary && it.mint == Mint.usdc
                    }
                if (primary?.unusable == true) {
                    onTimelockUnlocked()
                }

                onNextIndexDetermined(response.nextPoolIndex)
            }?.onSuccess {
                fetching.store(false)
            }?.onFailure {
                fetching.store(false)
            }
        }
    }
}