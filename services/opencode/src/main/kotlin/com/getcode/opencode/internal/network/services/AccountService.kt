package com.getcode.opencode.internal.network.services

import com.codeinc.opencode.gen.account.v1.AccountService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.api.AccountApi
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.model.accounts.AccountFilter
import com.getcode.opencode.model.accounts.AccountInfo
import com.getcode.opencode.model.accounts.AccountResponse
import com.getcode.opencode.model.core.errors.CodeAccountCheckError
import com.getcode.opencode.model.core.errors.GetAccountsError
import com.getcode.solana.keys.PublicKey
import javax.inject.Inject

internal class AccountService @Inject constructor(
    private val api: AccountApi,
) {
    suspend fun isCodeAccount(owner: KeyPair): Result<Boolean> {
        return runCatching {
            api.isCodeAccount(owner)
        }.fold(
            onSuccess = { response ->
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
            onFailure = { cause ->
                Result.failure(CodeAccountCheckError.Other(cause = cause))
            }
        )
    }

    suspend fun getAccounts(
        accountOwner: KeyPair,
        requestingOwner: KeyPair,
        filter: AccountFilter? = null,
    ): Result<AccountResponse> {
        return runCatching {
            api.getTokenAccounts(accountOwner, requestingOwner, filter)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    AccountService.GetTokenAccountInfosResponse.Result.OK -> {
                        val container = mutableMapOf<PublicKey, AccountInfo>()

                        for ((base58, info) in response.tokenAccountInfosMap) {
                            val account = PublicKey.fromBase58(base58)
                            val accountInfo = AccountInfo.newInstance(info)
                            if (accountInfo != null) {
                                container[account] = accountInfo
                            }
                        }


                        Result.success(
                            AccountResponse(
                                accounts = container.toMap(),
                                nextPoolIndex = response.nextPoolIndex,
                            )
                        )
                    }
                    AccountService.GetTokenAccountInfosResponse.Result.NOT_FOUND -> Result.failure(
                        GetAccountsError.NotFound())
                    AccountService.GetTokenAccountInfosResponse.Result.UNRECOGNIZED -> Result.failure(
                        GetAccountsError.Unrecognized())
                    else -> Result.failure(GetAccountsError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(GetAccountsError.Other(cause = cause))
            }
        )
    }
}

