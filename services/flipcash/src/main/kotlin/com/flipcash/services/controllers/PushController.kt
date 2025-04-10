package com.flipcash.services.controllers

import com.flipcash.services.repository.PushRepository
import com.flipcash.services.user.UserManager
import javax.inject.Inject

class PushController @Inject constructor(
    private val repository: PushRepository,
    private val userManager: UserManager,
) {
    suspend fun addToken(
        token: String,
        installationId: String?
    ): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.addToken(owner, token, installationId)
    }

    suspend fun deleteTokens(
        installationId: String?,
    ): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.deleteTokens(owner, installationId)
    }
}