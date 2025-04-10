package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.network.services.PushService
import com.flipcash.services.repository.PushRepository
import com.getcode.ed25519.Ed25519
import com.getcode.utils.ErrorUtils

internal class InternalPushRepository(
    private val service: PushService
) : PushRepository {
    override suspend fun addToken(
        owner: Ed25519.KeyPair,
        token: String,
        installationId: String?
    ): Result<Unit> = service.addToken(owner, token, installationId)
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun deleteTokens(
        owner: Ed25519.KeyPair,
        installationId: String?
    ): Result<Unit> = service.deleteTokens(owner, installationId)
        .onFailure { ErrorUtils.handleError(it) }
}