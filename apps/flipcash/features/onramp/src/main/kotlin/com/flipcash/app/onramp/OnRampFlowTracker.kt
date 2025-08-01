package com.flipcash.app.onramp

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object OnRampFlowTracker {
    internal var key: String = ""
        private set

    @OptIn(ExperimentalUuidApi::class)
    fun start() {
        key = Uuid.Companion.random().toString()
    }
}