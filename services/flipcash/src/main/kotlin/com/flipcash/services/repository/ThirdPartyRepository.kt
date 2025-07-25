package com.flipcash.services.repository

import com.flipcash.services.models.Jwt
import com.getcode.ed25519.Ed25519
import com.getcode.network.jwt.JwtSecuredEndpoint

interface ThirdPartyRepository {
    suspend fun getJwt(
        apiKey: String?,
        endpoint: JwtSecuredEndpoint,
        owner: Ed25519.KeyPair,
    ): Result<Jwt>
}