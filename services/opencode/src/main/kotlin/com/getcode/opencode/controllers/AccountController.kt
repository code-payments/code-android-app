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
import com.getcode.opencode.model.core.errors.SubmitIntentError
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
import kotlinx.coroutines.flow.filter
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
        if (this.cluster.value != cluster) {
            // A different account than this singleton last served (e.g. logout -> new/again
            // login without a process restart). Drop the previous account's cached account
            // list so it can't bleed into the new account — otherwise consumers (balances,
            // hasAccountFor, and the onboarding core-account gate) can observe the prior
            // account's accounts, most damagingly its USDF primary.
            accounts.value = emptyList()
        }
        this.cluster.value = cluster
    }

    init {
        cluster.filterNotNull()
            .flatMapLatest { networkObserver.state.map { it.connected } }
            .distinctUntilChanged()
            .filter { it }
            .onEach {
                retryable { fetchAdditionalAccountInfo() }
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

    /**
     * Ensures the core USDF account exists for [owner], creating it if the server
     * reports none yet. Idempotent: a no-op once the account is known.
     *
     * Unlike [fetchAdditionalAccountInfo] (the reactive bootstrap that runs after
     * login), this is an explicit, awaitable call used to GATE onboarding before
     * the user is released to the scanner. A [Result.failure] — e.g. an antispam
     * [SubmitIntentError.Denied] — means the caller must NOT proceed.
     */
    suspend fun ensureCoreAccount(owner: AccountCluster): Result<Unit> {
        // Source of truth is the server, not the in-memory [accounts] cache. That cache can
        // still hold a PRIOR account's USDF primary when a new account onboards in the same
        // process (logout -> re-onboard, or an account switch). Short-circuiting on it made
        // this gate report "already present" and release a fresh account to the scanner with
        // no core account server-side — so it could never receive a direct-send tip until a
        // process restart cleared the cache. Always confirm the current owner against
        // getAccounts here.
        return getAccounts(owner, owner).fold(
            onSuccess = { response ->
                accounts.value = response.accounts.values.toList()
                if (hasCoreMintPrimary()) {
                    trace(tag = "Onboarding", message = "USDF core account already present", type = TraceType.Process)
                    Result.success(Unit)
                } else {
                    // The server responded, but the owner has no USDF core-mint PRIMARY yet
                    // (e.g. a freshly-onboarded owner with only non-primary/other-mint
                    // accounts, or a create still racing the reactive bootstrap). The server
                    // recognizes an OCP user — and can auto-open currency destinations for
                    // direct-send tips — only once a USDF primary exists, so provision it
                    // before the onboarding gate releases the user to the scanner. A
                    // successful lookup that lacks the primary is NOT "already provisioned".
                    provisionCoreAccount(owner)
                }
            },
            onFailure = { error ->
                if (error is GetAccountsError.NotFound) {
                    provisionCoreAccount(owner)
                } else {
                    trace(tag = "Onboarding", message = "USDF core account lookup failed", error = error, type = TraceType.Error)
                    Result.failure(error)
                }
            }
        )
    }

    /**
     * Whether the local account state holds a USDF core-mint PRIMARY. This is the exact
     * account the OCP server keys owner-recognition off of, so onboarding must confirm it
     * specifically — a USDF balance under a non-primary account type does not count.
     */
    private fun hasCoreMintPrimary(): Boolean =
        accounts.value.any { it.mint == Mint.usdf && it.accountType == AccountType.Primary }

    /**
     * Submits the OpenAccounts intent for the USDF core-mint primary and refreshes local
     * state. Tolerates losing a race to a concurrent provision (e.g. the reactive account
     * bootstrap): if the create is rejected but a re-fetch shows the primary now exists,
     * the gate still succeeds rather than blocking onboarding on a redundant open.
     */
    private suspend fun provisionCoreAccount(owner: AccountCluster): Result<Unit> {
        trace(tag = "Onboarding", message = "Provisioning USDF core account (onboarding gate)", type = TraceType.Process)
        return createUserAccount(owner, mint = Mint.usdf).fold(
            onSuccess = {
                trace(tag = "Onboarding", message = "USDF core account provisioned", type = TraceType.Process)
                getAccounts(owner, owner).onSuccess {
                    accounts.value = it.accounts.values.toList()
                }
                Result.success(Unit)
            },
            onFailure = { error ->
                // A concurrent provision may have already opened the core account, making
                // this create redundant. Re-check before surfacing the failure.
                getAccounts(owner, owner).onSuccess {
                    accounts.value = it.accounts.values.toList()
                }
                if (hasCoreMintPrimary()) {
                    trace(tag = "Onboarding", message = "USDF core account provisioned by concurrent open", type = TraceType.Process)
                    Result.success(Unit)
                } else {
                    trace(tag = "Onboarding", message = "USDF core account provisioning failed", error = error, type = TraceType.Error)
                    Result.failure(error)
                }
            }
        )
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