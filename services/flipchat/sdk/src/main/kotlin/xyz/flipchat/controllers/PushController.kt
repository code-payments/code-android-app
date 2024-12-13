package xyz.flipchat.controllers

import com.getcode.services.utils.installationId
import com.getcode.utils.ErrorUtils
import com.google.firebase.Firebase
import com.google.firebase.installations.installations
import xyz.flipchat.services.internal.network.repository.push.PushRepository
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

class PushController @Inject constructor(
    private val userManager: UserManager,
    private val repository: PushRepository,
) {
    suspend fun addToken(token: String): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(Throwable("No owner"))
        val installationId = Firebase.installations.installationId()
        return repository.addToken(owner, token, installationId)
            .onFailure { ErrorUtils.handleError(it) }
    }

    suspend fun deleteToken(token: String): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(Throwable("No owner"))
        return repository.deleteToken(owner, token)
            .onFailure { ErrorUtils.handleError(it) }
    }

    suspend fun deleteTokens(): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(Throwable("No owner"))
        val installationId = Firebase.installations.installationId()
        return repository.deleteTokens(owner, installationId)
            .onFailure { ErrorUtils.handleError(it) }
    }
}