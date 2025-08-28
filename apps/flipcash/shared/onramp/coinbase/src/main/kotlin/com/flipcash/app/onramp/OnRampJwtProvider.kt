package com.flipcash.app.onramp

import com.flipcash.services.controllers.ThirdPartyController
import com.getcode.network.jwt.Jwt
import com.getcode.network.jwt.JwtProvider
import com.getcode.network.jwt.JwtSecuredEndpoint
import javax.inject.Inject

class OnRampJwtProvider @Inject constructor(
    private val thirdPartyController: ThirdPartyController,
): JwtProvider {
    override suspend fun provideJwtForEndpoint(
        apiKey: String,
        endpoint: JwtSecuredEndpoint,
    ): Result<Jwt> {
        return thirdPartyController.getJwt(apiKey, endpoint)
    }
}