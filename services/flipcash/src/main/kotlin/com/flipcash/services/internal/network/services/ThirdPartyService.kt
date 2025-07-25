package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.thirdparty.v1.ThirdPartyService
import com.flipcash.services.internal.network.api.ThirdPartyApi
import com.flipcash.services.models.GetJwtError
import com.flipcash.services.models.Jwt
import com.getcode.ed25519.Ed25519
import com.getcode.network.jwt.JwtSecuredEndpoint
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import javax.inject.Inject

internal class ThirdPartyService @Inject constructor(
    private val api: ThirdPartyApi,
) {
    suspend fun getJwt(
        apiKey: String?,
        endpoint: JwtSecuredEndpoint,
        owner: Ed25519.KeyPair,
    ): Result<Jwt> {
        return runCatching {
            api.getJwt(apiKey, endpoint, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    ThirdPartyService.GetJwtResponse.Result.OK -> Result.success(response.jwt.value)
                    ThirdPartyService.GetJwtResponse.Result.DENIED -> Result.failure(GetJwtError.Denied())
                    ThirdPartyService.GetJwtResponse.Result.UNSUPPORTED_PROVIDER -> Result.failure(GetJwtError.UnsupportedProvider())
                    ThirdPartyService.GetJwtResponse.Result.INVALID_API_KEY -> Result.failure(GetJwtError.InvalidApiKey())
                    ThirdPartyService.GetJwtResponse.Result.PHONE_VERIFICATION_REQUIRED -> Result.failure(GetJwtError.PhoneVerificationRequired())
                    ThirdPartyService.GetJwtResponse.Result.EMAIL_VERIFICATION_REQUIRED -> Result.failure(GetJwtError.EmailVerificationRequired())
                    ThirdPartyService.GetJwtResponse.Result.UNRECOGNIZED -> Result.failure(GetJwtError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(GetJwtError.Other(cause))
            }
        )
    }
}