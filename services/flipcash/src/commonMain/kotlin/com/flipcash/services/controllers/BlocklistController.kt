package com.flipcash.services.controllers

import com.flipcash.services.models.BlocklistPage
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.repository.BlocklistRepository
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import javax.inject.Inject

class BlocklistController @Inject constructor(
    private val repository: BlocklistRepository,
    private val userManager: UserManager,
) {
    suspend fun blockUser(userId: ID): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.blockUser(userId, owner)
    }

    suspend fun unblockUser(userId: ID): Result<Unit> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.unblockUser(userId, owner)
    }

    suspend fun isBlocked(userId: ID): Result<Boolean> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.isBlocked(userId, owner)
    }

    suspend fun getBlocklist(queryOptions: QueryOptions = QueryOptions()): Result<BlocklistPage> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getBlocklist(owner, queryOptions)
    }
}
