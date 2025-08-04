package com.flipcash.app.onramp

import com.flipcash.app.core.NavScreenProvider
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object OnRampFlowTracker {
    internal var key: String = ""
        private set

    internal var source: NavScreenProvider? = null
        private set

    @OptIn(ExperimentalUuidApi::class)
    fun start(from: NavScreenProvider?) {
        source = from
        key = Uuid.Companion.random().toString()
    }
}