package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.pool.v1.Model
import com.codeinc.flipcash.gen.pool.v1.PoolService
import com.flipcash.services.internal.model.pools.PoolRequest
import com.flipcash.services.internal.network.api.PoolApi
import com.flipcash.services.models.ClosePoolError
import com.flipcash.services.models.CreatePoolError
import com.flipcash.services.models.ResolvePoolOutcomeError
import com.flipcash.services.models.GetPoolError
import com.flipcash.services.models.GetPoolPageError
import com.flipcash.services.models.PlacePoolBetError
import com.flipcash.services.models.PoolBetMetadata
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import javax.inject.Inject

internal class PoolService @Inject constructor(
    private val api: PoolApi
) {
    suspend fun createPool(
        owner: KeyPair,
        request: PoolRequest.Create,
    ): Result<Unit> {
        return runCatching {
            api.createPool(owner, request)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    PoolService.CreatePoolResponse.Result.OK -> Result.success(Unit)
                    PoolService.CreatePoolResponse.Result.RENDEZVOUS_EXISTS -> Result.failure(CreatePoolError.RendezvousExists())
                    PoolService.CreatePoolResponse.Result.FUNDING_DESTINATION_EXISTS -> Result.failure(CreatePoolError.FundingDestinationExists())
                    PoolService.CreatePoolResponse.Result.UNRECOGNIZED -> Result.failure(CreatePoolError.Unrecognized())
                }
                Result.success(Unit)
            },
            onFailure = { cause ->
                Result.failure(CreatePoolError.Other(cause = cause))
            }
        )
    }

    suspend fun getPool(request: PoolRequest.Get): Result<Model.PoolMetadata> {
        return runCatching {
            api.getPool(request)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    PoolService.GetPoolResponse.Result.OK -> Result.success(response.pool)
                    PoolService.GetPoolResponse.Result.NOT_FOUND -> Result.failure(GetPoolError.NotFound())
                    PoolService.GetPoolResponse.Result.UNRECOGNIZED -> Result.failure(GetPoolError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(GetPoolError.Other(cause = cause))
            },
        )
    }

    suspend fun getPagedPools(
        owner: KeyPair,
        request: PoolRequest.GetPage
    ): Result<List<Model.PoolMetadata>> {
        return runCatching {
            api.getPagedPools(owner, request)
        }.foldWithSuppression(
            onSuccess = { result ->
                when (result.result) {
                    PoolService.GetPagedPoolsResponse.Result.OK -> Result.success(result.poolsList)
                    PoolService.GetPagedPoolsResponse.Result.NOT_FOUND -> Result.failure(GetPoolPageError.NotFound())
                    PoolService.GetPagedPoolsResponse.Result.UNRECOGNIZED -> Result.failure(GetPoolPageError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(GetPoolPageError.Other(cause = cause))
            }
        )
    }

    suspend fun closePool(
        owner: KeyPair,
        request: PoolRequest.Close
    ): Result<Unit> {
        return runCatching {
            api.closePool(owner, request)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    PoolService.ClosePoolResponse.Result.OK -> Result.success(Unit)
                    PoolService.ClosePoolResponse.Result.DENIED -> Result.failure(ClosePoolError.Denied())
                    PoolService.ClosePoolResponse.Result.NOT_FOUND -> Result.failure(ClosePoolError.NotFound())
                    PoolService.ClosePoolResponse.Result.UNRECOGNIZED -> Result.failure(ClosePoolError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(ClosePoolError.Other(cause = cause))
            }
        )
    }

    suspend fun resolvePool(
        owner: KeyPair,
        request: PoolRequest.Resolve
    ): Result<Unit> {
        return runCatching {
            api.resolvePool(owner, request)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    PoolService.ResolvePoolResponse.Result.OK -> Result.success(Unit)
                    PoolService.ResolvePoolResponse.Result.NOT_FOUND -> Result.failure(ResolvePoolOutcomeError.NotFound())
                    PoolService.ResolvePoolResponse.Result.DENIED -> Result.failure(ResolvePoolOutcomeError.Denied())
                    PoolService.ResolvePoolResponse.Result.DIFFERENT_OUTCOME_DECLARED -> Result.failure(ResolvePoolOutcomeError.AlreadyDeclared())
                    PoolService.ResolvePoolResponse.Result.POOL_OPEN -> Result.failure(ResolvePoolOutcomeError.PoolOpen())
                    PoolService.ResolvePoolResponse.Result.UNRECOGNIZED -> Result.failure(ResolvePoolOutcomeError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(ResolvePoolOutcomeError.Other(cause = cause))
            }
        )
    }

    suspend fun placeBet(
        owner: KeyPair,
        request: PoolRequest.PlaceBet
    ): Result<Unit> {
        return runCatching {
            api.placeBet(owner, request)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    PoolService.MakeBetResponse.Result.OK -> Result.success(Unit)
                    PoolService.MakeBetResponse.Result.POOL_NOT_FOUND -> Result.failure(PlacePoolBetError.PoolNotFound())
                    PoolService.MakeBetResponse.Result.POOL_CLOSED -> Result.failure(PlacePoolBetError.PoolClosed())
                    PoolService.MakeBetResponse.Result.MULTIPLE_BETS -> Result.failure(PlacePoolBetError.BetAlreadyMade())
                    PoolService.MakeBetResponse.Result.MAX_BETS_RECEIVED -> Result.failure(PlacePoolBetError.MaxBetsReceived())
                    PoolService.MakeBetResponse.Result.UNRECOGNIZED -> Result.failure(PlacePoolBetError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(PlacePoolBetError.Other(cause = cause))
            }
        )
    }
}