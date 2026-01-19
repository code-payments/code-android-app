package com.getcode.opencode.internal.domain.repositories

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.services.AccountService
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountResponse
import com.getcode.opencode.repositories.AccountRepository
import javax.inject.Inject

internal class InternalAccountRepository @Inject constructor(
    private val service: AccountService,
) : AccountRepository {
    override suspend fun isCodeAccount(
        owner: Ed25519.KeyPair
    ): Result<Boolean> = service.isValidAccount(owner)

    override suspend fun getAccounts(
        accountOwner: Ed25519.KeyPair,
        requestingOwner: Ed25519.KeyPair,
        filter: AccountFilter?,
    ): Result<AccountResponse> = service.getAccounts(accountOwner, requestingOwner, filter)

    override suspend fun getAccount(
        accountOwner: Ed25519.KeyPair,
        requestingOwner: Ed25519.KeyPair,
        filter: AccountFilter,
    ): Result<AccountInfo> = getAccounts(accountOwner, requestingOwner, filter)
        .map { response -> response.accounts.values.firstOrNull() }
        .fold(
            onSuccess = {
                if (it != null) {
                    Result.success(it)
                } else {
                    Result.failure(Exception("Account not found"))
                }
            },
            onFailure = { Result.failure(it) }
        )
}