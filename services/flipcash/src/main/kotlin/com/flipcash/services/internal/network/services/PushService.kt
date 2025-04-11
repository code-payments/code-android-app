package com.flipcash.services.internal.network.services

import com.flipcash.services.internal.network.api.PushApi
import com.flipcash.services.internal.network.managedApiRequest
import com.flipcash.services.models.AddTokenError
import com.flipcash.services.models.DeleteTokenError
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.core.NetworkOracle
import javax.inject.Inject
import com.codeinc.flipcash.gen.push.v1.PushService as RpcPushService

internal class PushService @Inject constructor(
    private val api: PushApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun addToken(
        owner: KeyPair,
        token: String,
        installationId: String?
    ): Result<Unit> {
        return networkOracle.managedApiRequest(
            call = { api.addToken(owner, token, installationId) },
            handleResponse = { response ->
                when (response.result) {
                    RpcPushService.AddTokenResponse.Result.OK -> Result.success(Unit)
                    RpcPushService.AddTokenResponse.Result.INVALID_PUSH_TOKEN -> Result.failure(AddTokenError.InvalidPushToken())
                    RpcPushService.AddTokenResponse.Result.UNRECOGNIZED -> Result.failure( AddTokenError.Unrecognized())
                    else -> Result.failure(AddTokenError.Other())
                }
            },
            onOtherError = { error ->
                Result.failure(AddTokenError.Other(cause = error))
            }
        )
    }

    suspend fun deleteTokens(
        owner: KeyPair,
        installationId: String?,
    ): Result<Unit> {
        return networkOracle.managedApiRequest(
            call = { api.deleteTokens(owner, installationId) },
            handleResponse = { response ->
                when (response.result) {
                    RpcPushService.DeleteTokensResponse.Result.OK -> Result.success(Unit)
                    RpcPushService.DeleteTokensResponse.Result.UNRECOGNIZED -> Result.failure( DeleteTokenError.Unrecognized())
                    else -> Result.failure(DeleteTokenError.Other())
                }
            },
            onOtherError = { error ->
                Result.failure(DeleteTokenError.Other(cause = error))
            }
        )
    }
}