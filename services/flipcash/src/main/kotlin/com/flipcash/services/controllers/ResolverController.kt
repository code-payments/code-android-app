package com.flipcash.services.controllers

import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.ResolveIdentifier
import com.flipcash.services.repository.ResolverRepository
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.PublicKey
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResolverController @Inject constructor(
    private val repository: ResolverRepository,
    private val userManager: UserManager,
) {
    // Memoizes successful resolutions for the process lifetime. An identity's on-chain address is
    // effectively immutable, and callers commonly resolve the same identity twice in quick
    // succession (e.g. a chat pre-resolves the recipient to gate the send button, then the payment
    // delegate resolves it again at send time) — the cache turns the second into a no-op. Failures
    // are never cached, so an unresolvable identity is retried.
    private val cache = ConcurrentHashMap<ResolveIdentifier, PublicKey>()

    /** Resolves a phone number to its on-chain address. */
    suspend fun resolve(phone: ContactMethod.Phone): Result<PublicKey> =
        resolve(ResolveIdentifier.Phone(phone))

    /** Resolves a user ID to their on-chain address (e.g. for a tip DM counterparty). */
    suspend fun resolve(userId: ID): Result<PublicKey> =
        resolve(ResolveIdentifier.UserId(userId))

    private suspend fun resolve(identifier: ResolveIdentifier): Result<PublicKey> {
        cache[identifier]?.let { return Result.success(it) }
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.resolve(owner, identifier)
            .onSuccess { cache[identifier] = it }
    }
}
