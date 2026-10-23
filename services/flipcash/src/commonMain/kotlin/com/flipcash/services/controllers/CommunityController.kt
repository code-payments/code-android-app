package com.flipcash.services.controllers

import com.flipcash.services.models.Community
import com.flipcash.services.repository.CommunityRepository
import com.flipcash.services.user.UserManager
import com.getcode.solana.keys.Mint
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommunityController @Inject constructor(
    private val repository: CommunityRepository,
    private val userManager: UserManager,
) {
    /** Fetches the community built around the currency [mint]. */
    suspend fun getCommunity(mint: Mint): Result<Community> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.getCommunity(owner, mint)
    }
}
