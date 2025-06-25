package com.flipcash.app.persistence.sources.mediator

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.flipcash.app.persistence.entities.PoolEntity
import com.flipcash.app.persistence.entities.PoolWithBetsEntity
import com.flipcash.app.persistence.sources.PoolDataSource
import com.flipcash.services.controllers.PoolController
import com.flipcash.services.models.QueryOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalPagingApi::class)
class PoolRemoteMediator @Inject constructor(
    private val controller: PoolController,
    private val dataSource: PoolDataSource
): RemoteMediator<Int, PoolEntity>() {

    override suspend fun initialize(): InitializeAction {
        return InitializeAction.SKIP_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, PoolEntity>
    ): MediatorResult {
        return try {
            val loadKey = when (loadType) {
                LoadType.REFRESH -> null

                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> {
                    val lastItem = state.lastItemOrNull()
                        ?: return MediatorResult.Success(endOfPaginationReached = true)

                    lastItem.pagingToken
                }
            }

            val queryOptions = QueryOptions(
                token = loadKey,
                limit = state.config.pageSize,
            )

            val pools = controller.getPagedPools(queryOptions).getOrNull().orEmpty()

            withContext(Dispatchers.IO) {
                if (loadType == LoadType.REFRESH) {
                    dataSource.clear()
                }

                dataSource.upsert(pools)
            }

            MediatorResult.Success(endOfPaginationReached = pools.isEmpty())
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}