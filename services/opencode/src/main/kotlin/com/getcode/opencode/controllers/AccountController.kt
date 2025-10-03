package com.getcode.opencode.controllers

import com.getcode.crypt.MnemonicPhrase
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.api.intents.IntentCreateAccount
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountResponse
import com.getcode.opencode.model.accounts.PoolAccount
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.repositories.AccountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountController @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionController: TransactionController,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
}