package com.flipcash.services.persistence

import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

interface DataSource<Identifier, Result, Network> {
    suspend fun getById(id: Identifier): Result?
    suspend fun get(): List<Result>
    suspend fun upsert(value: Network)
    suspend fun query(whereClause: String): List<Result>
    suspend fun getMostRecent(): Result?
    suspend fun clear()
}

interface SingularDataSource<Identifier, Result, Network> : DataSource<Identifier, Result, Network> {
    fun observe(): Flow<List<Result>>
    fun observe(id: Identifier): Flow<Result?>
}

interface PagingDataSource<Identifier, Result, Network, PagingKey : Any, PagingValue : Any> :
    DataSource<Identifier, Result, Network> {
    fun observe(): PagingSource<PagingKey, PagingValue>
}