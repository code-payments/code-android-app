package com.flipcash.app.onramp

import com.flipcash.app.core.AppRoute
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object OnRampFlowTracker {
    var key: String = ""
        private set

    var source: AppRoute? = null
        private set

    @OptIn(ExperimentalUuidApi::class)
    fun start(from: AppRoute?) {
        source = from
        key = Uuid.random().toString()
    }
}