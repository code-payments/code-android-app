package com.flipcash.app.core.updater

import kotlinx.coroutines.CoroutineScope
import java.util.Timer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class NetworkUpdater {
    protected var updater: Timer? = null

    abstract fun poll(
        scope: CoroutineScope,
        frequency: Duration,
        startIn: Duration = 0.seconds,
    )

    open fun stop() {
        updater?.cancel()
        updater = null

    }
}