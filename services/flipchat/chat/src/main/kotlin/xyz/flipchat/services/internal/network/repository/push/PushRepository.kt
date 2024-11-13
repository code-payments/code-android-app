package xyz.flipchat.services.internal.network.repository.push

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.ID

interface PushRepository {
    suspend fun addToken(
        owner: KeyPair,
        userId: ID,
        token: String,
        installationId: String?
    ): Result<Unit>
}