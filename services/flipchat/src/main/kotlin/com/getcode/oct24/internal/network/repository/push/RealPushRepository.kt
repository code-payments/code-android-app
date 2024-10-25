package com.getcode.oct24.internal.network.repository.push

import com.getcode.ed25519.Ed25519
import com.getcode.model.ID
import com.getcode.oct24.internal.network.service.PushService
import com.getcode.utils.ErrorUtils
import javax.inject.Inject

internal class RealPushRepository @Inject constructor(
    private val service: PushService
) : PushRepository {
    override suspend fun addToken(
        owner: Ed25519.KeyPair,
        userId: ID,
        token: String,
        installationId: String?
    ): Result<Unit> {
        return service.addToken(owner, userId, token, installationId)
            .onFailure { ErrorUtils.handleError(it) }
    }
}