package xyz.flipchat.services.internal.network.repository.push

import com.getcode.ed25519.Ed25519
import com.getcode.model.ID
import com.getcode.utils.ErrorUtils
import xyz.flipchat.services.internal.network.service.PushService
import javax.inject.Inject

internal class RealPushRepository @Inject constructor(
    private val service: PushService
) : PushRepository {
    override suspend fun addToken(
        owner: Ed25519.KeyPair,
        token: String,
        installationId: String?
    ): Result<Unit> {
        return service.addToken(owner, token, installationId)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun deleteToken(owner: Ed25519.KeyPair, token: String): Result<Unit> {
        return service.deleteToken(owner, token)
    }
}