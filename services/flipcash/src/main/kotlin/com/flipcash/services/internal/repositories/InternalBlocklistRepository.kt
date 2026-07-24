package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.domain.BlockedUserMapper
import com.flipcash.services.internal.network.extensions.toPagingToken
import com.flipcash.services.internal.network.services.BlocklistService
import com.flipcash.services.models.BlocklistPage
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.repository.BlocklistRepository
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.core.ID
import com.getcode.utils.ErrorUtils

internal class InternalBlocklistRepository(
    private val service: BlocklistService,
    private val mapper: BlockedUserMapper,
) : BlocklistRepository {
    override suspend fun blockUser(userId: ID, owner: Ed25519.KeyPair): Result<Unit> =
        service.blockUser(userId, owner)
            .onFailure { ErrorUtils.handleError(it) }

    override suspend fun unblockUser(userId: ID, owner: Ed25519.KeyPair): Result<Unit> =
        service.unblockUser(userId, owner)
            .onFailure { ErrorUtils.handleError(it) }

    override suspend fun isBlocked(userId: ID, owner: Ed25519.KeyPair): Result<Boolean> =
        service.isBlocked(userId, owner)
            .onFailure { ErrorUtils.handleError(it) }

    override suspend fun getBlocklist(
        owner: Ed25519.KeyPair,
        queryOptions: QueryOptions,
    ): Result<BlocklistPage> = service.getBlocklist(owner, queryOptions)
        .onFailure { ErrorUtils.handleError(it) }
        .map { response ->
            BlocklistPage(
                users = response.blockedUsersList.map { mapper.map(it) },
                pagingToken = if (response.hasPagingToken()) response.pagingToken.toPagingToken() else null,
                hasMore = response.hasMore,
            )
        }
}
