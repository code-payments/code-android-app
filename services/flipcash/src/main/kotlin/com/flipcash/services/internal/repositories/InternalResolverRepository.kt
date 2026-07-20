package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.network.services.ResolverService
import com.flipcash.services.models.ResolveIdentifier
import com.flipcash.services.repository.ResolverRepository
import com.getcode.ed25519.Ed25519
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.ErrorUtils

internal class InternalResolverRepository(
    private val service: ResolverService,
) : ResolverRepository {
    override suspend fun resolve(
        owner: Ed25519.KeyPair,
        identifier: ResolveIdentifier,
    ): Result<PublicKey> = service.resolve(owner, identifier)
        .onFailure { ErrorUtils.handleError(it) }
}
