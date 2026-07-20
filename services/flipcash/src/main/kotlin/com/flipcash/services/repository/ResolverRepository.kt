package com.flipcash.services.repository

import com.flipcash.services.models.ResolveIdentifier
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.solana.keys.PublicKey

interface ResolverRepository {
    suspend fun resolve(owner: KeyPair, identifier: ResolveIdentifier): Result<PublicKey>
}
