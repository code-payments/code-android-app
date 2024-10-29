package com.getcode.oct24.internal.network.service

import com.codeinc.flipchat.gen.account.v1.AccountService.*
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID
import com.getcode.oct24.internal.annotations.FcNetworkOracle
import com.getcode.oct24.internal.network.api.AccountApi
import com.getcode.oct24.internal.network.core.NetworkOracle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

internal class AccountService @Inject constructor(
    private val api: AccountApi,
    @FcNetworkOracle private val networkOracle: NetworkOracle,
) {
    suspend fun register(owner: KeyPair, displayName: String): Result<ID> {
        return try {
            networkOracle.managedRequest(api.register(owner, displayName))
                .map { response ->
                    when (response.result) {
                        RegisterResponse.Result.OK -> {
                            Result.success(response.userId.value.toByteArray().toList())
                        }

                        RegisterResponse.Result.INVALID_SIGNATURE -> {
                            val error = RegisterError.InvalidSignature(response.errorReason)
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        RegisterResponse.Result.INVALID_DISPLAY_NAME -> {
                            val error = RegisterError.InvalidDisplayName(response.errorReason)
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        RegisterResponse.Result.UNRECOGNIZED -> {
                            val error = RegisterError.Unrecognized(response.errorReason)
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        else -> {
                            val error = RegisterError.Other("Failed to register")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = RegisterError.Other("Road to greatness is bumpy. Apologies for the hiccup.", cause = e)
            Result.failure(error)
        }
    }

    suspend fun login(owner: KeyPair): Result<ID> {
        return try {
            networkOracle.managedRequest(api.login(owner))
                .map { response ->
                    when (response.result) {
                        LoginResponse.Result.OK -> {
                            Result.success(response.userId.value.toByteArray().toList())
                        }

                        LoginResponse.Result.UNRECOGNIZED -> {
                            val error = LoginError.Unrecognized("Failed to login")
                            Timber.e(t = error)
                            Result.failure(error)
                        }

                        LoginResponse.Result.INVALID_TIMESTAMP -> {
                            val error = LoginError.InvalidTimestamp("Failed to login")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        LoginResponse.Result.DENIED -> {
                            val error = LoginError.Denied("Failed to login")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        else -> {
                            val error = LoginError.Other("Failed to login")
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = RegisterError.Other("Road to greatness is bumpy. Apologies for the hiccup.", cause = e)
            Result.failure(error)
        }
    }

    sealed class LoginError(override val message: String): Throwable(message) {
        data class InvalidTimestamp(override val message: String): LoginError(message)
        data class NotFound(override val message: String): LoginError(message)
        data class Denied(override val message: String): LoginError(message)
        data class Unrecognized(override val message: String): LoginError(message)
        data class Other(override val message: String, override val cause: Throwable? = null): LoginError(message)
    }

    sealed class RegisterError(override val message: String): Throwable(message) {
        data class InvalidSignature(override val message: String): RegisterError(message)
        data class InvalidDisplayName(override val message: String): RegisterError(message)
        data class Unrecognized(override val message: String): RegisterError(message)
        data class Other(override val message: String, override val cause: Throwable? = null): RegisterError(message)
    }
}