package com.flipcash.app.core.cache

data class CacheEntry<T>(
    val data: T,
    val origin: DataOrigin,
)

sealed interface DataOrigin {
    object Cache : DataOrigin
    object Network : DataOrigin
}