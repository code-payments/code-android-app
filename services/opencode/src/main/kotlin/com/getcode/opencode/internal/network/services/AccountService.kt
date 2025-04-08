package com.getcode.opencode.internal.network.services

import com.codeinc.opencode.gen.account.v1.AccountService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.api.AccountApi
import com.getcode.opencode.internal.network.core.NetworkOracle
import com.getcode.opencode.internal.network.managedApiRequest
import com.getcode.opencode.model.core.errors.CodeAccountCheckError
import com.getcode.opencode.model.core.errors.LinkAccountsError
import javax.inject.Inject

internal class AccountService @Inject constructor(
    private val api: AccountApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun isCodeAccount(owner: KeyPair): Result<Boolean> {
        return networkOracle.managedApiRequest(
            call = { api.isCodeAccount(owner) },
            handleResponse = { response ->
                when (response.result) {
                    AccountService.IsCodeAccountResponse.Result.OK -> Result.success(true)
                    AccountService.IsCodeAccountResponse.Result.NOT_FOUND -> Result.failure(
                        CodeAccountCheckError.NotFound())
                    AccountService.IsCodeAccountResponse.Result.UNLOCKED_TIMELOCK_ACCOUNT -> Result.failure(
                        CodeAccountCheckError.UnlockedTimelockAccount())
                    AccountService.IsCodeAccountResponse.Result.UNRECOGNIZED -> Result.failure(
                        CodeAccountCheckError.Unrecognized())
                    else -> Result.failure(CodeAccountCheckError.Other())
                }
            },
            onOtherError = { cause ->
                Result.failure(CodeAccountCheckError.Other(cause = cause))
            }
        )
    }

    // TODO: implement getAccounts

    suspend fun linkAdditionalAccounts(
        owner: KeyPair,
        accountToLink: KeyPair,
    ): Result<Unit> {
        return networkOracle.managedApiRequest(
            call = { api.linkAdditionalAccounts(owner, accountToLink) },
            handleResponse = { response ->
                when (response.result) {
                    AccountService.LinkAdditionalAccountsResponse.Result.OK -> Result.success(Unit)
                    AccountService.LinkAdditionalAccountsResponse.Result.DENIED -> Result.failure(
                        LinkAccountsError.Denied())
                    AccountService.LinkAdditionalAccountsResponse.Result.INVALID_ACCOUNT -> Result.failure(
                        LinkAccountsError.InvalidAccount())
                    AccountService.LinkAdditionalAccountsResponse.Result.UNRECOGNIZED -> Result.failure(
                        LinkAccountsError.Unrecognized())
                    else -> Result.failure(LinkAccountsError.Other())
                }
            },
            onOtherError = { cause ->
                Result.failure(LinkAccountsError.Other(cause = cause))
            }
        )
    }
}

