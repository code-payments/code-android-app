package com.flipcash.app.core.pools

data class PoolWithHostStatus(
    val pool: Pool,
    val isUserHost: Boolean,
)