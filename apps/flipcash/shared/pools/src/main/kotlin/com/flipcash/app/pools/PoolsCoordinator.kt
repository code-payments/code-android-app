package com.flipcash.app.pools

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.flipcash.app.core.pools.Pool
import com.flipcash.app.core.pools.PoolWithBets
import com.flipcash.app.persistence.sources.PoolDataSource
import com.flipcash.app.persistence.sources.mapper.pools.PoolEntityToPoolMapper
import com.flipcash.app.persistence.sources.mediator.PoolRemoteMediator
import com.flipcash.services.controllers.PoolController
import com.flipcash.services.models.NetworkPool
import com.flipcash.services.models.PoolMetadata
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

class PoolsCoordinator @Inject constructor(
    val controller: PoolController,
    val dataSource: PoolDataSource,
    val mapper: PoolEntityToPoolMapper,
    userManager: UserManager
) {
    private val pagingConfig = PagingConfig(pageSize = 20)

    @OptIn(ExperimentalPagingApi::class)
    private val _pools: Flow<PagingData<Pool>> = userManager.state
        .filter { it.authState.canAccessAuthenticatedApis }
        .flatMapLatest {
            Pager(
                config = pagingConfig,
                remoteMediator = PoolRemoteMediator(controller, dataSource)
            ) {
                dataSource.observe()
            }.flow.map { page -> page.map { entity -> mapper.map(entity) } }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val pools: Flow<PagingData<Pool>> = userManager.state
        .mapNotNull { it.authState }
        .filter { it.canAccessAuthenticatedApis }
        .flatMapLatest { _pools }

    suspend fun createPool(
        name: String,
        buyIn: Fiat,
    ): Result<PoolMetadata> {

        val (metadata, rendezvous) = controller.createPool(
            name = name,
            buyIn = buyIn,
        ).getOrElse { return Result.failure(it) }

        val mockNetworkResponse = NetworkPool(
            metadata = metadata,
            rendezvous = rendezvous,
        )

        dataSource.upsert(listOf(mockNetworkResponse))

        return Result.success(metadata)
    }

    suspend fun getPool(id: ID): Result<PoolWithBets> {
        val (metadata, rendezvous, bets) = controller.getPool(id)
            .getOrElse { return Result.failure(it) }

        val mockNetworkResponse = NetworkPool(
            metadata = metadata,
            rendezvous = rendezvous,
            bets = bets,
        )

        dataSource.upsert(listOf(mockNetworkResponse))

        val poolWithBets = dataSource.getByIdWithBets(metadata.id)
            ?: return Result.failure(Exception("Pool not found"))
        return Result.success(poolWithBets)
    }
}