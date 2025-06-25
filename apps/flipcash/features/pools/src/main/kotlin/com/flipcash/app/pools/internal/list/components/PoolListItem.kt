package com.flipcash.app.pools.internal.list.components

import com.flipcash.app.core.pools.Pool

sealed interface PoolListItem {
    data class PoolItem(val pool: Pool, val isHost: Boolean) : PoolListItem
    sealed interface Header: PoolListItem {
        data object Open : Header
        data object Completed : Header
    }
}