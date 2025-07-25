package com.flipcash.services.controllers

import com.flipcash.services.repository.ThirdPartyRepository
import com.flipcash.services.user.UserManager
import com.getcode.network.jwt.JwtSecuredEndpoint
import javax.inject.Inject

class ThirdPartyController @Inject constructor(
    private val repository: ThirdPartyRepository,
    private val userManager: UserManager,
) {
    suspend fun getJwt(apiKey: String?, endpoint: JwtSecuredEndpoint): Result<String> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.getJwt(apiKey, endpoint, owner)
    }
}