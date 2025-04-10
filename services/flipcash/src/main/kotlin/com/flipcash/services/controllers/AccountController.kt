package com.flipcash.services.controllers

import com.flipcash.services.internal.model.account.UserFlags
import com.flipcash.services.repository.AccountRepository
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import javax.inject.Inject

class AccountController @Inject constructor(
    private val repository: AccountRepository,
    private val userManager: UserManager,
) {
    suspend fun register(): Result<ID> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.register(owner)
    }

    suspend fun login(): Result<ID> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.login(owner)
    }

    suspend fun getUserFlags(userId: ID): Result<UserFlags> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getUserFlags(
            owner = owner,
            userId = userId
        )
    }
}
