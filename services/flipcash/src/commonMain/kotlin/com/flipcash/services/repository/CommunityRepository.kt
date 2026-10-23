package com.flipcash.services.repository

import com.flipcash.services.models.Community
import com.getcode.ed25519kmp.Ed25519.KeyPair
import com.getcode.solana.keys.Mint

interface CommunityRepository {
    suspend fun getCommunity(owner: KeyPair, mint: Mint): Result<Community>
}
