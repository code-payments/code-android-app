package com.getcode.opencode.controllers

import com.getcode.opencode.internal.network.api.intents.IntentCreateAccount
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountResponse
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
        val intent = IntentCreateAccount.createUserAccount(owner)

        return transactionController.submitIntent(scope, intent, owner.authority.keyPair)
            .map { it.id.bytes }
    }

    suspend fun createPoolAccount(owner: AccountCluster, index: Long): Result<ID> {
        val intent = IntentCreateAccount.createPoolAccount(owner, index)

        return transactionController.submitIntent(scope, intent, owner.authority.keyPair)
            .map { it.id.bytes }
    }


    suspend fun getAccounts(
        accountOwner: AccountCluster,
        requestingOwner: AccountCluster
    ): Result<AccountResponse> {
        return accountRepository.getAccounts(
            accountOwner = accountOwner.authority.keyPair,
            requestingOwner = requestingOwner.authority.keyPair
        )
    }
}