package com.flipcash.services.persistence

import androidx.paging.PagingSource

interface DataSource<Identifier, Result, Network> {
    suspend fun getById(id: Identifier): Result?
    suspend fun get(): List<Result>
    suspend fun upsert(value: Network)
    suspend fun query(whereClause: String): List<Result>
    suspend fun getMostRecent(): Result?
    suspend fun clear()
}

interface PagingDataSource<Identifier, Result, Network, PagingKey : Any, PagingValue : Any> :
    DataSource<Identifier, Result, Network> {
    fun observe(): PagingSource<PagingKey, PagingValue>
}