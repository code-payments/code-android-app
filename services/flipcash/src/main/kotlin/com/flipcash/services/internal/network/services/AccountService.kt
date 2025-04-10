package com.flipcash.services.internal.network.services

import com.flipcash.services.internal.network.api.AccountApi
import com.flipcash.services.models.GetUserFlagsError
import com.flipcash.services.models.LoginError
import com.flipcash.services.models.RegisterError
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.NetworkOracle
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import com.codeinc.flipcash.gen.account.v1.AccountService as RpcAccountService

internal class AccountService @Inject constructor(
    private val api: AccountApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun register(owner: KeyPair): Result<ID> {
        return try {
            networkOracle.managedRequest(api.register(owner))
                .map { response ->
                    when (response.result) {
                        RpcAccountService.RegisterResponse.Result.OK -> {
                            Result.success(response.userId.value.toByteArray().toList())
                        }

                        RpcAccountService.RegisterResponse.Result.INVALID_SIGNATURE -> {
                            val error = RegisterError.InvalidSignature()
                            Result.failure(error)
                        }

                        RpcAccountService.RegisterResponse.Result.DENIED -> {
                            val error = RegisterError.Denied()
                            Result.failure(error)
                        }

                        RpcAccountService.RegisterResponse.Result.UNRECOGNIZED -> {
                            val error = RegisterError.Unrecognized()
                            Result.failure(error)
                        }

                        else -> {
                            val error = RegisterError.Other()
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = RegisterError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun login(owner: KeyPair): Result<ID> {
        return try {
            networkOracle.managedRequest(api.login(owner))
                .map { response ->
                    when (response.result) {
                        RpcAccountService.LoginResponse.Result.OK -> {
                            Result.success(response.userId.value.toByteArray().toList())
                        }

                        RpcAccountService.LoginResponse.Result.UNRECOGNIZED -> {
                            val error = LoginError.Unrecognized()
                            Result.failure(error)
                        }

                        RpcAccountService.LoginResponse.Result.INVALID_TIMESTAMP -> {
                            val error = LoginError.InvalidTimestamp()
                            Result.failure(error)
                        }

                        RpcAccountService.LoginResponse.Result.DENIED -> {
                            val error = LoginError.Denied()
                            Result.failure(error)
                        }

                        else -> {
                            val error = LoginError.Other()
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = RegisterError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun getUserFlags(owner: KeyPair, userId: ID): Result<RpcAccountService.UserFlags> {
        return try {
            networkOracle.managedRequest(api.getUserFlags(owner = owner, userId = userId))
                .map { response ->
                    when (response.result) {
                        RpcAccountService.GetUserFlagsResponse.Result.OK -> Result.success(response.userFlags)
                        RpcAccountService.GetUserFlagsResponse.Result.DENIED -> {
                            val error = GetUserFlagsError.Denied()
                            Result.failure(error)
                        }

                        RpcAccountService.GetUserFlagsResponse.Result.UNRECOGNIZED -> {
                            val error = GetUserFlagsError.Unrecognized()
                            Result.failure(error)
                        }

                        else -> {
                            val error = GetUserFlagsError.Other()
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = GetUserFlagsError.Other(cause = e)
            Result.failure(error)
        }
    }
}