package com.flipcash.services.controllers

import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.ResolveIdentifier
import com.flipcash.services.repository.ResolverRepository
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.PublicKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResolverController @Inject constructor(
    private val repository: ResolverRepository,
    private val userManager: UserManager,
) {
    /** Resolves a phone number to its on-chain address. */
    suspend fun resolve(phone: ContactMethod.Phone): Result<PublicKey> =
        resolve(ResolveIdentifier.Phone(phone))

    /** Resolves a user ID to their on-chain address (e.g. for a tip DM counterparty). */
    suspend fun resolve(userId: ID): Result<PublicKey> =
        resolve(ResolveIdentifier.UserId(userId))

    private suspend fun resolve(identifier: ResolveIdentifier): Result<PublicKey> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.resolve(owner, identifier)
    }
}
