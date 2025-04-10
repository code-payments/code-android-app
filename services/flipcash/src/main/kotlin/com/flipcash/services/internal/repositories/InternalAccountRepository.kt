package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.domain.UserFlagsMapper
import com.flipcash.services.internal.model.account.UserFlags
import com.flipcash.services.internal.network.services.AccountService
import com.flipcash.services.repository.AccountRepository
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.core.ID
import com.getcode.utils.ErrorUtils

internal class InternalAccountRepository(
    private val service: AccountService,
    private val mapper: UserFlagsMapper,
) : AccountRepository {
    override suspend fun register(owner: Ed25519.KeyPair): Result<ID> = service.register(owner)
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun login(owner: Ed25519.KeyPair): Result<ID> = service.login(owner)
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun getUserFlags(owner: Ed25519.KeyPair, userId: ID): Result<UserFlags> =
        service.getUserFlags(owner, userId)
            .onFailure { ErrorUtils.handleError(it) }
            .map { mapper.map(it) }
}