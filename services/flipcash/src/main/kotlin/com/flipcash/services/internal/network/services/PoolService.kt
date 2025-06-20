package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.pool.v1.Model
import com.codeinc.flipcash.gen.pool.v1.PoolService
import com.flipcash.services.internal.model.pools.PoolRequest
import com.flipcash.services.internal.network.api.PoolApi
import com.flipcash.services.models.CreatePoolError
import com.flipcash.services.models.DeclarePoolOutcomeError
import com.flipcash.services.models.GetPoolError
import com.flipcash.services.models.PlacePoolBetError
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import javax.inject.Inject

internal class PoolService @Inject constructor(
    private val api: PoolApi
) {
    suspend fun createPool(
        owner: KeyPair,
        request: PoolRequest.Create): Result<Unit> {
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

    suspend fun resolvePool(
        owner: KeyPair,
        request: PoolRequest.Resolve
    ): Result<Unit> {
        return runCatching {
            api.declareOutcome(owner, request)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    PoolService.ResolvePoolResponse.Result.OK -> Result.success(Unit)
                    PoolService.ResolvePoolResponse.Result.NOT_FOUND -> Result.failure(DeclarePoolOutcomeError.NotFound())
                    PoolService.ResolvePoolResponse.Result.DENIED -> Result.failure(DeclarePoolOutcomeError.Denied())
                    PoolService.ResolvePoolResponse.Result.DIFFERENT_OUTCOME_DECLARED -> Result.failure(DeclarePoolOutcomeError.AlreadyDeclared())
                    PoolService.ResolvePoolResponse.Result.UNRECOGNIZED -> Result.failure(DeclarePoolOutcomeError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(DeclarePoolOutcomeError.Other(cause = cause))
            }
        )
    }

    suspend fun placeBet(
        owner: KeyPair,
        request: PoolRequest.PlaceBet): Result<Unit> {
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