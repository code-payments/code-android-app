package com.flipcash.services.repository

import com.getcode.ed25519.Ed25519.KeyPair

interface PushRepository {
    suspend fun addToken(
        owner: KeyPair,
        token: String,
        installationId: String?
    ): Result<Unit>

    suspend fun deleteTokens(
        owner: KeyPair,
        installationId: String?,
    ): Result<Unit>
}