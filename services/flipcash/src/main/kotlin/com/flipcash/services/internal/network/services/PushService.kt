package com.flipcash.services.internal.network.services

import com.flipcash.services.internal.network.api.PushApi
import com.flipcash.services.models.AddTokenError
import com.flipcash.services.models.DeleteTokenError
import com.getcode.ed25519.Ed25519.KeyPair
import javax.inject.Inject
import com.codeinc.flipcash.gen.push.v1.PushService as RpcPushService

internal class PushService @Inject constructor(
    private val api: PushApi,
) {
    suspend fun addToken(
        owner: KeyPair,
        token: String,
        installationId: String?
    ): Result<Unit> {
        return runCatching {
            api.addToken(owner, token, installationId)
        }.fold(
            onSuccess = { response ->
                when (response.result) {
                    RpcPushService.AddTokenResponse.Result.OK -> Result.success(Unit)
                    RpcPushService.AddTokenResponse.Result.INVALID_PUSH_TOKEN -> Result.failure(AddTokenError.InvalidPushToken())
                    RpcPushService.AddTokenResponse.Result.UNRECOGNIZED -> Result.failure( AddTokenError.Unrecognized())
                    else -> Result.failure(AddTokenError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(AddTokenError.Other(cause = cause))
            }
        )
    }

    suspend fun deleteTokens(
        owner: KeyPair,
        installationId: String?,
    ): Result<Unit> {
        return runCatching {
            api.deleteTokens(owner, installationId)
        }.fold(
            onSuccess = { response ->
                when (response.result) {
                    RpcPushService.DeleteTokensResponse.Result.OK -> Result.success(Unit)
                    RpcPushService.DeleteTokensResponse.Result.UNRECOGNIZED -> Result.failure( DeleteTokenError.Unrecognized())
                    else -> Result.failure(DeleteTokenError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(DeleteTokenError.Other(cause = cause))
            }
        )
    }
}