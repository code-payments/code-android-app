package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.network.services.ResolverService
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.repository.ResolverRepository
import com.getcode.ed25519.Ed25519
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.ErrorUtils

internal class InternalResolverRepository(
    private val service: ResolverService,
) : ResolverRepository {
    override suspend fun resolve(
        owner: Ed25519.KeyPair,
        phone: ContactMethod.Phone,
    ): Result<PublicKey> = service.resolve(owner, phone)
        .onFailure { ErrorUtils.handleError(it) }
}
