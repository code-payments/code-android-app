package com.flipcash.services.controllers

import com.flipcash.services.models.ContactMethod
import com.flipcash.services.repository.ResolverRepository
import com.flipcash.services.user.UserManager
import com.getcode.solana.keys.PublicKey
import javax.inject.Inject

class ResolverController @Inject constructor(
    private val repository: ResolverRepository,
    private val userManager: UserManager,
) {
    suspend fun resolve(phone: ContactMethod.Phone): Result<PublicKey> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.resolve(owner, phone)
    }
}
