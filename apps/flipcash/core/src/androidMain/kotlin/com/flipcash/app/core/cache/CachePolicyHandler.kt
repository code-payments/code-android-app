package com.flipcash.app.core.cache

import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

internal fun <T> fromCache(data: T): CacheEntry<T> = CacheEntry(data, DataOrigin.Cache)
internal fun <T> fromNetwork(data: T): CacheEntry<T> = CacheEntry(data, DataOrigin.Network)

class CachePolicyHandler<DomainEntity, NetworkEntity> {
    suspend fun execute(
        cachePolicy: CachePolicy,
        cacheLookup: suspend () -> DomainEntity?,
        persistNetworkData: suspend (NetworkEntity) -> Unit,
        networkRequest: suspend () -> Result<NetworkEntity>,
        domainMapper: suspend (NetworkEntity) -> DomainEntity,
    ): Result<CacheEntry<DomainEntity>> {
        when (cachePolicy) {
            CachePolicy.CacheOnly -> {
                val cachedData = cacheLookup()
                return if (cachedData != null) {
                    Result.success(fromCache(cachedData))
                } else {
                    Result.failure(Throwable("No cached data"))
                }
            }
            CachePolicy.CacheFirst -> {
                val cachedData = cacheLookup()
                if (cachedData != null) {
                    supervisorScope {
                        launch {
                            networkRequest().onSuccess { persistNetworkData(it) }
                        }
                    }
                    return Result.success(fromCache(cachedData))
                }
                return networkRequest().fold(
                    onSuccess = { networkData ->
                        persistNetworkData(networkData)
                        val domainData = domainMapper(networkData)
                        Result.success(fromNetwork(domainData))
                    },
                    onFailure = { Result.failure(it) }
                )
            }
            CachePolicy.NetworkFirst -> {
                val networkResult = networkRequest()
                if (networkResult.isSuccess) {
                    return networkResult.fold(
                        onSuccess = { networkData ->
                            persistNetworkData(networkData)
                            val domainData = domainMapper(networkData)
                            Result.success(fromNetwork(domainData))
                        },
                        onFailure = { Result.failure(it) }
                    )
                }
                val cachedData = cacheLookup()
                return if (cachedData != null) {
                    Result.success(fromCache(cachedData))
                } else {
                    networkResult.fold(
                        onSuccess = { networkData ->
                            persistNetworkData(networkData)
                            val domainData = domainMapper(networkData)
                            Result.success(fromNetwork(domainData))
                        },
                        onFailure = { Result.failure(it) }
                    )
                }
            }
            CachePolicy.NetworkOnly -> {
                return networkRequest().fold(
                    onSuccess = { networkData ->
                        persistNetworkData(networkData)
                        val domainData = domainMapper(networkData)
                        Result.success(fromNetwork(domainData))
                    },
                    onFailure = { Result.failure(it) }
                )
            }
        }
    }
}