package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.blocklist.v1.BlocklistService
import com.flipcash.services.internal.network.api.BlocklistApi
import com.flipcash.services.models.BlockUserError
import com.flipcash.services.models.GetBlocklistError
import com.flipcash.services.models.IsBlockedError
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.UnblockUserError
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.utils.toValidationOrElse
import javax.inject.Inject

internal class BlocklistService @Inject constructor(
    private val api: BlocklistApi,
) {
    suspend fun blockUser(userId: ID, owner: KeyPair): Result<Unit> {
        return runCatching {
            api.blockUser(userId, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    BlocklistService.BlockUserResponse.Result.OK -> Result.success(Unit)
                    BlocklistService.BlockUserResponse.Result.DENIED -> Result.failure(BlockUserError.Denied())
                    BlocklistService.BlockUserResponse.Result.USER_NOT_FOUND -> Result.failure(BlockUserError.UserNotFound())
                    BlocklistService.BlockUserResponse.Result.CANNOT_BLOCK_SELF -> Result.failure(BlockUserError.CannotBlockSelf())
                    BlocklistService.BlockUserResponse.Result.UNRECOGNIZED -> Result.failure(BlockUserError.Unrecognized())
                    else -> Result.failure(BlockUserError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { BlockUserError.Other(cause = it) })
            }
        )
    }

    suspend fun unblockUser(userId: ID, owner: KeyPair): Result<Unit> {
        return runCatching {
            api.unblockUser(userId, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    BlocklistService.UnblockUserResponse.Result.OK -> Result.success(Unit)
                    BlocklistService.UnblockUserResponse.Result.DENIED -> Result.failure(UnblockUserError.Denied())
                    BlocklistService.UnblockUserResponse.Result.UNRECOGNIZED -> Result.failure(UnblockUserError.Unrecognized())
                    else -> Result.failure(UnblockUserError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { UnblockUserError.Other(cause = it) })
            }
        )
    }

    suspend fun isBlocked(userId: ID, owner: KeyPair): Result<Boolean> {
        return runCatching {
            api.isBlocked(userId, owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    BlocklistService.IsBlockedResponse.Result.OK -> Result.success(response.isBlocked)
                    BlocklistService.IsBlockedResponse.Result.DENIED -> Result.failure(IsBlockedError.Denied())
                    BlocklistService.IsBlockedResponse.Result.UNRECOGNIZED -> Result.failure(IsBlockedError.Unrecognized())
                    else -> Result.failure(IsBlockedError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { IsBlockedError.Other(cause = it) })
            }
        )
    }

    suspend fun getBlocklist(
        owner: KeyPair,
        queryOptions: QueryOptions,
    ): Result<BlocklistService.GetBlocklistResponse> {
        return runCatching {
            api.getBlocklist(owner, queryOptions)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    BlocklistService.GetBlocklistResponse.Result.OK -> Result.success(response)
                    BlocklistService.GetBlocklistResponse.Result.DENIED -> Result.failure(GetBlocklistError.Denied())
                    BlocklistService.GetBlocklistResponse.Result.UNRECOGNIZED -> Result.failure(GetBlocklistError.Unrecognized())
                    else -> Result.failure(GetBlocklistError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetBlocklistError.Other(cause = it) })
            }
        )
    }
}
