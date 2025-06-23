package com.flipcash.app.pools.internal.list.components

import com.flipcash.app.core.pools.PoolWithBets

sealed interface PoolListItem {
    data class PoolItem(val data: PoolWithBets) : PoolListItem
    sealed interface Header: PoolListItem {
        data object Open : Header
        data object Completed : Header
    }
}