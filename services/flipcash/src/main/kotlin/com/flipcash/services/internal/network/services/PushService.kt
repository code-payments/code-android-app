package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.push.v1.PushService  as RpcPushService
import com.flipcash.services.internal.network.api.PushApi
import com.flipcash.services.models.AddTokenError
import com.flipcash.services.models.DeleteTokenError
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.NetworkOracle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

internal class PushService @Inject constructor(
    private val api: PushApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun addToken(
        owner: KeyPair,
        token: String,
        installationId: String?
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.addToken(owner, token, installationId))
                .map {
                    when (it.result) {
                        RpcPushService.AddTokenResponse.Result.OK -> Result.success(Unit)
                        RpcPushService.AddTokenResponse.Result.INVALID_PUSH_TOKEN -> {
                            val error = AddTokenError.InvalidPushToken()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        RpcPushService.AddTokenResponse.Result.UNRECOGNIZED -> {
                            val error = AddTokenError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        else -> {
                            val error = AddTokenError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = AddTokenError.Other(cause = e)
            Result.failure(error)
        }
    }

    suspend fun deleteTokens(
        owner: KeyPair,
        installationId: String?,
    ): Result<Unit> {
        return try {
            networkOracle.managedRequest(api.deleteTokens(owner, installationId))
                .map {
                    when (it.result) {
                        RpcPushService.DeleteTokensResponse.Result.OK -> Result.success(Unit)
                        RpcPushService.DeleteTokensResponse.Result.UNRECOGNIZED -> {
                            val error = DeleteTokenError.Unrecognized()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                        else -> {
                            val error = DeleteTokenError.Other()
                            Timber.e(t = error)
                            Result.failure(error)
                        }
                    }
                }.first()
        } catch (e: Exception) {
            val error = DeleteTokenError.Other(cause = e)
            Result.failure(error)
        }
    }
}