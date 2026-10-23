package com.flipcash.services.controllers

import com.flipcash.services.internal.extensions.installationId
import com.flipcash.services.repository.PushRepository
import com.flipcash.services.user.UserManager
import com.google.firebase.Firebase
import com.google.firebase.installations.installations
import javax.inject.Inject

class PushController @Inject constructor(
    private val repository: PushRepository,
    private val userManager: UserManager,
) {
    suspend fun addToken(token: String, ): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        val installationId = Firebase.installations.installationId()
        return repository.addToken(owner, token, installationId)
    }

    suspend fun deleteTokens(): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        val installationId = Firebase.installations.installationId()
        return repository.deleteTokens(owner, installationId)
    }
}