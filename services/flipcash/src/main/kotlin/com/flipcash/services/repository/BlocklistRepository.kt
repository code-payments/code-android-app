package com.flipcash.services.repository

import com.flipcash.services.models.BlocklistPage
import com.flipcash.services.models.QueryOptions
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.model.core.ID

interface BlocklistRepository {
    suspend fun blockUser(userId: ID, owner: KeyPair): Result<Unit>

    suspend fun unblockUser(userId: ID, owner: KeyPair): Result<Unit>

    suspend fun isBlocked(userId: ID, owner: KeyPair): Result<Boolean>

    suspend fun getBlocklist(owner: KeyPair, queryOptions: QueryOptions): Result<BlocklistPage>
}
