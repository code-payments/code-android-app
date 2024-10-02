package com.getcode.network.client

import com.codeinc.gen.account.v1.AccountService
import com.getcode.ed25519.Ed25519
import com.getcode.network.api.AccountApi
import com.getcode.network.core.NetworkOracle
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

class AccountService @Inject constructor(
    private val api: AccountApi,
    private val networkOracle: NetworkOracle,
) {

    suspend fun isCodeAccount(owner: Ed25519.KeyPair): Result<Boolean> {
        return try {
            networkOracle.managedRequest(api.isCodeAccount(owner))
                .map { response ->
                    when (response.result) {
                        AccountService.IsCodeAccountResponse.Result.OK -> {
                            Result.success(true)
                        }

                        AccountService.IsCodeAccountResponse.Result.NOT_FOUND -> {
                            val error = Throwable("Error: account not found")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        AccountService.IsCodeAccountResponse.Result.UNLOCKED_TIMELOCK_ACCOUNT -> {
                            val error = Throwable("Error: unlocked timelock account")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        AccountService.IsCodeAccountResponse.Result.UNRECOGNIZED -> {
                            val error = Throwable("Error: Unrecognized request.")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        else -> {
                            val error = Throwable("Error: Unknown")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            Result.failure(e)
        }
    }

    suspend fun linkAdditionalAccounts(owner: Ed25519.KeyPair, linkedAccount: Ed25519.KeyPair): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.linkAdditionalAccounts(owner, linkedAccount))
                .map { response ->
                    when (response.result) {
                        AccountService.LinkAdditionalAccountsResponse.Result.OK -> {
                            Result.success(Unit)
                        }
                        AccountService.LinkAdditionalAccountsResponse.Result.DENIED -> {
                            val error = Throwable("Error: Denied.")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        AccountService.LinkAdditionalAccountsResponse.Result.INVALID_ACCOUNT -> {
                            val error = Throwable("Error: Invalid account.")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        AccountService.LinkAdditionalAccountsResponse.Result.UNRECOGNIZED -> {
                            val error = Throwable("Error: Unrecognized request.")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        else -> {
                            val error = Throwable("Error: Unknown")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            Result.failure(e)
        }
    }
}