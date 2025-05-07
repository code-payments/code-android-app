package com.flipcash.services.internal.network.services

import com.flipcash.services.internal.network.api.AccountApi
import com.flipcash.services.internal.network.managedApiRequest
import com.flipcash.services.models.GetUserFlagsError
import com.flipcash.services.models.LoginError
import com.flipcash.services.models.RegisterError
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.DEFAULT_STREAM_TIMEOUT
import com.getcode.opencode.internal.network.core.NetworkOracle
import com.getcode.opencode.model.core.ID
import com.getcode.utils.trace
import kotlinx.coroutines.flow.catch
import javax.inject.Inject
import com.codeinc.flipcash.gen.account.v1.FlipcashAccountService as RpcAccountService

internal class AccountService @Inject constructor(
    private val api: AccountApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun register(owner: KeyPair): Result<ID> {
        return networkOracle.managedApiRequest(
            call = {
                api.register(owner)
                    .catch {
                        trace("gRPC error: $it")
                    }
            },
            timeout = DEFAULT_STREAM_TIMEOUT * 3,
            handleResponse = { response ->
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
            onOtherError = { Result.failure(RegisterError.Other(cause = it)) }
        )
    }

    suspend fun login(owner: KeyPair): Result<ID> {
        return networkOracle.managedApiRequest(
            call = { api.login(owner) },
            timeout = DEFAULT_STREAM_TIMEOUT * 3,
            handleResponse = { response ->
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
            onOtherError = { Result.failure(LoginError.Other(cause = it)) }
        )
    }

    suspend fun getUserFlags(owner: KeyPair, userId: ID): Result<RpcAccountService.UserFlags> {
        return networkOracle.managedApiRequest(
            call = { api.getUserFlags(userId, owner) },
            handleResponse = { response ->
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
            onOtherError = { Result.failure(GetUserFlagsError.Other(cause = it)) }
        )
    }
}