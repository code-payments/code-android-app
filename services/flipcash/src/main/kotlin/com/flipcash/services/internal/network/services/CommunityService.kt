package com.flipcash.services.internal.network.services

import com.flipcash.services.internal.network.api.CommunityApi
import com.flipcash.services.models.GetCommunityError
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.utils.toValidationOrElse
import com.getcode.solana.keys.Mint
import javax.inject.Inject
import com.codeinc.flipcash.gen.community.v1.Model as CommunityModel
import com.codeinc.flipcash.gen.community.v1.CommunityService as RpcCommunityService

internal class CommunityService @Inject constructor(
    private val api: CommunityApi,
) {
    suspend fun getCommunity(
        owner: KeyPair,
        mint: Mint,
    ): Result<CommunityModel.CommunityMetadata> {
        return runCatching {
            api.getCommunity(owner, mint)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    RpcCommunityService.GetCommunityResponse.Result.OK -> Result.success(response.community)
                    RpcCommunityService.GetCommunityResponse.Result.NOT_FOUND -> Result.failure(GetCommunityError.NotFound())
                    RpcCommunityService.GetCommunityResponse.Result.UNRECOGNIZED -> Result.failure(GetCommunityError.Unrecognized())
                    else -> Result.failure(GetCommunityError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetCommunityError.Other(cause = it) })
            }
        )
    }
}
