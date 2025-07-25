package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.network.services.ThirdPartyService
import com.flipcash.services.models.Jwt
import com.flipcash.services.repository.ThirdPartyRepository
import com.getcode.ed25519.Ed25519
import com.getcode.network.jwt.JwtSecuredEndpoint
import com.getcode.utils.ErrorUtils

internal class InternalThirdPartyRepository(
    private val service: ThirdPartyService
) : ThirdPartyRepository {
    override suspend fun getJwt(
        apiKey: String?,
        endpoint: JwtSecuredEndpoint,
        owner: Ed25519.KeyPair
    ): Result<Jwt> = service.getJwt(apiKey, endpoint, owner)
        .onFailure { ErrorUtils.handleError(it) }
}