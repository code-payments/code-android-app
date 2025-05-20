package com.flipcash.services.internal.network.services

import com.flipcash.services.internal.network.api.AccountApi
import com.flipcash.services.models.GetUserFlagsError
import com.flipcash.services.models.LoginError
import com.flipcash.services.models.RegisterError
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID
import javax.inject.Inject
import com.codeinc.flipcash.gen.account.v1.FlipcashAccountService as RpcAccountService

internal class AccountService @Inject constructor(
    private val api: AccountApi,
) {
    suspend fun register(owner: KeyPair): Result<ID> {
        return runCatching {
            api.register(owner)
        }.fold(
            onSuccess = { response ->
                when (response.result) {
                    RpcAccountService.RegisterResponse.Result.OK -> {
                        Result.success(response.userId.value.toByteArray().toList())
                    }

                    RpcAccountService.RegisterResponse.Result.INVALID_SIGNATURE -> {
                        Result.failure(RegisterError.InvalidSignature())
                    }

                    RpcAccountService.RegisterResponse.Result.DENIED -> {
                        Result.failure(RegisterError.Denied())
                    }

                    RpcAccountService.RegisterResponse.Result.UNRECOGNIZED -> {
                        Result.failure(RegisterError.Unrecognized())
                    }

                    else -> Result.failure(RegisterError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(RegisterError.Other(cause = cause))
            }
        )
    }

    suspend fun login(owner: KeyPair): Result<ID> {
        return runCatching {
            api.login(owner)
        }.fold(
            onSuccess = { response ->
                when (response.result) {
                    RpcAccountService.LoginResponse.Result.OK -> {
                        Result.success(response.userId.value.toByteArray().toList())
                    }

                    RpcAccountService.LoginResponse.Result.INVALID_TIMESTAMP -> {
                        Result.failure(LoginError.InvalidTimestamp())
                    }

                    RpcAccountService.LoginResponse.Result.DENIED -> {
                        Result.failure(LoginError.Denied())
                    }

                    RpcAccountService.LoginResponse.Result.UNRECOGNIZED -> {
                        Result.failure(LoginError.Unrecognized())
                    }

                    else -> Result.failure(LoginError.Other())
                }

            },
            onFailure = { cause ->
                Result.failure(LoginError.Other(cause = cause))
            }
        )
    }

    suspend fun getUserFlags(owner: KeyPair, userId: ID): Result<RpcAccountService.UserFlags> {
        return runCatching {
            api.getUserFlags(userId, owner)
        }.fold(
            onSuccess = { response ->
                when (response.result) {
                    RpcAccountService.GetUserFlagsResponse.Result.OK -> Result.success(response.userFlags)

                    RpcAccountService.GetUserFlagsResponse.Result.DENIED -> {
                        Result.failure(GetUserFlagsError.Denied())
                    }

                    RpcAccountService.GetUserFlagsResponse.Result.UNRECOGNIZED -> {
                        Result.failure(GetUserFlagsError.Unrecognized())
                    }

                    else -> Result.failure(GetUserFlagsError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(GetUserFlagsError.Other(cause = cause))
            }
        )
    }
}