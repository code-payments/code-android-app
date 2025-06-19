package com.flipcash.app.pools

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object PoolCreateFlow {
    internal var key: String = ""
        private set

    @OptIn(ExperimentalUuidApi::class)
    fun start() {
        key = Uuid.random().toString()
    }
}