package com.getcode.opencode.internal.domain.repositories

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.services.AccountService
import com.getcode.opencode.model.accounts.AccountResponse
import com.getcode.opencode.repositories.AccountRepository
import javax.inject.Inject

internal class InternalAccountRepository @Inject constructor(
    private val service: AccountService,
) : AccountRepository {
    override suspend fun isCodeAccount(
        owner: Ed25519.KeyPair
    ): Result<Boolean> = service.isCodeAccount(owner)

    override suspend fun getAccounts(
        accountOwner: Ed25519.KeyPair,
        requestingOwner: Ed25519.KeyPair,
    ): Result<AccountResponse> = service.getAccounts(accountOwner, requestingOwner)
}